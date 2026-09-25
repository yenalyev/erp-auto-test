package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.BusinessRole;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.PlanExecutionFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.request.ResourceCategoryRequest;
import com.erp.models.request.ResourceRequest;
import com.erp.models.request.ResourceUsageRequest;
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
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Plans")
@Feature("Plan Execution improvement UI")
public class PlanExecutionImprovementUiTest extends BaseUITest {
    private static final UserRole ACTOR_SESSION = UserRole.ORDER_PRODUCTION_WORKER;
    private PlanExecutionFixture fixture;
    private ResourceFixture resourceFixture;
    private InventoryFixture inventoryFixture;
    private StorageFixture storageFixture;
    private UserFixture userFixture;
    private UserFixture.BusinessActor activeActor;
    private Long storageId;
    private Long activeStorageId;
    private final List<TechnologicalMapFixture.IsolatedTechMapContext> contexts = new ArrayList<>();
    private final List<ManufacturingItemResponse> productions = new ArrayList<>();
    private List<Long> previousFavourites;
    private boolean favouritesChanged;
    private PlanResponse currentPlan;
    private final List<Long> createdCategoryIds = new ArrayList<>();
    private final List<Long> auxiliaryResourceIds = new ArrayList<>();
    private final List<Long> auxiliaryTechMapIds = new ArrayList<>();

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        fixture = new PlanExecutionFixture(testContext, apiExecutor);
        fixture.prepareContext();
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        storageFixture = new StorageFixture(testContext, apiExecutor);
        userFixture = new UserFixture(testContext, apiExecutor);
        storageId = ConfigProvider.getOwner1StorageId();
        activeStorageId = storageId;
        useDynamicActor(storageFixture.getById(UserRole.ADMIN, storageId));
    }

    @AfterClass(alwaysRun = true)
    public void cleanupDynamicUsers() {
        if (apiExecutor != null) {
            apiExecutor.evictSessionForRole(ACTOR_SESSION);
        }
        if (userFixture != null) {
            userFixture.deactivateTrackedUsers();
        }
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupImprovementData() {
        if (fixture == null || storageFixture == null || activeStorageId == null) {
            contexts.clear();
            productions.clear();
            createdCategoryIds.clear();
            auxiliaryResourceIds.clear();
            auxiliaryTechMapIds.clear();
            return;
        }
        if (favouritesChanged) {
            try { fixture.restoreFavouriteResources(ACTOR_SESSION, activeStorageId, previousFavourites); }
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
            try { inventoryFixture.removeResourceFromStorage(activeStorageId, context.getProduct().getId(), UserRole.ADMIN); }
            catch (Exception e) { log.warn("Stock cleanup: {}", e.getMessage()); }
            try { resourceFixture.deactivate(UserRole.ADMIN, context.getProduct().getId()); }
            catch (Exception e) { log.warn("Resource cleanup: {}", e.getMessage()); }
        }
        contexts.clear();
        TechnologicalMapFixture techMaps = new TechnologicalMapFixture(testContext, apiExecutor);
        for (Long techMapId : auxiliaryTechMapIds.reversed()) {
            try { techMaps.deactivateTechMap(UserRole.ADMIN, techMapId, activeStorageId); }
            catch (Exception e) { log.warn("Auxiliary tech map cleanup: {}", e.getMessage()); }
        }
        auxiliaryTechMapIds.clear();
        for (Long resourceId : auxiliaryResourceIds.reversed()) {
            try { inventoryFixture.removeResourceFromStorage(activeStorageId, resourceId, UserRole.ADMIN); }
            catch (Exception e) { log.warn("Auxiliary stock cleanup: {}", e.getMessage()); }
            try { resourceFixture.deactivate(UserRole.ADMIN, resourceId); }
            catch (Exception e) { log.warn("Auxiliary resource cleanup: {}", e.getMessage()); }
        }
        auxiliaryResourceIds.clear();
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

        previousFavourites = fixture.snapshotFavouriteResourceIds(ACTOR_SESSION, activeStorageId);
        favouritesChanged = true;
        fixture.saveFavouriteResources(ACTOR_SESSION, activeStorageId, List.of(first.getProduct().getId()));

        injectDynamicActorSession();
        PlanExecutionPage pageObject = new PlanExecutionPage(page).open().expandOutOfPlanSection();
        pageObject.selectExecutionCategories(categories.get(0).getName(), categories.get(1).getName())
                .expandOutOfPlanSection();
        assertThat(pageObject.isOutOfPlanProductRowVisible(first.getProduct().getName())).isTrue();
        assertThat(pageObject.isOutOfPlanProductRowVisible(second.getProduct().getName())).isTrue();

        pageObject.searchExecutionProduct(first.getProduct().getName());
        pageObject.waitForOutOfPlanProductVisibility(second.getProduct().getName(), false);
        assertThat(pageObject.isOutOfPlanProductRowVisible(first.getProduct().getName())).isTrue();
        assertThat(pageObject.isOutOfPlanProductRowVisible(second.getProduct().getName())).isFalse();
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-PLANEXEC-015")
    @Story("Excel has the required three-sheet structure and headers")
    public void exportHasRequiredWorkbookStructure() {
        ResourceCategoryResponse category = categories().getFirst();
        MeasurementUnitResponse unit = units().getFirst();
        product(unit.getId(), category.getId(), 2);

        injectDynamicActorSession();
        PlanExecutionPage pageObject = new PlanExecutionPage(page).open();
        var download = pageObject.clickExportToExcelAndDownload();
        assertThat(download.sizeBytes()).isGreaterThan(0);
        Map<String, List<List<String>>> workbook = XlsxWorkbookReader.sheets(read(download.path()));
        log.info("Plan execution XLSX structure: {}", workbook.entrySet().stream()
                .map(e -> e.getKey() + "=" + (e.getValue().isEmpty() ? List.of() : e.getValue().getFirst()))
                .toList());
        assertThat(workbook.get("За планом").getFirst()).containsExactly(
                "Продукт", "Категорія", "Ціль", "Од. вимір", "Зроблено",
                "На складі (" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")) + ")");
        assertThat(workbook.get("Поза планом").getFirst()).containsExactly(
                "Продукт", "Категорія", "Од. виміру", "Зроблено",
                "На складі (" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")) + ")");
        assertThat(workbook.get("Розбір").getFirst()).containsExactly("Продукт", "Кількість");
    }

    @Test(priority = 21)
    @TestCaseId("TC-UI-PLANEXEC-016")
    @Story("Excel contains only currently visible rows, including collapsed out-of-plan rows")
    public void exportUsesCurrentVisibleRows() {
        List<ResourceCategoryResponse> categories = categories();
        assertThat(categories).hasSizeGreaterThanOrEqualTo(2);
        MeasurementUnitResponse unit = units().getFirst();
        var visible = product(unit.getId(), categories.get(0).getId(), 2);
        var hiddenBySearch = product(unit.getId(), categories.get(0).getId(), 3);
        var hiddenByCategory = product(unit.getId(), categories.get(1).getId(), 4);

        previousFavourites = fixture.snapshotFavouriteResourceIds(ACTOR_SESSION, activeStorageId);
        favouritesChanged = true;
        fixture.saveFavouriteResources(ACTOR_SESSION, activeStorageId, List.of(visible.getProduct().getId()));

        injectDynamicActorSession();
        PlanExecutionPage pageObject = new PlanExecutionPage(page).open()
                .selectExecutionCategories(categories.get(0).getName());
        pageObject.searchExecutionProduct(visible.getProduct().getName());
        pageObject.clickFavouritesOnly();
        // Out-of-plan remains collapsed: collapse is presentation state, not an export filter.
        var download = pageObject.clickExportToExcelAndDownload();
        assertThat(download.sizeBytes()).isGreaterThan(0);
        Map<String, List<List<String>>> workbook = XlsxWorkbookReader.sheets(read(download.path()));
        List<String> cells = workbook.values().stream().flatMap(List::stream).flatMap(List::stream).toList();
        assertThat(cells).contains(visible.getProduct().getName());
        assertThat(cells).doesNotContain(
                hiddenBySearch.getProduct().getName(),
                hiddenByCategory.getProduct().getName());
    }

    @Test(priority = 30)
    @TestCaseId({"TC-UI-PLANEXEC-017", "TC-UI-PLANEXEC-018"})
    @Story("Manage selected resources bulk actions respect current filtering and archived state")
    public void manageSelectedResourcesBulkActionsRespectFilters() {
        var context = product(units().getFirst().getId(), categories().getFirst().getId(), 1);
        previousFavourites = fixture.snapshotFavouriteResourceIds(ACTOR_SESSION, activeStorageId);
        favouritesChanged = true;
        fixture.saveFavouriteResources(ACTOR_SESSION, activeStorageId, List.of());

        injectDynamicActorSession();
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

        injectDynamicActorSession();
        PlanExecutionPage pageObject = new PlanExecutionPage(page).open().expandOutOfPlanSection();
        assertThat(pageObject.getOutOfPlanFooterText())
                .contains("Разом", "2")
                .doesNotContain("11");
    }

    @Test(priority = 50)
    @TestCaseId("TC-UI-PLANEXEC-022")
    @Story("Execution-page category filtering uses exact selected category IDs")
    public void parentCategoryUsesExactMatchOnExecutionPage() {
        String suffix = String.valueOf(System.currentTimeMillis());
        ResourceCategoryResponse parent = createCategory("PE-parent-" + suffix, null);
        ResourceCategoryResponse child = createCategory("PE-child-" + suffix, parent.getId());
        var context = product(units().getFirst().getId(), child.getId(), 2);

        injectDynamicActorSession();
        PlanExecutionPage pageObject = new PlanExecutionPage(page).open()
                .selectExecutionCategories(parent.getName());
        assertThat(pageObject.isOutOfPlanSectionVisible())
                .as("The execution-page parent filter must not implicitly include child categories")
                .isFalse();
        assertThat(pageObject.isOutOfPlanProductRowVisible(context.getProduct().getName()))
                .as("A child-category product must not match an exact parent-category filter")
                .isFalse();
    }

    @Test(priority = 60)
    @TestCaseId("TC-UI-PLANEXEC-024")
    @Story("Excel export error shows feedback, creates no file and allows retry")
    public void exportErrorDoesNotCreateFileAndAllowsRetry() {
        product(units().getFirst().getId(), categories().getFirst().getId(), 1);
        injectDynamicActorSession();
        PlanExecutionPage pageObject = new PlanExecutionPage(page).open();
        page.route("**/api/v1/statistics/execution-export**", route -> route.fulfill(
                new Route.FulfillOptions().setStatus(500)
                        .setContentType("application/json")
                        .setBody("{\"message\":\"forced export failure\"}")));

        assertThat(pageObject.clickExportAndWaitForErrorWithoutDownload()).isTrue();
        assertThat(pageObject.isExportButtonEnabled()).isTrue();
    }

    @Test(priority = 65)
    @TestCaseId({"TC-UI-PLANEXEC-023", "TC-UI-PLANEXEC-029"})
    @Story("Name search respects Active and Archived resource states in the manage-favourites popup")
    public void nameSearchSeparatesActiveAndArchivedResources() {
        ResourceCategoryResponse category = categories().getFirst();
        Long unitId = units().getFirst().getId();
        var archived = fixture.createIsolatedProduct(activeStorageId, unitId, category.getId());
        var active = fixture.createIsolatedProduct(activeStorageId, unitId, category.getId());
        contexts.addAll(List.of(archived, active));

        TechnologicalMapFixture techMaps = new TechnologicalMapFixture(testContext, apiExecutor);
        assertThat(techMaps.deactivateTechMap(
                UserRole.ADMIN, archived.getTechMap().getId(), activeStorageId).statusCode())
                .isBetween(200, 299);
        assertThat(resourceFixture.deactivate(UserRole.ADMIN, archived.getProduct().getId()).statusCode())
                .isBetween(200, 299);
        bindArchivedOutputToNewActiveTechMap(techMaps, archived);

        injectDynamicActorSession();
        PlanExecutionPage pageObject = new PlanExecutionPage(page).open().openManageFavouritesDialog();

        String activeUrl = pageObject.filterManageDialogByNameAndCaptureRequestUrl(
                active.getProduct().getName());
        assertThat(activeUrl).contains("isActive=true", "name=" + active.getProduct().getName());
        assertThat(pageObject.isProductListedInManageDialog(active.getProduct().getName())).isTrue();
        String archivedInActiveUrl = pageObject.filterManageDialogByNameAndCaptureRequestUrl(
                archived.getProduct().getName());
        assertThat(archivedInActiveUrl).contains("isActive=true", "name=" + archived.getProduct().getName());
        assertThat(pageObject.isProductListedInManageDialog(archived.getProduct().getName())).isFalse();

        pageObject.filterManageDialogByNameAndCaptureRequestUrl("");
        pageObject.selectManageDialogResourceState("Архівні");
        String archivedUrl = pageObject.filterManageDialogByNameAndCaptureRequestUrl(
                archived.getProduct().getName());
        assertThat(archivedUrl).contains("isActive=false", "name=" + archived.getProduct().getName());
        pageObject.waitForManageDialogProduct(archived.getProduct().getName());
        assertThat(pageObject.isProductListedInManageDialog(archived.getProduct().getName()))
                .as("Archived catalog must return the resource for an exact name search")
                .isTrue();
        String activeInArchivedUrl = pageObject.filterManageDialogByNameAndCaptureRequestUrl(
                active.getProduct().getName());
        assertThat(activeInArchivedUrl).contains("isActive=false", "name=" + active.getProduct().getName());
        assertThat(pageObject.isProductListedInManageDialog(active.getProduct().getName())).isFalse();
        pageObject.cancelManageFavouritesDialog();
    }

    @Test(priority = 70)
    @TestCaseId({"TC-UI-PLANEXEC-025", "TC-UI-PLANEXEC-026", "TC-UI-PLANEXEC-027", "TC-UI-PLANEXEC-028"})
    @Story("Archive/unarchive preserves history and aggregates unit changes using the latest resource metadata")
    public void archiveUnarchivePreservesExecutionHistoryAndMetadata() {
        StorageResponse isolated = storageFixture.createProductionStorage(storageId, "plan-exec-archive-");
        activeStorageId = isolated.getId();
        useDynamicActor(isolated);
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

        var outside = product(units.get(0).getId(), categories.get(1).getId(), 4);
        TechnologicalMapFixture techMaps = new TechnologicalMapFixture(testContext, apiExecutor);

        ResourceResponse plannedReplacement = detachOutputResourceFromTechMap(techMaps, planned, "archive-planned-replacement-");
        ResourceResponse outsideReplacement = detachOutputResourceFromTechMap(techMaps, outside, "archive-outside-replacement-");
        removeResourceCompletelyFromInventory(planned.getProduct().getId());
        removeResourceCompletelyFromInventory(outside.getProduct().getId());

        assertThat(techMaps.deactivateTechMap(UserRole.ADMIN, planned.getTechMap().getId(), activeStorageId).statusCode())
                .isBetween(200, 299);
        assertThat(techMaps.deactivateTechMap(UserRole.ADMIN, outside.getTechMap().getId(), activeStorageId).statusCode())
                .isBetween(200, 299);
        try {
            assertThat(resourceFixture.deactivate(UserRole.ADMIN, planned.getProduct().getId()).statusCode())
                    .as("ADMIN must archive a production-history resource removed from tech maps and inventory, including zero rows")
                    .isBetween(200, 299);
            assertThat(resourceFixture.deactivate(UserRole.ADMIN, outside.getProduct().getId()).statusCode())
                    .isBetween(200, 299);
            currentPlan = fixture.createCurrentMonthPlan(activeStorageId, planned.getProduct().getId(), 10);

            injectDynamicActorSession();
            PlanExecutionPage pageObject = new PlanExecutionPage(page).open().expandOutOfPlanSection();
            assertThat(pageObject.isProductRowVisible(latestName)).isTrue();
            assertThat(pageObject.getProducedCellText(latestName)).contains("5");
            assertThat(pageObject.isOutOfPlanProductRowVisible(latestName)).isFalse();
            assertThat(pageObject.isOutOfPlanProductRowVisible(outside.getProduct().getName())).isTrue();
            assertThat(pageObject.getOutOfPlanProducedCellText(outside.getProduct().getName())).contains("4");

            var export = pageObject.clickExportToExcelAndDownload();
            Map<String, List<List<String>>> workbook = XlsxWorkbookReader.sheets(read(export.path()));
            log.info("Archived execution XLSX structure: {}", workbook.entrySet().stream()
                    .map(e -> e.getKey() + "=" + (e.getValue().isEmpty() ? List.of() : e.getValue().getFirst()))
                    .toList());
            List<String> cells = workbook.values().stream().flatMap(List::stream).flatMap(List::stream).toList();
            List<String> plannedRow = workbook.get("За планом").stream()
                    .filter(row -> row.contains(latestName))
                    .findFirst().orElseThrow();
            assertThat(cells).contains(latestName, categories.get(1).getName(), units.get(1).getShortName());
            assertThat(plannedRow).contains(units.get(1).getShortName()).doesNotContain(units.get(0).getShortName());
            assertThat(cells).doesNotContain(planned.getProduct().getName());
            assertThat(cells).doesNotContain(plannedReplacement.getName(), outsideReplacement.getName());

            assertThat(resourceFixture.unarchive(UserRole.ADMIN, planned.getProduct().getId()).statusCode())
                    .isBetween(200, 299);
            assertThat(resourceFixture.unarchive(UserRole.ADMIN, outside.getProduct().getId()).statusCode())
                    .isBetween(200, 299);
            pageObject = new PlanExecutionPage(page).open().expandOutOfPlanSection();
            assertThat(pageObject.getProducedCellText(latestName)).contains("5");
            assertThat(pageObject.isOutOfPlanProductRowVisible(latestName)).isFalse();
            assertThat(pageObject.getOutOfPlanProducedCellText(outside.getProduct().getName())).contains("4");
        } finally {
            restoreResourceForProductionCleanup(planned.getProduct().getId(), 5.0);
            restoreResourceForProductionCleanup(outside.getProduct().getId(), 4.0);
        }
    }

    private TechnologicalMapFixture.IsolatedTechMapContext product(Long unitId, Long categoryId, double amount) {
        var context = fixture.createIsolatedProduct(activeStorageId, unitId, categoryId);
        contexts.add(context);
        productions.add(fixture.createCurrentMonthProduction(activeStorageId, context.getTechMap(), amount));
        return context;
    }

    private ResourceResponse detachOutputResourceFromTechMap(
            TechnologicalMapFixture techMaps,
            TechnologicalMapFixture.IsolatedTechMapContext context,
            String replacementPrefix) {
        ResourceResponse original = resourceFixture.getById(UserRole.ADMIN, context.getProduct().getId());
        ResourceResponse replacement = resourceFixture.createUniqueResource(
                replacementPrefix,
                original.getUnit().getId(),
                original.getCategory().getId());
        auxiliaryResourceIds.add(replacement.getId());

        var request = TechnologicalMapDataFactory.fromExisting(context.getTechMap())
                .output(List.of(new ResourceUsageRequest(replacement.getId(), 1.0)))
                .build();
        Response response = techMaps.updateTechMap(UserRole.ADMIN, context.getTechMap().getId(), request);
        assertThat(response.statusCode())
                .as("ADMIN must replace output resource %s in tech map %s before resource archive",
                        original.getId(), context.getTechMap().getId())
                .isBetween(200, 299);
        return replacement;
    }

    /**
     * The favourites catalog contains only resources that are outputs of an active production
     * tech map. After archiving the resource, bind it to a new active map so the Archived selector
     * has a valid catalog entry (the same business fixture as ResourceWithTechnologicalMapApiTest).
     */
    private void bindArchivedOutputToNewActiveTechMap(
            TechnologicalMapFixture techMaps,
            TechnologicalMapFixture.IsolatedTechMapContext archived) {
        String suffix = String.valueOf(System.currentTimeMillis());
        ResourceResponse in1 = resourceFixture.createUniqueResource("PE-ARCH-IN1-" + suffix);
        ResourceResponse in2 = resourceFixture.createUniqueResource("PE-ARCH-IN2-" + suffix);
        auxiliaryResourceIds.add(in1.getId());
        auxiliaryResourceIds.add(in2.getId());

        var request = TechnologicalMapDataFactory.createProductionMapWithStorages(
                "PE-ArchivedOutput-" + suffix,
                List.of(
                        new ResourceUsageRequest(in1.getId(), 2.0),
                        new ResourceUsageRequest(in2.getId(), 1.0)),
                List.of(new ResourceUsageRequest(archived.getProduct().getId(), 1.0)),
                Set.of(activeStorageId)).build();
        var rebound = techMaps.createTechMapWithRequest(UserRole.ADMIN, request);
        auxiliaryTechMapIds.add(rebound.getId());
    }

    private void removeResourceCompletelyFromInventory(Long resourceId) {
        inventoryFixture.removeResourceFromStorage(activeStorageId, resourceId, UserRole.ADMIN);
        boolean stillPresentIncludingZero = inventoryFixture.listItems(
                        activeStorageId,
                        UserRole.ADMIN,
                        Map.of("showZeroStock", true, "size", 1000, "page", 0))
                .stream()
                .anyMatch(item -> item.getResource() != null
                        && resourceId.equals(item.getResource().getId()));
        assertThat(stillPresentIncludingZero)
                .as("Inventory must not retain resource %s even as a zero-amount row", resourceId)
                .isFalse();
    }

    private void restoreResourceForProductionCleanup(Long resourceId, double producedAmount) {
        try {
            ResourceResponse current = resourceFixture.getById(UserRole.ADMIN, resourceId);
            if (Boolean.FALSE.equals(current.getActive())) {
                Response response = resourceFixture.unarchive(UserRole.ADMIN, resourceId);
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    log.warn("Resource {} unarchive for cleanup returned HTTP {}", resourceId, response.statusCode());
                }
            }
        } catch (Exception e) {
            log.warn("Resource {} unarchive for cleanup failed: {}", resourceId, e.getMessage());
        }
        try {
            inventoryFixture.resetResourceStock(activeStorageId, resourceId, producedAmount, UserRole.ADMIN);
        } catch (Exception e) {
            log.warn("Resource {} stock restore for production cleanup failed: {}", resourceId, e.getMessage());
        }
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

    private void injectDynamicActorSession() {
        if (activeActor == null) {
            throw new IllegalStateException("Dynamic plan-execution actor was not created");
        }
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(activeActor.username(), activeActor.password());
        injectSessionCookies(cookies, sessionCookieDomain());
        browserContext.addInitScript("localStorage.setItem('selectedStorageId', '" + activeStorageId + "');");
    }

    private void useDynamicActor(StorageResponse storage) {
        activeActor = userFixture.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.BUSINESS_UNIT_OWNER, List.of(storage));
        apiExecutor.setSessionForRole(ACTOR_SESSION, activeActor.username(), activeActor.password());
    }

    private static byte[] read(java.nio.file.Path path) {
        try { return java.nio.file.Files.readAllBytes(path); }
        catch (java.io.IOException e) { throw new IllegalStateException("Cannot read exported XLSX", e); }
    }
}
