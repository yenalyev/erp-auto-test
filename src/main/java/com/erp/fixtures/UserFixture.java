package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.user.UserDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.request.UserRequest;
import com.erp.models.response.OneTimeUserCredentialsResponse;
import com.erp.models.response.PagedUserResponse;
import com.erp.models.response.UserModelResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.helpers.PollUtils;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class UserFixture extends BaseFixture {

    public static final String ADMINISTRATOR_ROLE_NAME = "Administrator-ROLE";

    private final List<String> trackedUserIds = new ArrayList<>();

    public UserFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    @Step("API: підготувати контекст користувача для RBAC matrix")
    public void prepareRbacUserContext() {
        if (testContext.get(ContextKey.SHARED_USER_ID) != null) {
            return;
        }
        testContext.set(ContextKey.SHARED_ROLE_NAME, ADMINISTRATOR_ROLE_NAME);
        UserModelResponse user = createTestUser("rbac-matrix-");
        testContext.set(ContextKey.SHARED_USER_ID, user.getId());
        testContext.set(ContextKey.SHARED_USER, user);
    }

    @Step("API: створити тестового користувача «{prefix}»")
    public UserModelResponse createTestUser(String prefix) {
        UserRequest request = UserDataFactory.createRandom(prefix);
        Response response = apiExecutor.execute(ApiEndpointDefinition.USER_POST_CREATE, UserRole.ADMIN, request);
        validateSuccess(response, "Create user");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.USER_POST_CREATE);

        OneTimeUserCredentialsResponse credentials = response.as(OneTimeUserCredentialsResponse.class);
        log.info("User created: username={}", credentials.getUsername());

        String userId = findUserIdByUsername(request.getUsername());
        UserModelResponse user = waitForUser(UserRole.ADMIN, userId);
        trackForCleanup(userId);
        return user;
    }

    @Step("API: отримати користувача {userId}")
    public UserModelResponse getUser(UserRole role, String userId) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.USER_GET_BY_ID, role, userId);
        validateSuccess(response, "Get user by id");
        return response.as(UserModelResponse.class);
    }

    private UserModelResponse waitForUser(UserRole role, String userId) {
        return PollUtils.waitUntil(
                () -> {
                    Response response = apiExecutor.execute(
                            ApiEndpointDefinition.USER_GET_BY_ID, role, userId);
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return response.as(UserModelResponse.class);
                    }
                    return null;
                },
                Objects::nonNull,
                10_000,
                "Get user by id " + userId);
    }

    @Step("API: оновити користувача {userId}")
    public UserModelResponse updateUser(UserRole role, String userId, UserRequest body) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.USER_PUT_UPDATE, role, body, userId);
        validateSuccess(response, "Update user");
        return response.as(UserModelResponse.class);
    }

    @Step("API: деактивувати користувача {userId}")
    public void deactivateUser(UserRole role, String userId) {
        UserModelResponse existing = getUser(role, userId);
        UserRequest body = UserDataFactory.deactivated(existing);
        updateUser(role, userId, body);
        untrackForCleanup(userId);
    }

    public void trackForCleanup(String userId) {
        if (userId != null && !trackedUserIds.contains(userId)) {
            trackedUserIds.add(userId);
        }
    }

    public void untrackForCleanup(String userId) {
        trackedUserIds.remove(userId);
    }

    @Step("API: cleanup tracked test users")
    public void deactivateTrackedUsers() {
        if (TestArtifactCleanup.isStagingEnv() && !TestArtifactCleanup.isStagingCleanupEnabled()) {
            log.warn("Staging mode — skipping user cleanup");
            trackedUserIds.clear();
            return;
        }
        List<String> ids = new ArrayList<>(trackedUserIds);
        for (String userId : ids) {
            try {
                deactivateUser(UserRole.ADMIN, userId);
            } catch (Exception e) {
                log.warn("Failed to deactivate user {}: {}", userId, e.getMessage());
            }
        }
        trackedUserIds.clear();
    }

    public void trackUserByUsername(String username) {
        trackForCleanup(findUserIdByUsername(username));
    }

    public String findUserIdByUsername(String username) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.USER_GET_PAGE,
                UserRole.ADMIN,
                Map.of("username", username, "size", 20, "page", 0));
        validateSuccess(response, "Search user by username");
        PagedUserResponse page = response.as(PagedUserResponse.class);
        return page.getContent().stream()
                .filter(u -> username.equals(u.getUsername()))
                .map(UserModelResponse::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("User not found after create: " + username));
    }
}
