package com.erp.tests.functional.order;

import com.erp.annotations.TestCaseId;
import com.erp.enums.BusinessRole;
import com.erp.enums.UserRole;
import com.erp.fixtures.NotificationFixture;
import com.erp.fixtures.OrderRelocationTaskFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.NotificationAvailableTemplateResponse;
import com.erp.models.response.NotificationUserSubscriptionResponse;
import com.erp.models.response.OrderRelocationTaskResponse;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.PushNotificationResponse;
import com.erp.models.response.StorageResponse;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Notifications")
@Feature("Order relocation request notifications")
public class OrderRelocationNotificationApiTest extends OrderApiTestBase {

    private static final UserRole SINGLE_OWNER = UserRole.DYNAMIC_LOCATION_OWNER;
    private static final UserRole MULTI_OWNER = UserRole.LOCATION_MIXED;
    private static final UserRole ORDER_ADMIN = UserRole.ORDER_ADMIN;
    private static final long DELIVERY_TIMEOUT_MS = 30_000;

    private NotificationFixture notifications;
    private OrderRelocationTaskFixture relocationTasks;
    private StorageFixture storages;
    private UserFixture users;
    private StorageResponse parent;
    private StorageResponse child;
    private StorageResponse grandchild;
    private StorageResponse sibling;
    private StorageResponse otherAllowed;
    private StorageResponse foreign;
    private String templateCode;
    private final Map<UserRole, List<PushNotificationResponse>> received = new EnumMap<>(UserRole.class);

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupOrderApiTests")
    public void setupNotificationScope() {
        notifications = new NotificationFixture(testContext, apiExecutor);
        relocationTasks = new OrderRelocationTaskFixture(testContext, apiExecutor);
        storages = new StorageFixture(testContext, apiExecutor);
        users = new UserFixture(testContext, apiExecutor);

        long root = ConfigProvider.getOrderAvailabilityRootStorageId();
        parent = storages.createChildStorage(root, "notif-rel-parent-");
        child = storages.createChildStorage(parent.getId(), "notif-rel-child-");
        grandchild = storages.createChildStorage(child.getId(), "notif-rel-grandchild-");
        sibling = storages.createChildStorage(parent.getId(), "notif-rel-sibling-");
        otherAllowed = storages.createChildStorage(root, "notif-rel-other-");
        foreign = storages.createChildStorage(root, "notif-rel-foreign-");

        for (StorageResponse source : allSources()) {
            relocationFixture.ensureStock(source.getId(), resourceId, DEFAULT_SEED_STOCK);
        }

        var single = users.createBusinessActor(getPlaywrightSessionProvider(),
                BusinessRole.BUSINESS_UNIT_OWNER, List.of(sibling));
        apiExecutor.setSessionForRole(SINGLE_OWNER, single.username(), single.password());
        var multi = users.createBusinessActor(getPlaywrightSessionProvider(),
                BusinessRole.BUSINESS_UNIT_OWNER, List.of(parent, otherAllowed));
        apiExecutor.setSessionForRole(MULTI_OWNER, multi.username(), multi.password());
        var orderAdmin = users.createBusinessActor(getPlaywrightSessionProvider(),
                BusinessRole.ORDER_ADMIN,
                List.of(storages.getById(UserRole.ADMIN, requesterStorageId)));
        apiExecutor.setSessionForRole(ORDER_ADMIN, orderAdmin.username(), orderAdmin.password());

        List<NotificationAvailableTemplateResponse> availableTemplates = notifications
                .getMyConfiguration(MULTI_OWNER).getTemplates().values().stream()
                .flatMap(List::stream)
                .toList();
        templateCode = availableTemplates.stream()
                .filter(template -> template.getDescription() != null
                        && template.getDescription().contains("Замовлення переміщення"))
                .map(NotificationAvailableTemplateResponse::getCode)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Шаблон «Замовлення переміщення» відсутній у GET /notifications/my "
                                + "для керівника мультилокацій. Доступні шаблони: "
                                + availableTemplates.stream()
                                .map(template -> template.getCode() + " — " + template.getDescription())
                                .toList()));
    }

    @AfterClass(alwaysRun = true)
    public void cleanupNotificationScope() {
        if (orderFixture != null && requesterStorageId != null && gatheringStorageId != null) {
            clearSharedGatheringHolds();
        }
        for (UserRole role : List.of(SINGLE_OWNER, MULTI_OWNER, ORDER_ADMIN)) {
            if (notifications != null && templateCode != null) {
                notifications.unsubscribeMy(role, templateCode);
            }
            apiExecutor.evictSessionForRole(role);
        }
        if (users != null) users.deactivateTrackedUsers();
        if (storages != null) {
            for (StorageResponse storage : Arrays.asList(grandchild, child, sibling, parent,
                    otherAllowed, foreign)) {
                if (storage != null) storages.archiveStorage(UserRole.ADMIN, storage.getId());
            }
        }
    }

    @TestCaseId(value = "TC-NOTIF-060", roles = BusinessRole.BUSINESS_UNIT_OWNER)
    @Test
    @Severity(SeverityLevel.BLOCKER)
    @Description("Керівник локації отримує замовлення переміщення своєї локації, але не чужої.")
    public void singleLocationOwnerSeesOnlyOwnSource() {
        subscribe(SINGLE_OWNER, List.of(sibling.getId()), List.of(sibling.getId()));
        Map<Long, OrderRelocationTaskResponse> tasks = createRequests(List.of(sibling, foreign));
        assertDelivered(SINGLE_OWNER, tasks.get(sibling.getId()), sibling);
        assertNotDelivered(SINGLE_OWNER, tasks.get(foreign.getId()));
        assertExactlyOne(SINGLE_OWNER, tasks.get(sibling.getId()));
    }

    @TestCaseId(value = "TC-NOTIF-061", roles = BusinessRole.BUSINESS_UNIT_OWNER)
    @Test
    @Severity(SeverityLevel.BLOCKER)
    @Description("Конкретна локація мультилокаційного керівника не охоплює сестринську гілку.")
    public void multiOwnerSelectedLocationSeesOnlyThatLocation() {
        subscribe(MULTI_OWNER, List.of(sibling.getId()), List.of(sibling.getId()));
        Map<Long, OrderRelocationTaskResponse> tasks = createRequests(List.of(sibling, child, otherAllowed, foreign));
        assertDelivered(MULTI_OWNER, tasks.get(sibling.getId()), sibling);
        assertNotDelivered(MULTI_OWNER, tasks.get(child.getId()));
        assertNotDelivered(MULTI_OWNER, tasks.get(otherAllowed.getId()));
        assertNotDelivered(MULTI_OWNER, tasks.get(foreign.getId()));
        assertExactlyOne(MULTI_OWNER, tasks.get(sibling.getId()));
    }

    @TestCaseId(value = "TC-NOTIF-062", roles = BusinessRole.BUSINESS_UNIT_OWNER)
    @Test
    @Severity(SeverityLevel.BLOCKER)
    @Description("Вибір батьківської локації охоплює її дерево, але не іншу доступну гілку і не чужу локацію.")
    public void multiOwnerSelectedParentSeesDescendants() {
        subscribe(MULTI_OWNER, List.of(parent.getId()), List.of(parent.getId()));
        Map<Long, OrderRelocationTaskResponse> tasks = createRequests(allSources());
        for (StorageResponse source : List.of(parent, child, grandchild, sibling)) {
            assertDelivered(MULTI_OWNER, tasks.get(source.getId()), source);
        }
        assertNotDelivered(MULTI_OWNER, tasks.get(otherAllowed.getId()));
        assertNotDelivered(MULTI_OWNER, tasks.get(foreign.getId()));
        for (StorageResponse source : List.of(parent, child, grandchild, sibling)) {
            assertExactlyOne(MULTI_OWNER, tasks.get(source.getId()));
        }
    }

    @TestCaseId(value = "TC-NOTIF-063", roles = BusinessRole.BUSINESS_UNIT_OWNER)
    @Test
    @Severity(SeverityLevel.BLOCKER)
    @Description("«Всі локації» охоплюють лише доступні мультилокаційному керівнику локації.")
    public void multiOwnerAllLocationsStillExcludesForeign() {
        subscribe(MULTI_OWNER, List.of(), null);
        Map<Long, OrderRelocationTaskResponse> tasks = createRequests(allSources());
        for (StorageResponse source : List.of(parent, child, grandchild, sibling, otherAllowed)) {
            assertDelivered(MULTI_OWNER, tasks.get(source.getId()), source);
        }
        assertNotDelivered(MULTI_OWNER, tasks.get(foreign.getId()));
        for (StorageResponse source : List.of(parent, child, grandchild, sibling, otherAllowed)) {
            assertExactlyOne(MULTI_OWNER, tasks.get(source.getId()));
        }
    }

    @TestCaseId("TC-NOTIF-064")
    @Test
    @Severity(SeverityLevel.CRITICAL)
    @Description("Зміна вибору локацій зберігається; після вимкнення підписки нові сповіщення не надходять.")
    public void changingScopeAndUnsubscribingAffectsNewRequests() {
        subscribe(MULTI_OWNER, List.of(sibling.getId()), List.of(sibling.getId()));
        subscribe(MULTI_OWNER, List.of(parent.getId()), List.of(parent.getId()));
        Map<Long, OrderRelocationTaskResponse> tasks = createRequests(List.of(child, otherAllowed));
        assertDelivered(MULTI_OWNER, tasks.get(child.getId()), child);
        assertNotDelivered(MULTI_OWNER, tasks.get(otherAllowed.getId()));

        notifications.unsubscribeMy(MULTI_OWNER, templateCode);
        assertThat(notifications.isMySubscribed(MULTI_OWNER, templateCode)).isFalse();
        OrderRelocationTaskResponse afterUnsubscribe = createRequests(List.of(sibling)).get(sibling.getId());
        assertNotDelivered(MULTI_OWNER, afterUnsubscribe);
    }

    private List<StorageResponse> allSources() {
        return List.of(parent, child, grandchild, sibling, otherAllowed, foreign);
    }

    private void subscribe(UserRole role, List<Long> selected, List<Long> expected) {
        notifications.subscribeMy(role, templateCode, selected);
        NotificationUserSubscriptionResponse saved = notifications.getMyConfiguration(role).getSubscriptions()
                .stream().filter(s -> templateCode.equals(s.getTemplateCode()))
                .findFirst().orElseThrow();
        if (expected != null) {
            assertThat(saved.getStorages()).extracting(s -> s.getId())
                    .containsExactlyInAnyOrderElementsOf(expected);
        }
    }

    private Map<Long, OrderRelocationTaskResponse> createRequests(List<StorageResponse> sources) {
        pinGatheringOnHand(0);
        OrderResponse order = orderFixture.createOrder(REQUESTER, requesterStorageId,
                resourceId, sources.size() + 1.0);
        orderFixture.takeToWork(ORDER_ADMIN, order.getId(), requesterStorageId);
        orderFixture.setGathering(ORDER_ADMIN, order.getId(), requesterStorageId, gatheringStorageId);
        long orderLineId = order.getLines().getFirst().getId();
        Map<Long, OrderRelocationTaskResponse> tasks = new java.util.LinkedHashMap<>();
        for (StorageResponse source : sources) {
            relocationFixture.ensureStock(source.getId(), resourceId, DEFAULT_SEED_STOCK);
            OrderRelocationTaskResponse task = relocationTasks.create(ORDER_ADMIN, order.getId(),
                    requesterStorageId, relocationTasks.request(source.getId(), orderLineId, 1.0));
            tasks.put(source.getId(), task);
        }
        return tasks;
    }

    private void assertDelivered(UserRole role, OrderRelocationTaskResponse task, StorageResponse source) {
        PushNotificationResponse push = com.erp.utils.helpers.PollUtils.waitUntil(
                () -> findForTask(role, task.getId()), value -> value != null,
                DELIVERY_TIMEOUT_MS, "notification for relocation task " + task.getId());
        assertThat(push.getParams()).containsValue(String.valueOf(task.getId()))
                .containsValue(String.valueOf(source.getId()))
                .containsValue(source.getName());
        assertThat(push.getTitle()).isNotBlank();
        assertThat(push.getDescription()).contains(task.getGatheringStorage().getName());
    }

    private void assertExactlyOne(UserRole role, OrderRelocationTaskResponse task) {
        assertThat(received.getOrDefault(role, List.of()).stream()
                .filter(n -> matchesTask(n, task.getId())).count())
                .as("One notification for task %s to %s", task.getId(), role)
                .isEqualTo(1);
    }

    private void assertNotDelivered(UserRole role, OrderRelocationTaskResponse task) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            assertThat(findForTask(role, task.getId()))
                    .as("No notification for relocation task %s to %s", task.getId(), role)
                    .isNull();
            try {
                TimeUnit.MILLISECONDS.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while checking notification absence", e);
            }
        } while (System.nanoTime() < deadline);
    }

    private PushNotificationResponse findForTask(UserRole role, Long taskId) {
        received.computeIfAbsent(role, ignored -> new ArrayList<>())
                .addAll(notifications.listBrowserNotifications(role));
        return received.get(role).stream()
                .filter(n -> matchesTask(n, taskId))
                .findFirst().orElse(null);
    }

    private boolean matchesTask(PushNotificationResponse push, Long taskId) {
        return push.getParams() != null
                && templateCode.equals(push.getParams().get("template_code"))
                && push.getParams().containsValue(String.valueOf(taskId));
    }
}
