package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.notification.NotificationDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.request.NotificationRecipientRequest;
import com.erp.models.request.NotificationSubscriptionRequest;
import com.erp.models.request.RemoveNotificationSubscriptionRequest;
import com.erp.models.response.NotificationLogResponse;
import com.erp.models.response.NotificationRecipientResponse;
import com.erp.models.response.NotificationSubscriptionResponse;
import com.erp.models.response.NotificationTemplateResponse;
import com.erp.models.response.PagedNotificationResponse;
import com.erp.models.response.PagedNotificationSubscriptionResponse;
import com.erp.models.response.PushNotificationResponse;
import com.erp.models.response.UserNotificationConfigResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.ApiResponseHelper;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
public class NotificationFixture extends BaseFixture {

    private final List<Integer> trackedRecipientIds = new ArrayList<>();

    public NotificationFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    public void prepareContext() {
        Long owner1 = ConfigProvider.getOwner1StorageId();
        Long owner2 = ConfigProvider.getOwner2StorageId();
        testContext.set(ContextKey.OWNER_1_STORAGE_ID, owner1);
        testContext.set(ContextKey.OWNER_2_STORAGE_ID, owner2);
    }

    @Step("API: створити notification recipient")
    public NotificationRecipientResponse createRecipient(UserRole role, NotificationRecipientRequest request) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.NOTIFICATION_RECIPIENT_CREATE, role, request);
        validateSuccess(response, "Create notification recipient");
        NotificationRecipientResponse created = response.as(NotificationRecipientResponse.class);
        trackRecipient(created.getId());
        testContext.set(ContextKey.NOTIFICATION_RECIPIENT_ID, created.getId());
        return created;
    }

    @Step("API: створити ACTIVE recipient (factory)")
    public NotificationRecipientResponse createActiveRecipient(UserRole role) {
        return createRecipient(role, NotificationDataFactory.newActiveRecipient());
    }

    @Step("API: GET recipient {id}")
    public NotificationRecipientResponse getRecipientById(UserRole role, Integer id) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.NOTIFICATION_RECIPIENT_GET_BY_ID, role, String.valueOf(id));
        validateSuccess(response, "GET notification recipient " + id);
        return response.as(NotificationRecipientResponse.class);
    }

    @Step("API: GET all recipients")
    public List<NotificationRecipientResponse> getAllRecipients(UserRole role) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.NOTIFICATION_RECIPIENT_GET_ALL, role);
        validateSuccess(response, "GET notification recipients");
        List<NotificationRecipientResponse> list = ApiResponseHelper.parseList(
                response, NotificationRecipientResponse.class, "GET recipients");
        return list != null ? list : List.of();
    }

    @Step("API: update recipient {id}")
    public NotificationRecipientResponse updateRecipient(
            UserRole role, Integer id, NotificationRecipientRequest request) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.NOTIFICATION_RECIPIENT_UPDATE, role, request, id);
        validateSuccess(response, "Update notification recipient " + id);
        return response.as(NotificationRecipientResponse.class);
    }

    @Step("API: disable recipient {id}")
    public void disableRecipient(UserRole role, Integer id) {
        NotificationRecipientResponse current = getRecipientById(role, id);
        NotificationRecipientRequest disable = NotificationRecipientRequest.builder()
                .id(id)
                .type(current.getType() != null ? current.getType() : NotificationDataFactory.TYPE_WHATSAPP)
                .caption(current.getCaption())
                .addressInfo(current.getAddressInfo())
                .state(NotificationDataFactory.STATE_DISABLED)
                .build();
        updateRecipient(role, id, disable);
    }

    @Step("API: GET templates")
    public List<NotificationTemplateResponse> getTemplates(UserRole role) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.NOTIFICATION_TEMPLATE_GET_ALL, role);
        validateSuccess(response, "GET notification templates");
        List<NotificationTemplateResponse> list = ApiResponseHelper.parseList(
                response, NotificationTemplateResponse.class, "GET templates");
        return list != null ? list : List.of();
    }

    @Step("API: save subscription")
    public Response saveSubscriptionRaw(UserRole role, NotificationSubscriptionRequest request) {
        return apiExecutor.execute(ApiEndpointDefinition.NOTIFICATION_SUBSCRIPTION_SAVE, role, request);
    }

    @Step("API: save subscription")
    public void saveSubscription(UserRole role, NotificationSubscriptionRequest request) {
        Response response = saveSubscriptionRaw(role, request);
        validateSuccess(response, "Save notification subscription");
    }

    @Step("API: delete subscription")
    public Response deleteSubscriptionRaw(UserRole role, RemoveNotificationSubscriptionRequest request) {
        return apiExecutor.execute(ApiEndpointDefinition.NOTIFICATION_SUBSCRIPTION_DELETE, role, request);
    }

    @Step("API: delete subscription")
    public void deleteSubscription(UserRole role, Integer recipientId, String templateCode) {
        Response response = deleteSubscriptionRaw(
                role, NotificationDataFactory.removeSubscription(recipientId, templateCode));
        validateSuccess(response, "Delete notification subscription");
    }

    @Step("API: list subscriptions")
    public List<NotificationSubscriptionResponse> listSubscriptions(UserRole role) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.NOTIFICATION_SUBSCRIPTION_GET_PAGE,
                role,
                java.util.Map.of("page", 0, "size", 200));
        validateSuccess(response, "GET notification subscriptions");
        PagedNotificationSubscriptionResponse page = response.as(PagedNotificationSubscriptionResponse.class);
        return page.getContent() != null ? page.getContent() : List.of();
    }

    @Step("API: find subscription for recipient {recipientId} / {templateCode}")
    public NotificationSubscriptionResponse findSubscription(
            UserRole role, Integer recipientId, String templateCode) {
        return listSubscriptions(role).stream()
                .filter(s -> Objects.equals(s.getRecipientId(), recipientId)
                        && Objects.equals(s.getTemplateCode(), templateCode))
                .findFirst()
                .orElse(null);
    }

    @Step("API: GET notifications journal")
    public List<NotificationLogResponse> listNotifications(UserRole role) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.NOTIFICATION_GET_PAGE,
                role,
                java.util.Map.of("page", 0, "size", 50, "sort", "createdAt,desc"));
        validateSuccess(response, "GET notifications journal");
        PagedNotificationResponse page = response.as(PagedNotificationResponse.class);
        return page.getContent() != null ? page.getContent() : List.of();
    }

    @Step("API: знайти stock_red у журналі для складу id={storageId}")
    public NotificationLogResponse findStockRedForStorage(UserRole role, Long storageId) {
        return listNotifications(role).stream()
                .filter(n -> NotificationDataFactory.TEMPLATE_STOCK_RED.equals(n.getTemplateCode()))
                .filter(n -> n.getStorage() != null && Objects.equals(storageId, n.getStorage().getId()))
                .findFirst()
                .orElse(null);
    }

    @Step("Await: stock_red у журналі для складу id={storageId}")
    public NotificationLogResponse awaitStockRedForStorage(UserRole role, Long storageId, long timeoutMs) {
        return com.erp.utils.helpers.PollUtils.waitUntil(
                () -> findStockRedForStorage(role, storageId),
                Objects::nonNull,
                timeoutMs,
                "stock_red notification for storageId=" + storageId);
    }

    @Step("API: знайти WEB_PUSH recipient caption={caption}")
    public NotificationRecipientResponse findWebPushByCaption(UserRole role, String caption) {
        if (caption == null) {
            return null;
        }
        return getAllRecipients(role).stream()
                .filter(r -> NotificationDataFactory.TYPE_WEB_PUSH.equals(r.getType()))
                .filter(r -> caption.equalsIgnoreCase(r.getCaption()))
                .findFirst()
                .orElse(null);
    }

    @Step("API: підписати {role} на relocation_incoming для складу {storageId}")
    public void subscribeToRelocationIncoming(UserRole role, Long storageId) {
        NotificationSubscriptionRequest request = NotificationDataFactory.subscription(
                null,
                NotificationDataFactory.TEMPLATE_RELOCATION_INCOMING,
                storageId != null ? List.of(storageId) : List.of());
        // WEB_PUSH recipient is created asynchronously on first login — retry 404 briefly.
        com.erp.utils.helpers.PollUtils.waitUntilTrue(
                () -> {
                    Response response = apiExecutor.execute(
                            ApiEndpointDefinition.NOTIFICATION_MY_SUBSCRIBE, role, request);
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return true;
                    }
                    log.info("Subscribe {} to relocation_incoming returned {}", role, response.statusCode());
                    return false;
                },
                20_000,
                "Subscribe " + role + " to relocation_incoming");
    }

    @Step("API: зняти підписку {role} з relocation_incoming")
    public void unsubscribeFromRelocationIncoming(UserRole role) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.NOTIFICATION_MY_UNSUBSCRIBE,
                role,
                NotificationDataFactory.removeSubscription(
                        null, NotificationDataFactory.TEMPLATE_RELOCATION_INCOMING));
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }
        log.warn("Unsubscribe {} from relocation_incoming returned {}", role, response.statusCode());
    }

    @Step("API: GET /notifications/my as {role}")
    public UserNotificationConfigResponse getMyConfiguration(UserRole role) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.NOTIFICATION_MY_GET, role);
        validateSuccess(response, "GET my notification configuration");
        return response.as(UserNotificationConfigResponse.class);
    }

    @Step("API: GET browser-notifications as {role}")
    public List<PushNotificationResponse> listBrowserNotifications(UserRole role) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.NOTIFICATION_BROWSER_GET, role);
        validateSuccess(response, "GET browser notifications");
        List<PushNotificationResponse> list = ApiResponseHelper.parseList(
                response, PushNotificationResponse.class, "GET browser notifications");
        return list != null ? list : List.of();
    }

    @Step("API: знайти browser-notification relocation_id={relocationId}")
    public PushNotificationResponse findBrowserNotificationForRelocation(UserRole role, Long relocationId) {
        if (relocationId == null) {
            return null;
        }
        String expected = String.valueOf(relocationId);
        return listBrowserNotifications(role).stream()
                .filter(n -> n.getParams() != null && expected.equals(n.getParams().get("relocation_id")))
                .findFirst()
                .orElse(null);
    }

    @Step("Await: browser-notification relocation_id={relocationId}")
    public PushNotificationResponse awaitBrowserNotification(UserRole role, Long relocationId, long timeoutMs) {
        return com.erp.utils.helpers.PollUtils.waitUntil(
                () -> findBrowserNotificationForRelocation(role, relocationId),
                Objects::nonNull,
                timeoutMs,
                "browser notification for relocationId=" + relocationId);
    }

    @Step("API: знайти relocation_incoming у журналі для складу id={storageId}")
    public NotificationLogResponse findRelocationIncomingForStorage(UserRole role, Long storageId) {
        return listNotifications(role).stream()
                .filter(n -> NotificationDataFactory.TEMPLATE_RELOCATION_INCOMING.equals(n.getTemplateCode()))
                .filter(n -> n.getStorage() != null && Objects.equals(storageId, n.getStorage().getId()))
                .findFirst()
                .orElse(null);
    }

    @Step("Await: relocation_incoming у журналі для складу id={storageId}")
    public NotificationLogResponse awaitRelocationIncomingForStorage(
            UserRole role, Long storageId, long timeoutMs) {
        return com.erp.utils.helpers.PollUtils.waitUntil(
                () -> findRelocationIncomingForStorage(role, storageId),
                Objects::nonNull,
                timeoutMs,
                "relocation_incoming notification for storageId=" + storageId);
    }

    @Step("FIXTURE: синтетичний push payload для relocation {relocationId}")
    public PushNotificationResponse syntheticRelocationIncoming(
            Long relocationId, Long senderId, Long recipientId, String senderName, String recipientName) {
        java.util.Map<String, String> params = new java.util.LinkedHashMap<>();
        params.put("template_code", NotificationDataFactory.TEMPLATE_RELOCATION_INCOMING);
        params.put("sender_id", String.valueOf(senderId));
        params.put("recipient_id", String.valueOf(recipientId));
        params.put("storage_name", recipientName);
        params.put("relocation_id", String.valueOf(relocationId));
        return PushNotificationResponse.builder()
                .id(relocationId != null ? relocationId.intValue() : 0)
                .title("ℹ️ Переміщення до " + recipientName)
                .description("Відправник: " + senderName)
                .params(params)
                .createdAt(java.time.Instant.now().toString())
                .build();
    }

    public void trackRecipient(Integer id) {
        if (id != null && !trackedRecipientIds.contains(id)) {
            trackedRecipientIds.add(id);
        }
    }

    @Step("Cleanup: disable tracked notification recipients")
    public void disableTrackedRecipients(UserRole role) {
        for (Integer id : new ArrayList<>(trackedRecipientIds)) {
            try {
                disableRecipient(role, id);
            } catch (Exception e) {
                log.warn("Failed to disable notification recipient {}: {}", id, e.getMessage());
            }
        }
        trackedRecipientIds.clear();
    }

    public List<Integer> getTrackedRecipientIds() {
        return List.copyOf(trackedRecipientIds);
    }
}
