package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.BusinessRole;
import com.erp.enums.OrderRelocationTaskState;
import com.erp.enums.OrderState;
import com.erp.enums.ProductionOrderState;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.OrderRelocationTaskFixture;
import com.erp.fixtures.ProductionOrderFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.ShiftFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.MultiLocationStorageItemResponse;
import com.erp.models.response.OrderRelocationTaskResponse;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageAmountResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.pages.OrderListPage;
import com.erp.pages.ProductionCreateFormPage;
import com.erp.pages.ProductionGroupPlanningPage;
import com.erp.pages.ProductionOrderWizardPage;
import com.erp.pages.ProductionTasksPage;
import com.erp.pages.RelocationCreateOutputPage;
import com.erp.pages.RelocationPage;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Browser;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Multi-actor browser journeys for an order from requester creation to final receipt.
 * API calls are limited to isolated seed data, cleanup and state/stock oracles.
 */
@Slf4j
@Epic("Orders")
@Feature("REQ-ORD multi-actor UI E2E")
public class OrderEndToEndUiTest extends OrderUiTestBase {

    private static final UserRole ORDER_ADMIN = UserRole.ORDER_ADMIN;
    private static final UserRole SOURCE_KEEPER = UserRole.ORDER_SOURCE_KEEPER;
    private static final UserRole ISOLATED_GATHERER = UserRole.ORDER_ISOLATED_GATHERER;
    private static final UserRole PRODUCTION_WORKER = UserRole.ORDER_PRODUCTION_WORKER;

    private UserFixture userFixture;
    private InventoryFixture inventoryFixture;
    private ResourceFixture resourceFixture;
    private TechnologicalMapFixture technologicalMapFixture;
    private ProductionOrderFixture productionOrderFixture;
    private OrderRelocationTaskFixture relocationTaskFixture;
    private ShiftFixture shiftFixture;

    private UserFixture.BusinessActor orderAdmin;
    private UserFixture.BusinessActor sourceKeeper;
    private UserFixture.BusinessActor isolatedGatherer;
    private UserFixture.BusinessActor productionWorker;
    private StorageResponse sourceStorage;
    private StorageResponse isolatedGatheringStorage;
    private ResourceResponse productionResource;
    private TechnologicalMapResponse productionTechMap;

    private final List<Long> methodOrderIds = new ArrayList<>();
    private final List<Long> methodProductionOrderIds = new ArrayList<>();

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupEndToEndActors() {
        requireOrderUiContext();
        userFixture = new UserFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        technologicalMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        productionOrderFixture = new ProductionOrderFixture(testContext, apiExecutor);
        relocationTaskFixture = new OrderRelocationTaskFixture(testContext, apiExecutor);
        shiftFixture = new ShiftFixture(testContext, apiExecutor);

        long availabilityRoot = ConfigProvider.getOrderAvailabilityRootStorageId();
        if (availabilityRoot <= 0) {
            availabilityRoot = gatheringStorageId;
        }
        sourceStorage = storageFixture.createChildStorage(availabilityRoot, "ord-e2e-source-");
        isolatedGatheringStorage = storageFixture.createChildStorage(availabilityRoot, "ord-e2e-gather-");
        isolatedGatheringStorage = storageFixture.ensureOrderHub(
                UserRole.ADMIN, isolatedGatheringStorage.getId());

        StorageResponse requester = storageFixture.getById(UserRole.ADMIN, requesterStorageId);
        orderAdmin = createActor(BusinessRole.ORDER_ADMIN, ORDER_ADMIN, requester);
        isolatedGatherer = createActor(
                BusinessRole.BUSINESS_UNIT_OWNER, ISOLATED_GATHERER, isolatedGatheringStorage);
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupMethodArtifacts() {
        for (Long orderId : methodOrderIds) {
            try {
                for (OrderRelocationTaskResponse task : relocationTaskFixture.getForOrder(ORDER_ADMIN, orderId)) {
                    if (task.getState() == OrderRelocationTaskState.NEW) {
                        relocationTaskFixture.cancel(
                                ORDER_ADMIN, orderId, task.getId(), requesterStorageId);
                    } else if (task.getState() == OrderRelocationTaskState.SHIPPED
                            && task.getRelocationId() != null) {
                        relocationFixture.resolve(
                                ISOLATED_GATHERER,
                                task.getRelocationId(),
                                isolatedGatheringStorage.getId(),
                                RelocationState.FINISHED);
                    }
                }
                OrderResponse order = orderFixture.getById(UserRole.ADMIN, orderId);
                if (order.getState() != OrderState.DONE && order.getState() != OrderState.CANCELLED) {
                    orderFixture.cancel(UserRole.ADMIN, orderId, requesterStorageId);
                }
            } catch (Exception e) {
                log.warn("Could not clean up UI E2E order {}: {}", orderId, e.getMessage());
            }
        }
        for (Long productionOrderId : methodProductionOrderIds) {
            try {
                productionOrderFixture.cancel(UserRole.ADMIN, productionOrderId);
            } catch (Exception e) {
                log.debug("Production order {} is already final or could not be cancelled: {}",
                        productionOrderId, e.getMessage());
            }
        }
        methodOrderIds.clear();
        methodProductionOrderIds.clear();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupEndToEndActors() {
        for (UserRole role : List.of(
                ORDER_ADMIN, SOURCE_KEEPER, ISOLATED_GATHERER, PRODUCTION_WORKER)) {
            apiExecutor.evictSessionForRole(role);
        }
        if (userFixture != null) {
            userFixture.deactivateTrackedUsers();
        }
        if (technologicalMapFixture != null && productionTechMap != null
                && isolatedGatheringStorage != null) {
            try {
                technologicalMapFixture.deactivateTechMap(
                        UserRole.ADMIN, productionTechMap.getId(), isolatedGatheringStorage.getId());
            } catch (Exception e) {
                log.warn("Could not deactivate E2E production tech map: {}", e.getMessage());
            }
        }
        if (sourceStorage != null) {
            storageFixture.archiveStorage(UserRole.ADMIN, sourceStorage.getId());
        }
        if (isolatedGatheringStorage != null) {
            storageFixture.archiveStorage(UserRole.ADMIN, isolatedGatheringStorage.getId());
        }
        if (resourceFixture != null && productionResource != null) {
            resourceFixture.deactivate(UserRole.ADMIN, productionResource.getId());
        }
    }

    @Test(priority = 10)
    @TestCaseId(value = "TC-ORD-E2E-001", roles = {
            BusinessRole.BUSINESS_UNIT_OWNER, BusinessRole.ORDER_ADMIN})
    @Story("Full order from local gathering stock")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Unit Owner створює замовлення через UI; Order Admin бере в роботу, обирає збір і повністю бронює; збір відправляє; замовник приймає.")
    public void fullLocalStockOrderCompletesThroughAllUiActors() {
        seedStock(resourceId, 5.0, 0.0);
        long orderId = createOrderThroughUi(resourceName, 5.0);

        OrderListPage adminPage = takeToWorkAndChooseGathering(orderId);
        adminPage.bookResource(resourceName, 5.0);
        adminPage = reloadOrder(orderId).waitForOrderState("Готово до доставки");
        assertThat(adminPage.isSendOrderEnabled())
                .as("Order Admin must not ship from the gathering location")
                .isFalse();

        String marker = shipReadyOrderAsGatherer(orderId, resourceName, true, "full-local");
        receiveAsRequesterAndAssertDone(orderId, marker);
    }

    @Test(priority = 20)
    @TestCaseId(value = "TC-ORD-E2E-002", roles = {
            BusinessRole.BUSINESS_UNIT_OWNER, BusinessRole.ORDER_ADMIN})
    @Story("Partial fulfillment confirmed by order administrator")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Order Admin бронює частину, вручну підтверджує «Готово до доставки»; збір відправляє лише бронь; замовник приймає, повторна видача недоступна.")
    public void partialOrderRequiresReadyConfirmationThenCompletes() {
        seedStock(resourceId, 2.0, 0.0);
        long orderId = createOrderThroughUi(resourceName, 5.0);

        OrderListPage adminPage = takeToWorkAndChooseGathering(orderId)
                .bookResource(resourceName, 2.0);
        assertThat(adminPage.isSendOrderEnabled()).isFalse();
        assertThat(adminPage.isReadyToDeliverVisible()).isTrue();
        adminPage.markReadyToDeliver();
        assertThat(adminPage.isSendOrderEnabled())
                .as("Order Admin has no relocation create permission on gathering")
                .isFalse();

        String marker = shipReadyOrderAsGatherer(orderId, resourceName, false, "partial-local");
        receiveAsRequesterAndAssertDone(orderId, marker);
        assertThat(orderFixture.getBookings(ORDER_ADMIN, orderId))
                .allMatch(booking -> booking.getState().name().equals("FULFILLED")
                        || booking.getState().name().equals("RELEASED"));
    }

    @Test(priority = 30)
    @TestCaseId(value = "TC-ORD-E2E-003", roles = {
            BusinessRole.BUSINESS_UNIT_OWNER, BusinessRole.ORDER_ADMIN})
    @Story("Order supplied from another location")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Order Admin створює relocation task; комірник джерела відправляє; збір приймає й отримує autobook/READY; замовлення відправляється та приймається.")
    public void remoteStockRelocationAutoBooksAndCompletesOrder() {
        ensureSourceKeeper();
        seedStock(resourceId, 0.0, 5.0);
        long orderId = createOrderThroughUi(resourceName, 5.0);
        OrderListPage adminPage = takeToWorkAndChooseGathering(orderId);
        long taskId = adminPage.createRelocationTask(sourceStorage.getName(), resourceName, 5.0);
        assertThat(readBookedAmount(sourceStorage.getId(), resourceId)).isEqualTo(5.0);

        String supplyMarker = marker("task-supply");
        switchActor(SOURCE_KEEPER, sourceKeeper, sourceStorage.getId());
        new ProductionTasksPage(page)
                .openRelocationRequest(taskId)
                .fillDescription(supplyMarker)
                .fillInvoiceIssuerDefaults()
                .confirmTaskSend();
        assertThat(relocationTask(orderId, taskId).getState())
                .isEqualTo(OrderRelocationTaskState.SHIPPED);

        switchActor(ISOLATED_GATHERER, isolatedGatherer, isolatedGatheringStorage.getId());
        new RelocationPage(page).open().openInTransitTab().acceptInTransitAsRecipient(supplyMarker);
        assertThat(relocationTask(orderId, taskId).getState())
                .isEqualTo(OrderRelocationTaskState.DONE);
        assertThat(orderFixture.getBookings(ORDER_ADMIN, orderId))
                .anySatisfy(booking -> assertThat(booking.getAmount()).isEqualByComparingTo("5"));

        String finalMarker = shipReadyOrderAsGatherer(orderId, resourceName, true, "remote-final");
        receiveAsRequesterAndAssertDone(orderId, finalMarker);
    }

    @Test(priority = 40)
    @TestCaseId(value = "TC-ORD-E2E-004", roles = {
            BusinessRole.ORDER_ADMIN, BusinessRole.BUSINESS_UNIT_OWNER})
    @Story("Production shortfall handoff and fulfillment")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Order Admin виявляє виробничий дефіцит на чужій локації збору; глобальний Admin створює та генерує ВЗ; виробник виконує задачу; результат autobook і відправляється замовнику.")
    public void productionShortfallIsProducedAutoBookedAndDelivered() {
        ensureProductionScenario();
        double quantity = 2.0;
        seedStock(productionResource.getId(), 0.0, 0.0);
        relocationFixture.seedExactStock(
                isolatedGatheringStorage.getId(), resourceId, 20.0, UserRole.ADMIN);
        long orderId = createOrderThroughUi(productionResource.getName(), quantity);

        OrderListPage adminPage = takeToWorkAndChooseGathering(orderId)
                .openProductionShortfallDialog();
        assertThat(adminPage.isNewProductionOrderEnabled())
                .as("Order Admin cannot create a production order outside its location-head scope")
                .isFalse();

        switchActor(UserRole.ADMIN, null, requesterStorageId);
        ProductionOrderWizardPage wizard = new OrderListPage(page)
                .openDeepLink(orderId)
                .openProductionShortfallDialog()
                .clickNewProductionOrder();
        long productionOrderId = wizard.createPrefilledOrder();
        methodProductionOrderIds.add(productionOrderId);

        ProductionGroupPlanningPage planning = new ProductionGroupPlanningPage(page);
        planning.assign(productionResource.getName(), isolatedGatheringStorage.getName(), (int) quantity);
        planning.step4();
        planning.clickApi(
                "Згенерувати",
                "POST",
                "/production-orders/" + productionOrderId + "/generate");

        switchActor(PRODUCTION_WORKER, productionWorker, isolatedGatheringStorage.getId());
        ProductionCreateFormPage form = new ProductionTasksPage(page)
                .open()
                .openProductionTask(productionOrderId, productionResource.getName())
                .ensureShiftSelected()
                .waitUntilSubmitEnabled();
        form.submitExpectSuccess();

        String finalMarker = shipReadyOrderAsGatherer(
                orderId, productionResource.getName(), true, "production-final");
        receiveAsRequesterAndAssertDone(orderId, finalMarker);
    }

    @Test(priority = 45)
    @TestCaseId(value = "TC-ORD-E2E-012", roles = {
            BusinessRole.BUSINESS_UNIT_OWNER, BusinessRole.ORDER_ADMIN})
    @Story("Relocation request maximum ignores gathering stock")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Для замовлення 5 із залишком 2 на зборі UI встановлює максимум запиту 5; створення і скасування запиту коректно резервує та звільняє джерело.")
    public void relocationRequestMaximumEqualsOrderQuantity() {
        seedStock(resourceId, 2.0, 5.0);
        long orderId = createOrderThroughUi(resourceName, 5.0);
        OrderListPage adminPage = takeToWorkAndChooseGathering(orderId);

        long taskId = adminPage.createRelocationTaskCheckingMaximum(
                sourceStorage.getName(), resourceName, 5.0, 5.0);
        assertThat(readBookedAmount(sourceStorage.getId(), resourceId)).isEqualTo(5.0);

        adminPage.cancelRelocationTask(taskId);
        assertThat(relocationTask(orderId, taskId).getState())
                .isEqualTo(OrderRelocationTaskState.CANCELLED);
        assertThat(readBookedAmount(sourceStorage.getId(), resourceId)).isZero();
    }

    @Test(priority = 50)
    @TestCaseId(value = "TC-ORD-E2E-005", roles = {
            BusinessRole.BUSINESS_UNIT_OWNER, BusinessRole.ORDER_ADMIN})
    @Story("Partial completion after cancelling an unused dependency")
    @Severity(SeverityLevel.CRITICAL)
    @Description("За наявності 2 із 5 одиниць на зборі UI дозволяє запитати всі 5; після скасування task локальна частина бронюється і видається.")
    public void cancelledRelocationDependencyReleasesHoldBeforePartialDelivery() {
        seedStock(resourceId, 2.0, 5.0);
        long orderId = createOrderThroughUi(resourceName, 5.0);
        OrderListPage adminPage = takeToWorkAndChooseGathering(orderId);
        long taskId = adminPage.createRelocationTask(sourceStorage.getName(), resourceName, 5.0);
        assertThat(readBookedAmount(sourceStorage.getId(), resourceId)).isEqualTo(5.0);

        adminPage.cancelRelocationTask(taskId);
        assertThat(relocationTask(orderId, taskId).getState())
                .isEqualTo(OrderRelocationTaskState.CANCELLED);
        assertThat(readBookedAmount(sourceStorage.getId(), resourceId)).isZero();
        adminPage.bookResource(resourceName, 2.0).markReadyToDeliver();

        String marker = shipReadyOrderAsGatherer(orderId, resourceName, true, "cancel-task-partial");
        receiveAsRequesterAndAssertDone(orderId, marker);
    }

    @Test(priority = 60)
    @TestCaseId(value = "TC-ORD-E2E-006", roles = {
            BusinessRole.BUSINESS_UNIT_OWNER, BusinessRole.ORDER_ADMIN})
    @Story("Mixed local booking and relocation complete an order")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Order Admin бронює локальну частину та створює relocation task на решту; після переміщення вся кількість заброньована, видана й прийнята.")
    public void partialLocalStockAndPartialRelocationCompleteOrder() {
        ensureSourceKeeper();
        seedStock(resourceId, 2.0, 3.0);
        long orderId = createOrderThroughUi(resourceName, 5.0);

        OrderListPage adminPage = takeToWorkAndChooseGathering(orderId)
                .bookResource(resourceName, 2.0);
        long taskId = adminPage.createRelocationTask(
                sourceStorage.getName(), resourceName, 3.0);
        assertThat(activeBookingAmount(orderId, resourceId)).isEqualTo(2.0);
        assertThat(readBookedAmount(sourceStorage.getId(), resourceId)).isEqualTo(3.0);

        fulfillRelocationTask(orderId, taskId);
        assertThat(activeBookingAmount(orderId, resourceId)).isEqualTo(5.0);

        String marker = shipReadyOrderAsGatherer(
                orderId, resourceName, true, "mixed-relocation-final");
        receiveAsRequesterAndAssertDone(orderId, marker);
    }

    @Test(priority = 70)
    @TestCaseId(value = "TC-ORD-E2E-007", roles = {
            BusinessRole.ORDER_ADMIN, BusinessRole.BUSINESS_UNIT_OWNER})
    @Story("Mixed local booking and completed production complete an order")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Order Admin бронює локальну частину; Admin створює ВЗ на нестачу; виробництво завершується, результат autobook, замовлення видається повністю.")
    public void partialLocalStockAndCompletedProductionCompleteOrder() {
        ensureProductionScenario();
        seedStock(productionResource.getId(), 2.0, 0.0);
        seedProductionInput();
        long orderId = createOrderThroughUi(productionResource.getName(), 5.0);

        takeToWorkAndChooseGathering(orderId)
                .bookResource(productionResource.getName(), 2.0);
        long productionOrderId = createAndGenerateProductionOrder(orderId, 3);
        completeProductionOrder(productionOrderId);

        assertThat(productionOrderFixture.getById(UserRole.ADMIN, productionOrderId).getState())
                .isEqualTo(ProductionOrderState.DONE);
        assertThat(activeBookingAmount(orderId, productionResource.getId())).isEqualTo(5.0);

        String marker = shipReadyOrderAsGatherer(
                orderId, productionResource.getName(), true, "mixed-production-final");
        receiveAsRequesterAndAssertDone(orderId, marker);
    }

    @Test(priority = 80)
    @TestCaseId(value = "TC-ORD-E2E-008", roles = {
            BusinessRole.ORDER_ADMIN, BusinessRole.BUSINESS_UNIT_OWNER})
    @Story("Partial delivery while production has not completed")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Order Admin бронює локальну частину та розміщує ВЗ на нестачу; виробництво не виконується; Admin підтверджує часткову готовність і видається лише бронь.")
    public void incompleteProductionLeavesOnlyLocalPartForDelivery() {
        ensureProductionScenario();
        seedStock(productionResource.getId(), 2.0, 0.0);
        seedProductionInput();
        long orderId = createOrderThroughUi(productionResource.getName(), 5.0);

        OrderListPage adminPage = takeToWorkAndChooseGathering(orderId)
                .bookResource(productionResource.getName(), 2.0);
        long productionOrderId = createAndGenerateProductionOrder(orderId, 3);
        assertThat(productionOrderFixture.getById(UserRole.ADMIN, productionOrderId).getState())
                .isNotEqualTo(ProductionOrderState.DONE);
        assertThat(activeBookingAmount(orderId, productionResource.getId())).isEqualTo(2.0);

        switchActor(ORDER_ADMIN, orderAdmin, requesterStorageId);
        new OrderListPage(page)
                .openDeepLink(orderId)
                .waitForBookingPanel()
                .markReadyToDeliver();

        String marker = shipReadyOrderAsGatherer(
                orderId, productionResource.getName(), true, "production-pending-partial");
        receiveAsRequesterAndAssertDone(orderId, marker);
        assertThat(productionOrderFixture.getById(UserRole.ADMIN, productionOrderId).getState())
                .as("Production must remain unfinished when the order is partially delivered")
                .isNotEqualTo(ProductionOrderState.DONE);
    }

    @Test(priority = 90)
    @TestCaseId(value = "TC-ORD-E2E-009", roles = {BusinessRole.BUSINESS_UNIT_OWNER})
    @Story("Order author can edit and delete a NEW order")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Автор бачить редагування і скасування нового замовлення; видалення реалізоване доменною операцією cancel та переводить замовлення у CANCELLED.")
    public void requesterCanEditAndDeleteNewOrder() {
        seedStock(resourceId, 1.0, 0.0);
        long orderId = createOrderThroughUi(resourceName, 1.0);
        OrderListPage requesterPage = new OrderListPage(page).openDeepLink(orderId);

        assertThat(requesterPage.isEditOrderVisible())
                .as("NEW order author must be able to edit the order")
                .isTrue();
        assertThat(requesterPage.isCancelOrderVisible())
                .as("NEW order author must be able to delete/cancel the order")
                .isTrue();
        requesterPage.clickCancelOrder();

        assertThat(orderFixture.getById(REQUESTER, orderId).getState())
                .isEqualTo(OrderState.CANCELLED);
    }

    @Test(priority = 100)
    @TestCaseId(value = "TC-ORD-E2E-010", roles = {
            BusinessRole.BUSINESS_UNIT_OWNER, BusinessRole.ORDER_ADMIN})
    @Story("Order administrator cancels a fully booked order")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Order Admin бере замовлення в роботу, повністю бронює з точки комплектації та скасовує його; активні броні звільняються.")
    public void orderAdminCancelsFullyBookedOrder() {
        seedStock(resourceId, 5.0, 0.0);
        long orderId = createOrderThroughUi(resourceName, 5.0);

        OrderListPage adminPage = takeToWorkAndChooseGathering(orderId)
                .bookResource(resourceName, 5.0);
        adminPage = reloadOrder(orderId).waitForOrderState("Готово до доставки");
        assertThat(activeBookingAmount(orderId, resourceId)).isEqualTo(5.0);
        assertThat(adminPage.isCancelOrderVisible()).isTrue();
        adminPage.clickCancelOrder();

        assertThat(orderFixture.getById(ORDER_ADMIN, orderId).getState())
                .isEqualTo(OrderState.CANCELLED);
        assertThat(activeBookingAmount(orderId, resourceId)).isZero();
    }

    @Test(priority = 110)
    @TestCaseId(value = "TC-ORD-E2E-011", roles = {
            BusinessRole.BUSINESS_UNIT_OWNER, BusinessRole.ORDER_ADMIN})
    @Story("Author permissions are removed after an order enters work")
    @Severity(SeverityLevel.CRITICAL)
    @Description("У статусі В РОБОТІ автор не бачить редагування або видалення; Order Admin може скасувати замовлення і перевести його у CANCELLED.")
    public void requesterCannotEditOrDeleteInProgressButAdminCanCancel() {
        seedStock(resourceId, 2.0, 0.0);
        long orderId = createOrderThroughUi(resourceName, 5.0);
        takeToWorkAndChooseGathering(orderId)
                .bookResource(resourceName, 2.0);

        switchActor(REQUESTER, null, requesterStorageId);
        OrderListPage requesterPage = new OrderListPage(page).openDeepLink(orderId);
        assertThat(requesterPage.isEditOrderVisible())
                .as("IN_PROGRESS order author must not be able to edit")
                .isFalse();
        assertThat(requesterPage.isCancelOrderVisible())
                .as("IN_PROGRESS order author must not be able to delete/cancel")
                .isFalse();

        switchActor(ORDER_ADMIN, orderAdmin, requesterStorageId);
        OrderListPage adminPage = new OrderListPage(page).openDeepLink(orderId);
        assertThat(adminPage.isCancelOrderVisible()).isTrue();
        adminPage.clickCancelOrder();
        assertThat(orderFixture.getById(ORDER_ADMIN, orderId).getState())
                .isEqualTo(OrderState.CANCELLED);
    }

    private UserFixture.BusinessActor createActor(
            BusinessRole businessRole, UserRole slot, StorageResponse storage) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                UserFixture.BusinessActor actor = userFixture.createBusinessActor(
                        getPlaywrightSessionProvider(), businessRole, List.of(storage));
                apiExecutor.setSessionForRole(slot, actor.username(), actor.password());
                return actor;
            } catch (RuntimeException e) {
                last = e;
                log.warn("Create {} actor attempt {}/3 failed: {}", businessRole, attempt, e.getMessage());
            }
        }
        throw last;
    }

    private void ensureProductionScenario() {
        if (productionResource != null) {
            return;
        }
        productionWorker = createActor(
                BusinessRole.BUSINESS_UNIT_OWNER, PRODUCTION_WORKER, isolatedGatheringStorage);
        productionResource = resourceFixture.createUniqueResource("ord-e2e-production-");
        // A zero inventory record grants the requester dictionary visibility without adding stock.
        relocationFixture.seedExactStock(requesterStorageId, productionResource.getId(), 1.0, UserRole.ADMIN);
        inventoryFixture.resetResourceStock(
                requesterStorageId, productionResource.getId(), 0.0, UserRole.ADMIN);
        productionTechMap = technologicalMapFixture.createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionMapWithStorages(
                                "ord-e2e-production-map",
                                List.of(new ResourceUsageRequest(resourceId, 1.0)),
                                List.of(new ResourceUsageRequest(productionResource.getId(), 1.0)),
                                Set.of(isolatedGatheringStorage.getId()))
                        .build());
        shiftFixture.create(
                UserRole.ADMIN,
                isolatedGatheringStorage.getId(),
                shiftFixture.uniqueRequest("ord-e2e-shift-"));
    }

    private void ensureSourceKeeper() {
        if (sourceKeeper == null) {
            sourceKeeper = createActor(
                    BusinessRole.BUSINESS_UNIT_OWNER, SOURCE_KEEPER, sourceStorage);
        }
    }

    private void seedProductionInput() {
        relocationFixture.seedExactStock(
                isolatedGatheringStorage.getId(), resourceId, 20.0, UserRole.ADMIN);
    }

    private long createAndGenerateProductionOrder(long orderId, int quantity) {
        switchActor(UserRole.ADMIN, null, requesterStorageId);
        ProductionOrderWizardPage wizard = new OrderListPage(page)
                .openDeepLink(orderId)
                .openProductionShortfallDialog()
                .clickNewProductionOrder();
        long productionOrderId = wizard.createPrefilledOrder();
        methodProductionOrderIds.add(productionOrderId);

        ProductionGroupPlanningPage planning = new ProductionGroupPlanningPage(page);
        planning.assign(productionResource.getName(), isolatedGatheringStorage.getName(), quantity);
        planning.step4();
        planning.clickApi(
                "Згенерувати",
                "POST",
                "/production-orders/" + productionOrderId + "/generate");
        return productionOrderId;
    }

    private void completeProductionOrder(long productionOrderId) {
        switchActor(PRODUCTION_WORKER, productionWorker, isolatedGatheringStorage.getId());
        ProductionCreateFormPage form = new ProductionTasksPage(page)
                .open()
                .openProductionTask(productionOrderId, productionResource.getName())
                .ensureShiftSelected()
                .waitUntilSubmitEnabled();
        form.submitExpectSuccess();
    }

    private void fulfillRelocationTask(long orderId, long taskId) {
        String supplyMarker = marker("mixed-task-supply");
        switchActor(SOURCE_KEEPER, sourceKeeper, sourceStorage.getId());
        new ProductionTasksPage(page)
                .openRelocationRequest(taskId)
                .fillDescription(supplyMarker)
                .fillInvoiceIssuerDefaults()
                .confirmTaskSend();
        assertThat(relocationTask(orderId, taskId).getState())
                .isEqualTo(OrderRelocationTaskState.SHIPPED);

        switchActor(ISOLATED_GATHERER, isolatedGatherer, isolatedGatheringStorage.getId());
        new RelocationPage(page).open().openInTransitTab().acceptInTransitAsRecipient(supplyMarker);
        assertThat(relocationTask(orderId, taskId).getState())
                .isEqualTo(OrderRelocationTaskState.DONE);
    }

    private void seedStock(Long testedResourceId, double gatheringAmount, double sourceAmount) {
        // RelocationFixture.seedExactStock only raises stock to a minimum and
        // cannot reduce leftovers from a previous method. Inventory gives the
        // two isolated locations an exact, repeatable baseline.
        inventoryFixture.resetResourceStock(
                isolatedGatheringStorage.getId(), testedResourceId, gatheringAmount, UserRole.ADMIN);
        inventoryFixture.resetResourceStock(
                sourceStorage.getId(), testedResourceId, sourceAmount, UserRole.ADMIN);
    }

    private long createOrderThroughUi(String testedResourceName, double quantity) {
        switchActor(REQUESTER, null, requesterStorageId);
        OrderListPage pageObject = new OrderListPage(page)
                .open()
                .clickCreateOrder()
                .ensureDeliveryStorageSelected(requesterStorageName)
                .fillCreateResourceLine(testedResourceName, formatAmount(quantity));
        long orderId = pageObject.submitCreateDialogAndGetOrderId();
        methodOrderIds.add(orderId);
        assertThat(pageObject.isOrderStateVisible("Нове")).isTrue();
        return orderId;
    }

    private OrderListPage takeToWorkAndChooseGathering(long orderId) {
        switchActor(ORDER_ADMIN, orderAdmin, requesterStorageId);
        OrderListPage pageObject = new OrderListPage(page)
                .openDeepLink(orderId)
                .clickTakeToWork()
                .selectGatheringStorage(isolatedGatheringStorage.getName());
        assertThat(pageObject.isOrderStateVisible("В роботі")).isTrue();
        return pageObject;
    }

    private OrderListPage reloadOrder(long orderId) {
        page.reload();
        return new OrderListPage(page)
                .waitForLoaded()
                .waitForOrderDialog(orderId)
                .waitForBookingPanel();
    }

    private String shipReadyOrderAsGatherer(
            long orderId, String testedResourceName, boolean prepare, String prefix) {
        switchActor(ISOLATED_GATHERER, isolatedGatherer, isolatedGatheringStorage.getId());
        OrderListPage gatheringPage = new OrderListPage(page)
                .openDeepLink(orderId)
                .waitForOrderState("Готово до доставки");
        if (prepare) {
            gatheringPage.markBookingPrepared(testedResourceName);
        }
        assertThat(gatheringPage.isSendOrderEnabled()).isTrue();
        String marker = marker(prefix);
        RelocationCreateOutputPage output = gatheringPage.clickSendOrder();
        output.fillDescription(marker).fillInvoiceIssuerDefaults().confirmSend();
        return marker;
    }

    private void receiveAsRequesterAndAssertDone(long orderId, String shipmentMarker) {
        switchActor(REQUESTER, null, requesterStorageId);
        new RelocationPage(page)
                .open()
                .openInTransitTab()
                .acceptInTransitAsRecipient(shipmentMarker);
        new OrderListPage(page)
                .openDeepLink(orderId)
                .waitForOrderState("Виконано");
        assertThat(orderFixture.getById(REQUESTER, orderId).getState()).isEqualTo(OrderState.DONE);
    }

    private void switchActor(
            UserRole role, UserFixture.BusinessActor actor, long selectedStorageId) {
        if (page != null) {
            try {
                page.close();
            } catch (Exception ignored) {
                // context close below is authoritative
            }
        }
        if (browserContext != null) {
            browserContext.close();
        }
        browserContext = getPlaywrightSessionProvider().getBrowser().newContext(
                new Browser.NewContextOptions()
                        .setIgnoreHTTPSErrors(true)
                        .setAcceptDownloads(true)
                        .setViewportSize(1600, 1100));
        Map<String, String> cookies = actor == null
                ? orderRoleSession(role)
                : authService.getSessionForUser(actor.username(), actor.password());
        injectSessionCookies(cookies, sessionCookieDomain());
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
        page = browserContext.newPage();
        int timeoutMs = ConfigProvider.getUiTimeoutSeconds() * 1000;
        page.setDefaultTimeout(timeoutMs);
        page.setDefaultNavigationTimeout(timeoutMs);
    }

    private OrderRelocationTaskResponse relocationTask(long orderId, long taskId) {
        return relocationTaskFixture.getForOrder(ORDER_ADMIN, orderId).stream()
                .filter(task -> task.getId().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Relocation task not found: " + taskId));
    }

    private double readBookedAmount(Long storageId, Long testedResourceId) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_INVENTORY_MULTI_GET,
                UserRole.ADMIN,
                Map.of(
                        "locations", storageId,
                        "resourceIds", testedResourceId,
                        "size", 5));
        assertThat(response.statusCode()).isEqualTo(200);
        List<MultiLocationStorageItemResponse> content =
                response.jsonPath().getList("content", MultiLocationStorageItemResponse.class);
        if (content == null || content.isEmpty() || content.getFirst().getLocations() == null) {
            return 0.0;
        }
        return content.getFirst().getLocations().stream()
                .filter(location -> location.getStorage() != null
                        && storageId.equals(location.getStorage().getId()))
                .map(StorageAmountResponse::getBookedAmount)
                .filter(value -> value != null)
                .findFirst()
                .orElse(0.0);
    }

    private double activeBookingAmount(long orderId, long testedResourceId) {
        return orderFixture.getBookings(ORDER_ADMIN, orderId).stream()
                .filter(booking -> booking.getResourceId().equals(testedResourceId))
                .filter(booking -> booking.getState().name().equals("ACTIVE"))
                .mapToDouble(booking -> booking.getAmount().doubleValue())
                .sum();
    }

    private static String marker(String prefix) {
        return "ord-e2e-" + prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String formatAmount(double amount) {
        return amount == Math.rint(amount) ? String.valueOf((long) amount) : String.valueOf(amount);
    }
}
