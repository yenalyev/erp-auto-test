package com.erp.tests.functional.resource;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.inventory.InventoryDataFactory;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.RelocationState;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.AlertFixture;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.ProductionFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.models.request.InventoryRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageItemResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.AllureHelper;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional suite for resource deactivation (soft delete) and reactivation.
 *
 * <p>Covers visibility rules (dictionary, selectors, prices), guard conditions
 * (stock, tech map, relocation, alerts), and production journal restrictions.
 */
@Slf4j
@Epic("Master Data")
@Feature("Resources")
@Story("Deactivate resource")
public class ResourceDeactivationTest extends BaseFunctionalTest {

    private static final String DEACTIVATION_ERROR_FRAGMENT = "не може бути деактивований";
    private static final String PRODUCTION_DEACTIVATED_FRAGMENT = "деактивований";

    private ResourceFixture resourceFixture;
    private TechnologicalMapFixture techMapFixture;
    private RelocationFixture relocationFixture;
    private InventoryFixture inventoryFixture;
    private ProductionFixture productionFixture;
    private AlertFixture alertFixture;

    private Long owner1StorageId;
    private Long owner2StorageId;

    @BeforeClass(alwaysRun = true)
    @Step("Setup environment for Resource Deactivation tests")
    public void setupResourceDeactivationTest() {
        if (testContext == null) {
            baseTestClassSetup();
        }
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        productionFixture = new ProductionFixture(testContext, apiExecutor);
        alertFixture = new AlertFixture(testContext, apiExecutor);

        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();

        owner1StorageId = ConfigProvider.getOwner1StorageId();
        owner2StorageId = ConfigProvider.getOwner2StorageId();
    }

    // =========================================================================
    // Happy path
    // =========================================================================

    @Test(priority = 10)
    @TestCaseId("TC-RES-010")
    @Description("""
            ADMIN деактивує ресурс без залишків і без зв'язків.
            Після деактивації ресурс зникає зі словника (isActive=true),
            з autocomplete та з вкладки цін; доступний на сторінці «Деактивовані ресурси».
            Примітка: TC-RES-001 — створення ресурсу (ResourceTest), не деактивація.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testDeactivateResourceHidesFromDictionarySelectorsAndPrices() {
        ResourceResponse resource = Allure.step("Arrange: створити ізольований ресурс", () ->
                resourceFixture.createUniqueResource("deact-happy-"));

        String searchTerm = extractSearchToken(resource.getName());

        Response deactivateResponse = Allure.step("Act: DELETE /resources/{id} — деактивація", () ->
                resourceFixture.deactivate(UserRole.ADMIN, resource.getId()));

        Allure.step("Assert: деактивація успішна", () -> {
            assertThat(deactivateResponse.statusCode()).isEqualTo(200);
            ResourceResponse byId = resourceFixture.getById(UserRole.ADMIN, resource.getId());
            assertThat(byId.getActive()).isFalse();
        });

        Allure.step("Assert: ресурс відсутній у словнику активних (isActive=true)", () ->
                assertThat(resourceFixture.isPresentInActiveDictionary(UserRole.ADMIN, resource.getId(), searchTerm))
                        .as("Деактивований ресурс не повинен відображатися в словнику")
                        .isFalse());

        Allure.step("Assert: ресурс відсутній у autocomplete (includeArchived=false)", () ->
                assertThat(resourceFixture.isPresentInAutocomplete(UserRole.ADMIN, searchTerm, resource.getId(), false))
                        .as("Деактивований ресурс не повинен бути в селекторах")
                        .isFalse());

        Allure.step("Assert: ресурс відсутній на вкладці цін (isActive=true)", () ->
                assertThat(resourceFixture.isPresentInResourcePrices(UserRole.ADMIN, resource.getId()))
                        .as("Деактивований ресурс не повинен відображатися на вкладці цін")
                        .isFalse());

        Allure.step("Assert: ресурс присутній на сторінці деактивованих (isActive=false)", () ->
                assertThat(resourceFixture.isPresentInDeactivatedPage(UserRole.ADMIN, resource.getId(), searchTerm))
                        .as("Деактивований ресурс має бути на сторінці «Деактивовані ресурси»")
                        .isTrue());

        Allure.step("Assert: includeArchived=true повертає ресурс у autocomplete", () ->
                assertThat(resourceFixture.isPresentInAutocomplete(UserRole.ADMIN, searchTerm, resource.getId(), true))
                        .as("Архівний пошук має знаходити деактивований ресурс")
                        .isTrue());
    }

    @Test(priority = 11)
    @TestCaseId("TC-RES-011")
    @Description("ADMIN реактивує ресурс зі сторінки «Деактивовані ресурси» через PUT /resources/unarchive/{id}")
    @Severity(SeverityLevel.CRITICAL)
    public void testUnarchiveResourceRestoresActiveDictionary() {
        ResourceResponse resource = resourceFixture.createUniqueResource("deact-unarchive-");
        String searchTerm = extractSearchToken(resource.getName());
        Response deactivateResponse = resourceFixture.deactivate(UserRole.ADMIN, resource.getId());
        assertThat(deactivateResponse.statusCode()).isEqualTo(200);

        Response unarchiveResponse = Allure.step("Act: PUT /resources/unarchive/{id}", () ->
                resourceFixture.unarchive(UserRole.ADMIN, resource.getId()));

        Allure.step("Assert: реактивація успішна", () -> {
            assertThat(unarchiveResponse.statusCode()).isEqualTo(200);
            ResourceResponse reactivated = resourceFixture.getById(UserRole.ADMIN, resource.getId());
            assertThat(reactivated.getActive()).isNotEqualTo(Boolean.FALSE);
        });

        Allure.step("Assert: ресурс знову у словнику активних", () ->
                assertThat(resourceFixture.isPresentInActiveDictionary(UserRole.ADMIN, resource.getId(), searchTerm))
                        .isTrue());

        Allure.step("Assert: ресурс відсутній на сторінці деактивованих", () ->
                assertThat(resourceFixture.isPresentInDeactivatedPage(UserRole.ADMIN, resource.getId(), searchTerm))
                        .isFalse());
    }

    // =========================================================================
    // Guard conditions — deactivation must fail
    // =========================================================================

    @Test(priority = 20)
    @TestCaseId("TC-RES-012")
    @Description("Деактивація заборонена, якщо ресурс є в залишках internal локації")
    @Severity(SeverityLevel.CRITICAL)
    public void testCannotDeactivateResourceWithStock() {
        ResourceResponse resource = resourceFixture.createUniqueResource("deact-stock-");
        Long resourceId = resource.getId();
        double seedAmount = 50.0;

        try {
            Allure.step("Arrange: seed залишку на owner1 (ensureStock → fallback inventory)", () -> {
                try {
                    relocationFixture.ensureStock(owner1StorageId, resourceId, seedAmount);
                } catch (RuntimeException receiveFailed) {
                    log.warn("ensureStock failed, trying inventory conduct: {}", receiveFailed.getMessage());
                    try {
                        seedStockViaInventory(owner1StorageId, resourceId, seedAmount);
                    } catch (RuntimeException inventoryFailed) {
                        throw new SkipException(
                                "Не вдалось seed залишку на dev (ensureStock та inventory): "
                                        + receiveFailed.getMessage());
                    }
                }
                double stock = relocationFixture.getResourceStock(
                        owner1StorageId, resourceId, UserRole.OWNER_1);
                assertThat(stock)
                        .as("Перед деактивацією ресурс має мати залишок > 0")
                        .isGreaterThan(0);
            });

            Response response = Allure.step("Act: спроба деактивації ресурсу з залишками", () ->
                    resourceFixture.deactivate(UserRole.ADMIN, resourceId));

            Allure.step("Assert: 400 + error message, ресурс лишається активним", () -> {
                ResourceFixture.assertDeactivationRejected(response, DEACTIVATION_ERROR_FRAGMENT);
                ResourceFixture.assertAnyErrorMessageContains(response, "складі");
                ResourceFixture.assertResourceStillActive(
                        resourceFixture.getById(UserRole.ADMIN, resourceId));
            });
        } finally {
            Allure.step("Cleanup: прибрати залишок і деактивувати тестовий ресурс", () -> {
                try {
                    inventoryFixture.removeResourceFromStorage(
                            owner1StorageId, resourceId, UserRole.ADMIN);
                } catch (Exception e) {
                    log.warn("Stock cleanup failed for resource {}: {}", resourceId, e.getMessage());
                }
                Response deactivateResponse = resourceFixture.deactivate(UserRole.ADMIN, resourceId);
                if (deactivateResponse.statusCode() != 200) {
                    log.warn("Test resource {} deactivation after cleanup returned HTTP {}",
                            resourceId, deactivateResponse.statusCode());
                }
            });
        }
    }

    @Test(priority = 21)
    @TestCaseId("TC-RES-013")
    @Description("Деактивація заборонена, якщо ресурс входить до активної техкарти (input/output)")
    @Severity(SeverityLevel.CRITICAL)
    public void testCannotDeactivateResourceInActiveTechMap() {
        List<ResourceResponse> resources = createResourceTriple("deact-techmap-");
        techMapFixture.setMode(owner1StorageId, StorageTechnologicalMapMode.EDIT_ALLOWED);

        TechnologicalMapRequest techMapRequest = TechnologicalMapDataFactory
                .createProductionTechMap(resources, owner1StorageId)
                .build();

        TechnologicalMapResponse techMap = Allure.step("Arrange: створити активну техкарту", () -> {
            Response createResponse = apiExecutor.execute(
                    ApiEndpointDefinition.TECH_MAP_CREATE, UserRole.ADMIN, techMapRequest);
            assertThat(createResponse.statusCode()).isEqualTo(200);
            return createResponse.as(TechnologicalMapResponse.class);
        });

        ResourceResponse target = resources.getFirst();
        Response response = Allure.step("Act: спроба деактивації ресурсу з техкарти «" + techMap.getName() + "»", () ->
                resourceFixture.deactivate(UserRole.ADMIN, target.getId()));

        Allure.step("Assert: 400 + error message про технологічні карти", () -> {
            ResourceFixture.assertDeactivationRejected(response, DEACTIVATION_ERROR_FRAGMENT);
            ResourceFixture.assertAnyErrorMessageContains(response, "технологічних картах");
            ResourceFixture.assertResourceStillActive(
                    resourceFixture.getById(UserRole.ADMIN, target.getId()));
        });
    }

    @Test(priority = 22)
    @TestCaseId("TC-RES-014")
    @Description("Деактивація заборонена, якщо ресурс у переміщенні в статусі «В дорозі» (CREATED)")
    @Severity(SeverityLevel.CRITICAL)
    public void testCannotDeactivateResourceInTransitRelocation() {
        ResourceResponse resource = resourceFixture.createUniqueResource("deact-reloc-");
        Long resourceId = resource.getId();
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        double seedAmount = 50.0;
        double sendAmount = 10.0;
        boolean[] useNamedBatch = {true};

        Allure.step("Arrange: seed залишку (receive → fallback inventory)", () -> {
            relocationFixture.prepareContext();
            try {
                relocationFixture.seedBatchOnStorage(owner1StorageId, resourceId, seedAmount, batchNumber);
            } catch (RuntimeException receiveFailed) {
                log.warn("Receive seed failed, trying inventory conduct: {}", receiveFailed.getMessage());
                try {
                    seedStockViaInventory(owner1StorageId, resourceId, seedAmount);
                    useNamedBatch[0] = false;
                } catch (RuntimeException inventoryFailed) {
                    throw new SkipException(
                            "Не вдалось seed залишку на dev (receive та inventory): " + receiveFailed.getMessage());
                }
            }
        });

        Allure.step("Arrange: видача storage→storage (статус CREATED / «В дорозі»)", () -> {
            try {
                RelocationResponse relocation = useNamedBatch[0]
                        ? relocationFixture.createSendWithBatch(
                                UserRole.OWNER_1, owner1StorageId, owner2StorageId,
                                resourceId, sendAmount, batchNumber, false)
                        : relocationFixture.createSend(
                                UserRole.OWNER_1, owner1StorageId, owner2StorageId,
                                resourceId, sendAmount);
                assertThat(relocation.getState()).isEqualTo(RelocationState.CREATED);
            } catch (RuntimeException e) {
                throw new SkipException("Не вдалось створити переміщення на dev: " + e.getMessage());
            }
        });

        Response response = Allure.step("Act: спроба деактивації", () ->
                resourceFixture.deactivate(UserRole.ADMIN, resourceId));

        Allure.step("Assert: 400 + error message про переміщення", () -> {
            ResourceFixture.assertDeactivationRejected(response, DEACTIVATION_ERROR_FRAGMENT);
            ResourceFixture.assertAnyErrorMessageContains(response, "переміщеннях");
            ResourceFixture.assertResourceStillActive(
                    resourceFixture.getById(UserRole.ADMIN, resourceId));
        });
    }

    @Test(priority = 23)
    @TestCaseId("TC-RES-015")
    @Description("Деактивація заборонена, якщо ресурс у сповіщенні про залишки")
    @Severity(SeverityLevel.CRITICAL)
    public void testCannotDeactivateResourceInStockAlert() {
        AlertFixture.AlertSnapshot alertSnapshot = alertFixture.snapshotStorageAlert(
                owner1StorageId, UserRole.ADMIN);
        ResourceResponse resource = resourceFixture.createUniqueResource("deact-alert-");

        try {
            Allure.step("Arrange: створити сповіщення про залишки", () ->
                    alertFixture.createOrUpdateStockAlert(
                            UserRole.ADMIN, owner1StorageId, resource.getId(), 100.0));

            Response response = Allure.step("Act: спроба деактивації", () ->
                    resourceFixture.deactivate(UserRole.ADMIN, resource.getId()));

            Allure.step("Assert: 400 + error message про сповіщення", () -> {
                ResourceFixture.assertDeactivationRejected(response, DEACTIVATION_ERROR_FRAGMENT);
                ResourceFixture.assertAnyErrorMessageContains(response, "сповіщеннях");
                ResourceFixture.assertResourceStillActive(
                        resourceFixture.getById(UserRole.ADMIN, resource.getId()));
            });
        } finally {
            Allure.step("Cleanup: відновити сповіщення, прибрати phantom stock, деактивувати ресурс", () -> {
                alertFixture.restoreSnapshot(owner1StorageId, UserRole.ADMIN, alertSnapshot);
                try {
                    inventoryFixture.removeResourceFromStorage(
                            owner1StorageId, resource.getId(), UserRole.ADMIN);
                } catch (Exception e) {
                    log.warn("Phantom stock cleanup failed for resource {}: {}",
                            resource.getId(), e.getMessage());
                }
                Response deactivateResponse = resourceFixture.deactivate(UserRole.ADMIN, resource.getId());
                if (deactivateResponse.statusCode() != 200) {
                    log.warn("Test resource {} deactivation after cleanup returned HTTP {}",
                            resource.getId(), deactivateResponse.statusCode());
                }
            });
        }
    }

    // =========================================================================
    // Production journal guard
    // =========================================================================

    @Test(priority = 30)
    @TestCaseId("TC-RES-016")
    @Description("""
            Якщо складова техкарти запису виробництва деактивована —
            DELETE та PUT виробництва повертають 400 з повідомленням про деактивований ресурс.

            Відомий дефект продукту (прогін 37): deactivate input після production без leftover
            stock очікує HTTP 200, бекенд віддає 400 (ресурс ще «у використанні» / валідація
            залишків). Окремо DELETE після успішного deactivate може віддати 500 замість 400
            з errors[]. Тест червоний до фіксу в tk — очікування навмисно не послаблюємо.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testCannotModifyProductionWhenTechMapResourceDeactivated() {
        List<ResourceResponse> resources = createResourceTriple("deact-prod-");
        Long input1 = resources.get(0).getId();
        Long input2 = resources.get(1).getId();
        Long output = resources.get(2).getId();

        techMapFixture.setMode(owner1StorageId, StorageTechnologicalMapMode.EDIT_ALLOWED);
        TechnologicalMapRequest techMapRequest = TechnologicalMapDataFactory
                .createProductionTechMap(resources, owner1StorageId)
                .build();
        Response createTmResponse = apiExecutor.execute(
                ApiEndpointDefinition.TECH_MAP_CREATE, UserRole.ADMIN, techMapRequest);
        assertThat(createTmResponse.statusCode()).isEqualTo(200);
        TechnologicalMapResponse techMap = createTmResponse.as(TechnologicalMapResponse.class);

        try {
            // Exact seed (avoid ensureStock +50 pad) — usage 10+5 for amount=1
            RelocationStockSeeder.receiveFromSupplier(
                    apiExecutor, UserRole.ADMIN, owner1StorageId, Map.of(input1, 10.0, input2, 5.0));
        } catch (RuntimeException e) {
            throw new AssertionError("Не вдалось поповнити залишки для виробництва: " + e.getMessage(), e);
        }

        ManufacturingItemResponse production = Allure.step("Arrange: створити запис виробництва", () ->
                productionFixture.createAs(UserRole.OWNER_1, owner1StorageId, techMap, 1.0,
                        com.erp.data.factories.production.ProductionDataFactory.uniqueBatchNumber()));

        Allure.step("Arrange: обнулити leftover input stock перед deactivate", () -> {
            inventoryFixture.resetResourceStock(owner1StorageId, input1, 0.0, UserRole.ADMIN);
            inventoryFixture.resetResourceStock(owner1StorageId, input2, 0.0, UserRole.ADMIN);
        });

        Allure.step("Arrange: деактивувати техкарту (зняти блокування active tech map)", () -> {
            Response deactivateTm = apiExecutor.execute(
                    ApiEndpointDefinition.TECH_MAP_DEACTIVATE,
                    UserRole.ADMIN,
                    null,
                    String.valueOf(techMap.getId()),
                    String.valueOf(owner1StorageId));
            assertThat(deactivateTm.statusCode()).isEqualTo(200);
        });

        ResourceResponse resourceToDeactivate = resources.get(0);
        Response deactivateResource = Allure.step(
                "Arrange: деактивувати складову «" + resourceToDeactivate.getName() + "»", () ->
                        resourceFixture.deactivate(UserRole.ADMIN, resourceToDeactivate.getId()));

        Allure.step("Assert: deactivate input після production без leftover stock", () -> {
            AllureHelper.attachResponseDetails(deactivateResource);
            assertThat(deactivateResource.statusCode())
                    .as("deactivate resource id=%d after production (expect no leftover input stock)",
                            resourceToDeactivate.getId())
                    .isEqualTo(200);
        });

        Response deleteResponse = Allure.step("Act: DELETE виробництво з деактивованою складовою", () ->
                apiExecutor.execute(
                        ApiEndpointDefinition.PRODUCTION_DELETE,
                        UserRole.OWNER_1,
                        null,
                        production.getId(),
                        owner1StorageId));

        Allure.step("Assert: DELETE відхилено — ресурс деактивований", () -> {
            assertThat(deleteResponse.statusCode()).isEqualTo(400);
            String errorMessage = deleteResponse.jsonPath().getString("errors[0].messages[0]");
            assertThat(errorMessage)
                    .isNotBlank()
                    .contains(PRODUCTION_DEACTIVATED_FRAGMENT);
            AllureHelper.attachResponseDetails(deleteResponse);
        });

        Response updateResponse = Allure.step("Act: PUT оновлення виробництва з деактивованою складовою", () ->
                apiExecutor.execute(
                        ApiEndpointDefinition.PRODUCTION_PUT_UPDATE,
                        UserRole.OWNER_1,
                        com.erp.data.factories.production.ProductionDataFactory
                                .buildCreateRequest(techMap, 2.0, java.time.LocalDate.now(),
                                        com.erp.data.factories.production.ProductionDataFactory.uniqueBatchNumber()),
                        production.getId(),
                        owner1StorageId));

        Allure.step("Assert: PUT відхилено — ресурс деактивований", () -> {
            assertThat(updateResponse.statusCode()).isEqualTo(400);
            String errorMessage = updateResponse.jsonPath().getString("errors[0].messages[0]");
            assertThat(errorMessage)
                    .isNotBlank()
                    .contains(PRODUCTION_DEACTIVATED_FRAGMENT);
        });
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private List<ResourceResponse> createResourceTriple(String prefix) {
        List<ResourceResponse> resources = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            resources.add(resourceFixture.createUniqueResource(prefix + i + "-"));
        }
        return resources;
    }

    /** Seeds stock via open inventory session — avoids flaky {@code POST /relocations/receive} on dev. */
    private void seedStockViaInventory(long storageId, long resourceId, double amount) {
        inventoryFixture.ensureClosed(storageId);
        inventoryFixture.openSession(storageId);
        List<StorageItemResponse> items = inventoryFixture.listItems(storageId, UserRole.ADMIN);
        InventoryRequest request = InventoryDataFactory.mergeWithExisting(items, Map.of(resourceId, amount));
        inventoryFixture.conductInventory(storageId, UserRole.ADMIN, request);
        inventoryFixture.closeSession(storageId);
    }

    /** Uses a stable substring of the unique resource name for autocomplete search. */
    private static String extractSearchToken(String resourceName) {
        int underscore = resourceName.lastIndexOf('_');
        if (underscore > 0) {
            return resourceName.substring(0, underscore);
        }
        return resourceName.length() > 8 ? resourceName.substring(0, 8) : resourceName;
    }
}
