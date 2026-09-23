package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.PlanExecutionFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.request.ResourceCategoryRequest;
import com.erp.models.request.ResourceRequest;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.MeasurementUnitResponse;
import com.erp.models.response.PlanResponse;
import com.erp.models.response.ResourceCategoryResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.pages.PlanExecutionPage;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.utils.helpers.XlsxWorkbookReader;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import com.microsoft.playwright.Route;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Plans")
@Feature("Plan Execution improvement UI")
public class PlanExecutionImprovementUiTest extends BaseUITest {
    private PlanExecutionFixture fixture;
    private ResourceFixture resourceFixture;
    private InventoryFixture inventoryFixture;
    private StorageFixture storageFixture;
    private Long storageId;
    private Long activeStorageId;
    private final List<TechnologicalMapFixture.IsolatedTechMapContext> contexts = new ArrayList<>();
    private final List<ManufacturingItemResponse> productions = new ArrayList<>();
    private List<Long> previousFavourites;
    private boolean favouritesChanged;
    private PlanResponse currentPlan;
    private final List<Long> createdCategoryIds = new ArrayList<>();

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        fixture = new PlanExecutionFixture(testContext, apiExecutor);
        fixture.prepareContext();
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        storageFixture = new StorageFixture(testContext, apiExecutor);
        storageId = ConfigProvider.getOwner1StorageId();
        activeStorageId = storageId;
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupImprovementData() {
        if (fixture == null || storageFixture == null || activeStorageId == null) {
            contexts.clear();
            productions.clear();
            createdCategoryIds.clear();
            return;
        }
        if (favouritesChanged) {
            try { fixture.restoreFavouriteResources(UserRole.OWNER_1, activeStorageId, previousFavourites); }
            catch (Exception e) { log.warn("Favourite cleanup: {}", e.getMessage()); }
        }
        favouritesChanged = false;
        previousFavourites = null;
        for (ManufacturingItemResponse production : productions.reversed()) {
            try { fixture.cleanupProduction(production, activeStorageId); } catch (Exception e) { log.warn("Production cleanup: {}", e.getMessage()); }
        }
        productions.clear();
        if (currentPlan != null) {
            try { fixture.cleanupPlan(currentPlan); } catch (Exception e) { log.warn("Plan cleanup: {}", e.getMessage()); }
            currentPlan = null;
        }
        for (var context : contexts.reversed()) {
            try { fixture.cleanupTechMap(context.getTechMap(), activeStorageId); } catch (Exception e) { log.warn("Tech map cleanup: {}", e.getMessage()); }
            try { inventoryFixture.resetResourceStock(activeStorageId, context.getProduct().getId(), 0, UserRole.ADMIN); }
            catch (Exception e) { log.warn("Stock cleanup: {}", e.getMessage()); }
            try { resourceFixture.deactivate(UserRole.ADMIN, context.getProduct().getId()); }
            catch (Exception e) { log.warn("Resource cleanup: {}", e.getMessage()); }
        }
        contexts.clear();
        for (Long categoryId : createdCategoryIds.reversed()) {
            try { apiExecutor.execute(ApiEndpointDefinition.RESOURCE_CATEGORY_DELETE, UserRole.ADMIN, null, categoryId); }
            catch (Exception e) { log.warn("Category cleanup: {}", e.getMessage()); }
        }
        createdCategoryIds.clear();
        if (!activeStorageId.equals(storageId)) {
            try { inventoryFixture.clearStock(activeStorageId); } catch (Exception e) { log.warn("Location stock cleanup: {}", e.getMessage()); }
        }
        storageFixture.deactivateTrackedStorages(UserRole.ADMIN);
        activeStorageId = storageId;
    }

    @Test(priority = 10)
    @TestCaseId({"TC-UI-PLANEXEC-014", "TC-UI-PLANEXEC-021"})
    @Story("Category multiselect combines with product search and favourites")
    public void categoryMultiselectCombinesWithOtherFilters() {
        List<ResourceCategoryResponse> categories = categories();
        assertThat(categories).hasSizeGreaterThanOrEqualTo(2);
        MeasurementUnitResponse unit = units().getFirst();
        var first = product(unit.getId(), categories.get(0).getId(), 2);
        var second = product(unit.getId(), categories.get(1).getId(), 3);

        previousFavourites = fixture.snapshotFavouriteResourceIds(UserRole.OWNER_1, activeStorageId);
        favouritesChanged = true;
        fixture.saveFavouriteResources(UserRole.OWNER_1, activeStorageId, List.of(first.getProduct().getId()));

        injectOwnerSession();
        PlanExecutionPage pageObject = new PlanExecutionPage(page).open().expandOutOfPlanSection();
        pageObject.selectExecutionCategories(categories.get(0).getName(), categories.get(1).getName())
                .expandOutOfPlanSection();
        assertThat(pageObject.isOutOfPlanProductRowVisible(first.getProduct().getName())).isTrue();
        assertThat(pageObject.isOutOfPlanProductRowVisible(second.getProduct().getName())).isTrue();

        pageObject.searchExecutionProduct(first.getProduct().getName());
        assertThat(pageObject.isOutOfPlanProductRowVisible(first.getProduct().getName())).isTrue();
        assertThat(pageObject.isOutOfPlanProductRowVisible(second.getProduct().getName())).isFalse();
    }

    @Test(priority = 20)
    @TestCaseId({"TC-UI-PLANEXEC-015", "TC-UI-PLANEXEC-016"})
    @Story("Excel contains exactly the visible rows, including collapsed out-of-plan rows")
    public void exportUsesCurrentVisibleRows() {
        ResourceCategoryResponse category = categories().getFirst();
        MeasurementUnitResponse unit = units().getFirst();
        var visible = product(unit.getId(), category.getId(), 2);
        var hiddenBySearch = product(unit.getId(), category.getId(), 3);

        injectOwnerSession();
        PlanExecutionPage pageObject = new PlanExecutionPage(page).open();
        // Out-of-plan remains collapsed: these rows still belong to the export scope.
        pageObject.searchExecutionProduct(visible.getProduct().getName());
        var download = pageObject.clickExportToExcelAndDownload();
        assertThat(download.sizeBytes()).isGreaterThan(0);
        Map<String, List<List<String>>> workbook = XlsxWorkbookReader.sheets(read(download.path()));
        log.info("Plan execution XLSX structure: {}", workbook.entrySet().stream()
                .map(e -> e.getKey() + "=" + (e.getValue().isEmpty() ? List.of() : e.getValue().getFirst()))
                .toList());
        List<String> cells = workbook.values().stream().flatMap(List::stream).flatMap(List::stream).toList();
        assertThat(workbook.get("За планом").getFirst()).containsExactly(
                "Продукт", "Категорія", "Ціль", "Од. вимір", "Зроблено",
                "На складі (" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")) + ")");
        assertThat(workbook.get("Поза планом").getFirst()).containsExactly(
                "Продукт", "Категорія", "Од. виміру", "Зроблено",
                "На складі (" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")) + ")");
        assertThat(cells).contains(visible.getProduct().getName());
        assertThat(cells).doesNotContain(hiddenBySearch.getProduct().getName());
    }

    @Test(priority = 30)
    @TestCaseId({"TC-UI-PLANEXEC-017", "TC-UI-PLANEXEC-018"})
    @Story("Manage selected resources bulk actions respect current filtering and archived state")
    public void manageSelectedResourcesBulkActionsRespectFilters() {
        var context = product(units().getFirst().getId(), categories().getFirst().getId(), 1);
        previousFavourites = fixture.snapshotFavouriteResourceIds(UserRole.OWNER_1, activeStorageId);
        favouritesChanged = true;
        fixture.saveFavouriteResources(UserRole.OWNER_1, activeStorageId, List.of());

        injectOwnerSession();
        PlanExecutionPage pageObject = new PlanExecutionPage(page).open().openManageFavouritesDialog();
        pageObject.filterManageDialogByName(context.getProduct().getName())
                .clickManageDialogBulkAction("Обрати все");
        assertThat(pageObject.getManageDialogSaveButtonText()).isEqualTo("Зберегти (1)");
        pageObject.clickManageDialogBulkAction("Зняти все");
        assertThat(pageObject.getManageDialogSaveButtonText()).isEqualTo("Зберегти (0)");

        // Active is the default and the explicit Archived switch must be operable.
        pageObject.selectManageDialogResourceState("Архівні");
        assertThat(pageObject.isProductListedInManageDialog(context.getProduct().getName())).isFalse();
    }

    @Test(priority = 40)
    @TestCaseId({"TC-UI-PLANEXEC-019", "TC-UI-PLANEXEC-020"})
    @Story("Out-of-plan total sums only pcs/kits among visible rows")
    public void outOfPlanTotalUsesOnlyPcsAndKits() {
        List<MeasurementUnitResponse> units = units();
        MeasurementUnitResponse countable = units.stream()
                .filter(u -> "шт".equalsIgnoreCase(u.getShortName()) || "комп".equalsIgnoreCase(u.getShortName()))
                .findFirst().orElseThrow();
        MeasurementUnitResponse other = units.stream()
                .filter(u -> !u.getId().equals(countable.getId()))
                .findFirst().orElseThrow();
        ResourceCategoryResponse category = categories().getFirst();
        product(countable.getId(), category.getId(), 2);
        product(other.getId(), category.getId(), 9);

        injectOwnerSession();
        PlanExecutionPage pageObject = new PlanExecutionPage(page).open().expandOutOfPlanSection();
        assertThat(pageObject.getOutOfPlanFooterText())
                .contains("Разом", "2")
                .doesNotContain("11");
    }

    @Test(priority = 50)
    @TestCaseId("TC-UI-PLANEXEC-022")
    @Story("Selecting a parent category includes descendants on the page and in the favourites popup")
    public void parentCategoryIncludesDescendants() {
        String suffix = String.valueOf(System.currentTimeMillis());
        ResourceCategoryResponse parent = createCategory("PE-parent-" + suffix, null);
        ResourceCategoryResponse child = createCategory("PE-child-" + suffix, parent.getId());
        var context = product(units().getFirst().getId(), child.getId(), 2);

        injectOwnerSession();
        PlanExecutionPage pageObject = new PlanExecutionPage(page).open()
                .selectExecutionCategories(parent.getName())
                .expandOutOfPlanSection();
        assertThat(pageObject.isOutOfPlanProductRowVisible(context.getProduct().getName()))
                .as("Parent category must include a product from its child category")
                .isTrue();

        pageObject.openManageFavouritesDialog()
                .selectManageDialogCategory(parent.getName())
                .filterManageDialogByName(context.getProduct().getName());
        assertThat(pageObject.isProductListedInManageDialog(context.getProduct().getName())).isTrue();
        pageObject.clickManageDialogBulkAction("Обрати все");
        assertThat(pageObject.getManageDialogSaveButtonText()).isEqualTo("Зберегти (1)");
    }

    @Test(priority = 60)
    @TestCaseId("TC-UI-PLANEXEC-024")
    @Story("Excel export error shows feedback, creates no file and allows retry")
    public void exportErrorDoesNotCreateFileAndAllowsRetry() {
        product(units().getFirst().getId(), categories().getFirst().getId(), 1);
        injectOwnerSession();
        PlanExecutionPage pageObject = new PlanExecutionPage(page).open();
        page.route("**/api/v1/statistics/execution-export**", route -> route.fulfill(
                new Route.FulfillOptions().setStatus(500)
                        .setContentType("application/json")
                        .setBody("{\"message\":\"forced export failure\"}")));

        assertThat(pageObject.clickExportAndWaitForErrorWithoutDownload()).isTrue();
        assertThat(pageObject.isExportButtonEnabled()).isTrue();
    }

    @Test(priority = 70)
    @TestCaseId({"TC-UI-PLANEXEC-023", "TC-UI-PLANEXEC-025", "TC-UI-PLANEXEC-026",
            "TC-UI-PLANEXEC-027", "TC-UI-PLANEXEC-028"})
    @Story("Archive/unarchive preserves planned and out-of-plan history, latest metadata and unit buckets")
    public void archiveUnarchivePreservesExecutionHistoryAndMetadata() {
        StorageResponse isolated = storageFixture.createProductionStorage(storageId, "plan-exec-archive-");
        activeStorageId = isolated.getId();
        List<MeasurementUnitResponse> units = units();
        assertThat(units).hasSizeGreaterThanOrEqualTo(2);
        List<ResourceCategoryResponse> categories = categories();
        assertThat(categories).hasSizeGreaterThanOrEqualTo(2);

        var planned = product(units.get(0).getId(), categories.get(0).getId(), 2);
        String latestName = planned.getProduct().getName() + "-latest";
        ResourceResponse changed = resourceFixture.update(UserRole.ADMIN, planned.getProduct().getId(),
                ResourceRequest.builder()
                        .name(latestName)
                        .measurementUnitId(units.get(1).getId())
                        .categoryId(categories.get(1).getId())
                        .build());
        assertThat(changed.getUnit().getId()).isEqualTo(units.get(1).getId());
        productions.add(fixture.createCurrentMonthProduction(activeStorageId, planned.getTechMap(), 3));
        currentPlan = fixture.createCurrentMonthPlan(activeStorageId, planned.getProduct().getId(), 10);

        var outside = product(units.get(0).getId(), categories.get(1).getId(), 4);
        TechnologicalMapFixture techMaps = new TechnologicalMapFixture(testContext, apiExecutor);
        assertThat(techMaps.deactivateTechMap(UserRole.ADMIN, planned.getTechMap().getId(), activeStorageId).statusCode())
                .isBetween(200, 299);
        assertThat(techMaps.deactivateTechMap(UserRole.ADMIN, outside.getTechMap().getId(), activeStorageId).statusCode())
                .isBetween(200, 299);
        inventoryFixture.resetResourceStock(activeStorageId, planned.getProduct().getId(), 0, UserRole.ADMIN);
        inventoryFixture.resetResourceStock(activeStorageId, outside.getProduct().getId(), 0, UserRole.ADMIN);
        assertThat(resourceFixture.deactivate(UserRole.ADMIN, planned.getProduct().getId()).statusCode())
                .isBetween(200, 299);
        assertThat(resourceFixture.deactivate(UserRole.ADMIN, outside.getProduct().getId()).statusCode())
                .isBetween(200, 299);

        injectOwnerSession();
        PlanExecutionPage pageObject = new PlanExecutionPage(page).open().expandOutOfPlanSection();
        assertThat(pageObject.isProductRowVisible(latestName)).isTrue();
        assertThat(pageObject.getProducedCellText(latestName)).contains("3");
        assertThat(pageObject.isOutOfPlanProductRowVisible(latestName)).isTrue();
        assertThat(pageObject.getOutOfPlanProducedCellText(latestName)).contains("2");
        assertThat(pageObject.isOutOfPlanProductRowVisible(outside.getProduct().getName())).isTrue();
        assertThat(pageObject.getOutOfPlanProducedCellText(outside.getProduct().getName())).contains("4");

        var export = pageObject.clickExportToExcelAndDownload();
        Map<String, List<List<String>>> workbook = XlsxWorkbookReader.sheets(read(export.path()));
        log.info("Archived execution XLSX structure: {}", workbook.entrySet().stream()
                .map(e -> e.getKey() + "=" + (e.getValue().isEmpty() ? List.of() : e.getValue().getFirst()))
                .toList());
        List<String> cells = workbook.values().stream().flatMap(List::stream).flatMap(List::stream).toList();
        assertThat(cells).contains(latestName, categories.get(1).getName(), units.get(0).getShortName(), units.get(1).getShortName());
        assertThat(cells).doesNotContain(planned.getProduct().getName());

        pageObject.openManageFavouritesDialog()
                .filterManageDialogByNameAllowEmpty(latestName);
        assertThat(pageObject.isProductListedInManageDialog(latestName))
                .as("Active catalog must not return archived resources").isFalse();
        pageObject.selectManageDialogResourceState("Архівні")
                .filterManageDialogByName(latestName);
        assertThat(pageObject.isProductListedInManageDialog(latestName))
                .as("Archived catalog must return an explicitly searched archived resource").isTrue();
        pageObject.cancelManageFavouritesDialog();

        assertThat(resourceFixture.unarchive(UserRole.ADMIN, planned.getProduct().getId()).statusCode())
                .isBetween(200, 299);
        assertThat(resourceFixture.unarchive(UserRole.ADMIN, outside.getProduct().getId()).statusCode())
                .isBetween(200, 299);
        pageObject = new PlanExecutionPage(page).open().expandOutOfPlanSection();
        assertThat(pageObject.getProducedCellText(latestName)).contains("3");
        assertThat(pageObject.getOutOfPlanProducedCellText(latestName)).contains("2");
        assertThat(pageObject.getOutOfPlanProducedCellText(outside.getProduct().getName())).contains("4");
    }

    private TechnologicalMapFixture.IsolatedTechMapContext product(Long unitId, Long categoryId, double amount) {
        var context = fixture.createIsolatedProduct(activeStorageId, unitId, categoryId);
        contexts.add(context);
        productions.add(fixture.createCurrentMonthProduction(activeStorageId, context.getTechMap(), amount));
        return context;
    }

    private ResourceCategoryResponse createCategory(String name, Long parentId) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.RESOURCE_CATEGORY_CREATE, UserRole.ADMIN,
                ResourceCategoryRequest.builder().name(name).parentId(parentId).build());
        assertThat(response.statusCode()).isBetween(200, 299);
        ResourceCategoryResponse created = response.as(ResourceCategoryResponse.class);
        createdCategoryIds.add(created.getId());
        return created;
    }

    private List<ResourceCategoryResponse> categories() {
        Response response = apiExecutor.execute(ApiEndpointDefinition.RESOURCE_CATEGORY_GET_ALL, UserRole.ADMIN);
        return DatabaseIntegrityValidator.extractList(response, ResourceCategoryResponse.class);
    }

    private List<MeasurementUnitResponse> units() {
        Response response = apiExecutor.execute(ApiEndpointDefinition.MEASUREMENT_UNIT_GET_ALL, UserRole.ADMIN);
        return DatabaseIntegrityValidator.extractList(response, MeasurementUnitResponse.class);
    }

    private void injectOwnerSession() {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.OWNER_1.getUsername(), UserRole.OWNER_1.getPassword());
        injectSessionCookies(cookies, sessionCookieDomain());
        browserContext.addInitScript("localStorage.setItem('selectedStorageId', '" + activeStorageId + "');");
    }

    private static byte[] read(java.nio.file.Path path) {
        try { return java.nio.file.Files.readAllBytes(path); }
        catch (java.io.IOException e) { throw new IllegalStateException("Cannot read exported XLSX", e); }
    }
}
