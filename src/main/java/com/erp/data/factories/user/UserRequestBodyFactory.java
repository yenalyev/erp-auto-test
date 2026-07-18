package com.erp.data.factories.user;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.models.response.UserModelResponse;
import com.erp.test_context.ContextKey;

import static com.erp.data.RequestBodyFactory.register;

public final class UserRequestBodyFactory {

    private static final String RBAC_USER_PREFIX = "rbac-usr-";

    private UserRequestBodyFactory() {
    }

    public static void registerStrategies() {
        register(ApiEndpointDefinition.USER_POST_CREATE, context ->
                UserDataFactory.createRandom(RBAC_USER_PREFIX));

        register(ApiEndpointDefinition.USER_PUT_UPDATE, context -> {
            UserModelResponse existing = context.get(ContextKey.SHARED_USER);
            if (existing == null) {
                throw new IllegalStateException(
                        "Test Context Error: 'sharedUser' is null. Call UserFixture.prepareRbacUserContext().");
            }
            return UserDataFactory.fromExisting(existing);
        });
    }
}
