package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.BusinessRole;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProductionFixture;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.ShiftFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.TechnologicalMapAlternativeGroupResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.ResourceUsageResponse;
import com.erp.pages.ProductionCreateFormPage;
import com.erp.pages.ProductionUpdateFormPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Production")
@Feature("Batch recipe lock UI")
public class ProductionBatchRecipeConsistencyUiTest extends BaseUITest {

    private static final double MIN_STOCK = 200.0;

    private final List<Long> createdProductionIds = new ArrayList<>();
    private ProductionFixture productionFixture;
    private TechnologicalMapFixture techMapFixture;
    private StorageFixture storageFixture;
    private ShiftFixture shiftFixture;
    private Long shiftId;
    private UserFixture userFixture;
    private ResourceFixture resourceFixture;
    private final Set<Long> createdResourceIds = new LinkedHashSet<>();
    private UserFixture.BusinessActor ownerActor;
    private Long storageId;
    private TechnologicalMapResponse firstMap;
    private TechnologicalMapResponse secondMap;
    private String productName;
    private String groupName;
    private String firstAlternativeName;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        StorageResponse storage = storageFixture.createProductionStorage(
                ConfigProvider.getOwner1StorageId(), "ui-prd-recipe-");
        storageId = storage.getId();
        userFixture = new UserFixture(testContext, apiExecutor);
        ownerActor = userFixture.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.BUSINESS_UNIT_OWNER, List.of(storage));
        apiExecutor.setSessionForRole(UserRole.OWNER_1, ownerActor.username(), ownerActor.password());
        shiftFixture = new ShiftFixture(testContext, apiExecutor);
        shiftId = shiftFixture.create(UserRole.ADMIN, storageId,
                shiftFixture.uniqueRequest("ui-prd-recipe-")).getId();
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        resourceFixture.fetchSharedUnit(1);
        resourceFixture.fetchSharedResourceCategory();
        productionFixture = new ProductionFixture(testContext, apiExecutor);
        techMapFixture = productionFixture.getTechMapFixture();
        techMapFixture.setMode(storageId, StorageTechnologicalMapMode.EDIT_ALLOWED);
        firstMap = techMapFixture.createTechMapWithAlternativeGroup(UserRole.ADMIN, storageId);
        trackMapResources(firstMap);
        secondMap = techMapFixture.createAlternateActiveTechMap(UserRole.ADMIN, firstMap);
        productName = firstMap.getOutput().getFirst().getResource().getName().trim();
        groupName = firstMap.getGroups().getFirst().getName();
        firstAlternativeName = firstMap.getGroups().getFirst().getAlternativeResources().stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsDefault()))
                .map(TechnologicalMapAlternativeGroupResourceResponse::getResource)
                .map(resource -> resource.getName().trim())
                .findFirst()
                .orElseThrow();
    }

    @BeforeMethod(alwaysRun = true)
    public void prepareUiSession() {
        if (storageId == null || firstMap == null || secondMap == null) {
            throw new SkipException("Production recipe class fixture setup did not complete");
        }
        productionFixture.ensureStockForTechMapInputs(storageId, firstMap, MIN_STOCK);
        productionFixture.ensureStockForTechMapInputs(storageId, secondMap, MIN_STOCK);
        injectRoleSession(UserRole.OWNER_1, storageId);
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupProductions() {
        List<Long> reverse = new ArrayList<>(createdProductionIds);
        Collections.reverse(reverse);
        for (Long id : reverse) {
            try {
                productionFixture.deleteAs(UserRole.ADMIN, id, storageId);
            } catch (RuntimeException cleanupError) {
                log.warn("Failed to delete UI production {} during cleanup", id, cleanupError);
            }
        }
        createdProductionIds.clear();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupMaps() {
        if (shiftFixture != null && shiftId != null && storageId != null) {
            try {
                shiftFixture.deleteRaw(UserRole.ADMIN, shiftId, storageId);
            } catch (RuntimeException cleanupError) {
                log.warn("Failed to delete UI production shift {}", shiftId, cleanupError);
            }
        }
        if (techMapFixture == null || storageId == null) {
            if (storageFixture != null) {
                storageFixture.deactivateTrackedStorages(UserRole.ADMIN);
            }
            if (apiExecutor != null) {
                apiExecutor.evictSessionForRole(UserRole.OWNER_1);
            }
            if (userFixture != null) {
                userFixture.deactivateTrackedUsers();
            }
            cleanupResources();
            return;
        }
        try {
            if (secondMap != null) {
                techMapFixture.deactivateTechMap(UserRole.ADMIN, secondMap.getId(), storageId);
            }
        } catch (RuntimeException cleanupError) {
            log.warn("Failed to deactivate second UI tech map", cleanupError);
        }
        try {
            if (firstMap != null) {
                techMapFixture.deactivateTechMap(UserRole.ADMIN, firstMap.getId(), storageId);
            }
        } catch (RuntimeException cleanupError) {
            log.warn("Failed to deactivate first UI tech map", cleanupError);
        }
        try {
            techMapFixture.setMode(storageId, StorageTechnologicalMapMode.READ_ONLY);
        } catch (RuntimeException cleanupError) {
            log.warn("Failed to restore READ_ONLY tech-map mode", cleanupError);
        }
        InventoryFixture inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        for (Long resourceId : createdResourceIds) {
            try {
                inventoryFixture.removeResourceFromStorage(storageId, resourceId, UserRole.ADMIN);
            } catch (RuntimeException cleanupError) {
                log.warn("Failed to remove UI recipe resource {} from stock", resourceId, cleanupError);
            }
        }
        if (storageFixture != null) {
            storageFixture.deactivateTrackedStorages(UserRole.ADMIN);
        }
        cleanupResources();
        apiExecutor.evictSessionForRole(UserRole.OWNER_1);
        if (userFixture != null) {
            userFixture.deactivateTrackedUsers();
        }
    }

    private void trackMapResources(TechnologicalMapResponse map) {
        map.getInput().stream().map(ResourceUsageResponse::getResource)
                .forEach(resource -> createdResourceIds.add(resource.getId()));
        map.getOutput().stream().map(ResourceUsageResponse::getResource)
                .forEach(resource -> createdResourceIds.add(resource.getId()));
        map.getGroups().stream()
                .flatMap(group -> group.getAlternativeResources().stream())
                .map(TechnologicalMapAlternativeGroupResourceResponse::getResource)
                .forEach(resource -> createdResourceIds.add(resource.getId()));
    }

    private void cleanupResources() {
        if (resourceFixture == null) {
            return;
        }
        for (Long resourceId : createdResourceIds) {
            try {
                var response = resourceFixture.deactivate(UserRole.ADMIN, resourceId);
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    log.warn("Failed to deactivate UI recipe resource {}: HTTP {}: {}",
                            resourceId, response.statusCode(), response.asString());
                }
            } catch (RuntimeException cleanupError) {
                log.warn("Failed to deactivate UI recipe resource {}", resourceId, cleanupError);
            }
        }
        createdResourceIds.clear();
    }

    @Test(priority = 10)
    @TestCaseId("TC-UI-PROD-BATCH-001")
    @Story("Non-empty batch locks recipe")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Create form: existing batch auto-fills its tech map and alternative, disables both and shows the batch hint.")
    public void nonEmptyBatchAutofillsAndLocksRecipe() {
        String batch = createAnchor(firstMap);

        ProductionCreateFormPage form = openFormForProduct()
                .fillBatchNumber(batch)
                .waitForBatchRecipeLock(batch);

        assertThat(form.getSelectedTechMapLabel()).contains(firstMap.getName());
        assertThat(form.isTechMapDisabled()).isTrue();
        assertThat(form.getSelectedAlternativeResourceLabel(groupName)).contains(firstAlternativeName);
        assertThat(form.isAlternativeSelectionDisabled(groupName)).isTrue();
        assertThat(form.getBatchRecipeHint())
                .isEqualTo("Техкарту визначено партією " + batch
                        + ", якщо треба використати іншу - вкажіть інший номер партії для цього запису");
        form.attachScreenshot("TC-UI-PROD-BATCH-001 — recipe locked by batch");
    }

    @Test(priority = 11)
    @TestCaseId("TC-UI-PROD-BATCH-002")
    @Story("Empty batch keeps recipe editable")
    @Severity(SeverityLevel.CRITICAL)
    public void emptyBatchLeavesRecipeFieldsEditable() {
        String emptyBatch = ProductionDataFactory.uniqueBatchNumber();

        ProductionCreateFormPage form = openFormForProduct()
                .fillBatchNumber(emptyBatch)
                .waitForBatchRecipeUnlock();

        assertThat(form.isTechMapDisabled()).isFalse();
        assertThat(form.getBatchRecipeHint()).isBlank();
        form.attachScreenshot("TC-UI-PROD-BATCH-002 — empty batch editable");
    }

    @Test(priority = 12)
    @TestCaseId("TC-UI-PROD-BATCH-003")
    @Story("Switch from non-empty to empty batch")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Recipe values are retained as an editable starting copy when the new batch is empty.")
    public void switchingToEmptyBatchUnlocksAndRetainsRecipe() {
        String occupiedBatch = createAnchor(firstMap);
        String emptyBatch = ProductionDataFactory.uniqueBatchNumber();
        ProductionCreateFormPage form = openFormForProduct()
                .fillBatchNumber(occupiedBatch)
                .waitForBatchRecipeLock(occupiedBatch);

        form.fillBatchNumber(emptyBatch).waitForBatchRecipeUnlock();

        assertThat(form.getSelectedTechMapLabel()).contains(firstMap.getName());
        assertThat(form.getSelectedAlternativeResourceLabel(groupName)).contains(firstAlternativeName);
        assertThat(form.isAlternativeSelectionDisabled(groupName)).isFalse();
        form.attachScreenshot("TC-UI-PROD-BATCH-003 — retained recipe unlocked");
    }

    @Test(priority = 13)
    @TestCaseId("TC-UI-PROD-BATCH-004")
    @Story("Switch between two non-empty batches")
    @Severity(SeverityLevel.BLOCKER)
    public void switchingToAnotherNonEmptyBatchAppliesItsRecipe() {
        String firstBatch = createAnchor(firstMap);
        String secondBatch = createAnchor(secondMap);
        ProductionCreateFormPage form = openFormForProduct()
                .fillBatchNumber(firstBatch)
                .waitForBatchRecipeLock(firstBatch);

        form.fillBatchNumber(secondBatch).waitForBatchRecipeLock(secondBatch);

        assertThat(form.getSelectedTechMapLabel()).contains(secondMap.getName());
        assertThat(form.isTechMapDisabled()).isTrue();
        assertThat(form.getBatchRecipeHint()).contains(secondBatch);
        form.attachScreenshot("TC-UI-PROD-BATCH-004 — second batch recipe applied");
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-PROD-BATCH-005")
    @Story("Stored recipe and batch are immutable in edit form")
    @Severity(SeverityLevel.CRITICAL)
    public void editFormDisablesTechMapAndBatchNumber() {
        String batch = ProductionDataFactory.uniqueBatchNumber();
        ManufacturingItemResponse anchor = productionFixture.createAs(
                UserRole.OWNER_1, storageId, firstMap, 1.0, batch);
        createdProductionIds.add(anchor.getId());

        ProductionUpdateFormPage form = new ProductionUpdateFormPage(page)
                .open(anchor.getId(), storageId);

        assertThat(form.isTechMapDisabled()).isTrue();
        assertThat(form.isBatchNumberDisabled()).isTrue();
        assertThat(form.getBatchNumber()).isEqualTo(batch);
        assertThat(form.hasImmutableBatchHint()).isTrue();
        form.attachScreenshot("TC-UI-PROD-BATCH-005 — immutable edit fields");
    }

    private ProductionCreateFormPage openFormForProduct() {
        return new ProductionCreateFormPage(page).open()
                .ensureShiftSelected()
                .selectProduct(productName);
    }

    private String createAnchor(TechnologicalMapResponse map) {
        String batch = ProductionDataFactory.uniqueBatchNumber();
        ManufacturingItemResponse anchor = productionFixture.createAs(
                UserRole.OWNER_1, storageId, map, 1.0, batch);
        createdProductionIds.add(anchor.getId());
        return batch;
    }

    private void injectRoleSession(UserRole role, long selectedStorageId) {
        String username = role == UserRole.OWNER_1 && ownerActor != null
                ? ownerActor.username() : role.getUsername();
        String password = role == UserRole.OWNER_1 && ownerActor != null
                ? ownerActor.password() : role.getPassword();
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(username, password);
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');"
                        + "localStorage.setItem('selectedStorageId:" + username
                        + "', '" + selectedStorageId + "');");
    }
}
