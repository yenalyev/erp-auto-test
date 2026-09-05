package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.PlanNeededResourcesFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.PlanResponse;
import com.erp.models.response.ResourceCategoryResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.pages.PlanExecutionPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Slf4j
@Epic("Plans")
@Feature("Потрібні ресурси (Виконання плану)")
public class PlanNeededResourcesUiTest extends BaseUITest {

    private PlanNeededResourcesFixture fixture;
    private StorageFixture storageFixture;
    private Long ownerStorageId;

    private final List<PlanResponse> plans = new ArrayList<>();
    private final List<ManufacturingItemResponse> productions = new ArrayList<>();
    private Long productionStorageId;
    private final List<CleanupMap> maps = new ArrayList<>();
    private final List<Long> storagesNewestFirst = new ArrayList<>();

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        fixture = new PlanNeededResourcesFixture(testContext, apiExecutor);
        fixture.prepareContext();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        ownerStorageId = ConfigProvider.getOwner1StorageId();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupArtifacts() {
        if (fixture != null) {
            for (PlanResponse plan : plans) {
                try {
                    fixture.techMaps().deleteLocationPlan(plan.getId());
                } catch (Exception e) {
                    log.warn("Plan cleanup failed: {}", e.getMessage());
                }
            }
        }
        plans.clear();
        if (fixture != null && productionStorageId != null) {
            for (ManufacturingItemResponse production : productions) {
                try {
                    fixture.production().deleteAs(UserRole.ADMIN, production.getId(), productionStorageId);
                } catch (Exception e) {
                    log.warn("Production cleanup failed: {}", e.getMessage());
                }
            }
        }
        productions.clear();
        productionStorageId = null;
        if (fixture != null) {
            for (CleanupMap map : maps) {
                try {
                    fixture.cleanupTechMap(map.techMap(), map.storageId());
                } catch (Exception e) {
                    log.warn("Tech map cleanup failed: {}", e.getMessage());
                }
            }
        }
        maps.clear();
        if (storageFixture != null) {
            for (Long storageId : storagesNewestFirst) {
                try {
                    storageFixture.archiveStorage(UserRole.ADMIN, storageId);
                    storageFixture.untrackForCleanup(storageId);
                } catch (Exception e) {
                    log.warn("Storage archive failed {}: {}", storageId, e.getMessage());
                }
            }
            storagesNewestFirst.clear();
            storageFixture.deactivateTrackedStorages(UserRole.ADMIN);
        } else {
            storagesNewestFirst.clear();
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-PLN-NR-001")
    @Story("Tab availability")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Вкладка «Потрібні ресурси» активна для поточного місяця на конкретній локації.")
    public void neededTabActiveForCurrentMonth() {
        IsolatedChain isolated = arrangeCanonicalUnderOwner();
        injectRoleSession(UserRole.OWNER_1, isolated.storageId());
        PlanExecutionPage planPage = new PlanExecutionPage(page).open().openNeededResourcesTab();

        assertThat(planPage.isNeededTabEnabled()).isTrue();
        assertThat(planPage.isNeededRowVisible(isolated.chain().getRaw().getName())
                || planPage.isNeededRowVisible(isolated.chain().getIntermediate().getName()))
                .as("Таблиця потреби показує входи техкарти")
                .isTrue();
        planPage.attachScreenshot("TC-PLN-NR-001 needed tab");
    }

    @Test(priority = 20)
    @TestCaseId("TC-PLN-NR-002")
    @Story("Tab availability")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Для завершеного місяця вкладка disabled з tooltip.")
    public void neededTabDisabledForPastMonth() {
        IsolatedChain isolated = arrangeCanonicalUnderOwner();
        YearMonth past = YearMonth.now().minusMonths(1);
        plans.add(fixture.createPlan(
                isolated.storageId(), isolated.chain().getProduct().getId(), past, 5));

        injectRoleSession(UserRole.OWNER_1, isolated.storageId());
        PlanExecutionPage planPage = new PlanExecutionPage(page).open().openNeededResourcesTab();
        assertThat(planPage.isNeededTabEnabled()).isTrue();

        planPage.selectPeriodAt(1);
        assertThat(planPage.isNeededTabDisabled())
                .as("Вкладка «Потрібні ресурси» disabled для минулого місяця")
                .isTrue();
        assertThat(planPage.getNeededPastMonthTooltip()).contains("завершеного місяця");
        planPage.attachScreenshot("TC-PLN-NR-002 past month");
    }

    @Test(priority = 30)
    @TestCaseId("TC-PLN-NR-003")
    @Story("Tab availability")
    @Severity(SeverityLevel.NORMAL)
    @Description("«Всі локації» — банер замість вкладок виконання і потреби.")
    public void allLocationsShowsGuardInsteadOfTabs() {
        injectAllLocationsView();
        injectRoleSessionKeepingStorage(UserRole.ADMIN);
        PlanExecutionPage planPage = new PlanExecutionPage(page).openWithoutExecutionFetch();

        assertThat(planPage.isAllLocationsGuardVisible()).isTrue();
        assertThat(planPage.isNeededTabVisible()).isFalse();
        planPage.attachScreenshot("TC-PLN-NR-003 all locations");
    }

    @Test(priority = 40)
    @TestCaseId("TC-PLN-NR-016")
    @Story("Table")
    @Severity(SeverityLevel.NORMAL)
    @Description("Колонки, бейдж «виробляється», дефіцитні рядки.")
    public void tableColumnsAndProducedBadge() {
        IsolatedChain isolated = arrangeCanonicalUnderOwner();
        injectRoleSession(UserRole.OWNER_1, isolated.storageId());
        PlanExecutionPage planPage = new PlanExecutionPage(page).open().openNeededResourcesTab();

        assertThat(planPage.isNeededHeaderVisible("Ресурс")).isTrue();
        assertThat(planPage.isNeededHeaderVisible("Категорія")).isTrue();
        assertThat(planPage.isNeededHeaderVisible("Потрібно")).isTrue();
        assertThat(planPage.isNeededHeaderVisible("В наявності")).isTrue();
        assertThat(planPage.isNeededHeaderVisible("Дефіцит")).isTrue();
        assertThat(planPage.isNeededProducedBadgeVisible(isolated.chain().getIntermediate().getName())).isTrue();
        assertThat(planPage.isNeededProducedBadgeVisible(isolated.chain().getRaw().getName())).isFalse();
        assertThat(planPage.getNeededAmount(isolated.chain().getIntermediate().getName()))
                .isCloseTo(200.0, within(0.2));
        planPage.attachScreenshot("TC-PLN-NR-016 table");
    }

    @Test(priority = 50)
    @TestCaseId("TC-PLN-NR-017")
    @Story("Table")
    @Severity(SeverityLevel.MINOR)
    @Description("Empty state: «Немає потреби в додаткових ресурсах».")
    public void emptyStateWhenNoNeed() {
        StorageResponse storage = storageFixture.createChildStorage(ownerStorageId, "nr-ui-empty-");
        storagesNewestFirst.add(0, storage.getId());
        PlanNeededResourcesFixture.Chain chain = trackChain(storage.getId(), fixture.createTwoLevelChain(storage.getId()));

        injectRoleSession(UserRole.OWNER_1, storage.getId());
        PlanExecutionPage planPage = new PlanExecutionPage(page).open().openNeededResourcesTab();
        assertThat(planPage.isNeededEmptyVisible()).isTrue();
        assertThat(chain.getProduct().getName()).isNotBlank();
        planPage.attachScreenshot("TC-PLN-NR-017 empty");
    }

    @Test(priority = 60)
    @TestCaseId("TC-PLN-NR-020")
    @Story("Filters")
    @Severity(SeverityLevel.NORMAL)
    @Description("Фільтр «Категорії» лишає лише ресурси обраної категорії.")
    public void categoryFilterHidesOtherRows() {
        List<ResourceCategoryResponse> categories = fixture.listCategories();
        if (categories == null || categories.size() < 2) {
            throw new SkipException("Need ≥2 resource categories for TC-PLN-NR-020");
        }
        StorageResponse storage = storageFixture.createChildStorage(ownerStorageId, "nr-ui-cat-");
        storagesNewestFirst.add(0, storage.getId());
        PlanNeededResourcesFixture.Chain chain = trackChain(
                storage.getId(),
                fixture.createTwoLevelChain(
                        Set.of(storage.getId()),
                        categories.get(0).getId(),
                        categories.get(1).getId()));
        plans.add(fixture.createCurrentMonthPlan(storage.getId(), chain.getProduct().getId(), 20));

        injectRoleSession(UserRole.OWNER_1, storage.getId());
        PlanExecutionPage planPage = new PlanExecutionPage(page).open().openNeededResourcesTab();
        assertThat(planPage.isNeededRowVisible(chain.getIntermediate().getName())).isTrue();
        assertThat(planPage.isNeededRowVisible(chain.getRaw().getName())).isTrue();

        planPage.selectNeededCategory(categories.get(1).getName());
        assertThat(planPage.isNeededRowVisible(chain.getRaw().getName())).isTrue();
        assertThat(planPage.isNeededRowVisible(chain.getIntermediate().getName())).isFalse();
        planPage.attachScreenshot("TC-PLN-NR-020 category filter");
    }

    @Test(priority = 70)
    @TestCaseId("TC-PLN-NR-021")
    @Story("Filters")
    @Severity(SeverityLevel.NORMAL)
    @Description("«Лише дефіцитні» ховає покриті рядки; empty фільтрів, якщо всі відсіяні.")
    public void onlyShortagesFilter() {
        StorageResponse storage = storageFixture.createChildStorage(ownerStorageId, "nr-ui-def-");
        storagesNewestFirst.add(0, storage.getId());
        PlanNeededResourcesFixture.Chain chain = trackChain(storage.getId(), fixture.createTwoLevelChain(storage.getId()));
        plans.add(fixture.createCurrentMonthPlan(storage.getId(), chain.getProduct().getId(), 10));
        fixture.seedStock(storage.getId(), chain.getIntermediate().getId(), 1000);

        injectRoleSession(UserRole.OWNER_1, storage.getId());
        PlanExecutionPage planPage = new PlanExecutionPage(page).open().openNeededResourcesTab();
        assertThat(planPage.isNeededRowVisible(chain.getIntermediate().getName())).isTrue();

        planPage.setOnlyShortages(true);
        assertThat(planPage.isNeededRowVisible(chain.getIntermediate().getName())).isFalse();
        assertThat(planPage.isNeededFilterEmptyVisible() || planPage.getNeededRowCount() == 0).isTrue();
        planPage.attachScreenshot("TC-PLN-NR-021 shortages");
    }

    @Test(priority = 80)
    @TestCaseId("TC-PLN-NR-022")
    @Story("Filters")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Чекбокс «Враховувати залишки» перемикає includeStock.")
    public void includeStockCheckbox() {
        IsolatedChain isolated = arrangeCanonicalUnderOwner();
        injectRoleSession(UserRole.OWNER_1, isolated.storageId());
        PlanExecutionPage planPage = new PlanExecutionPage(page).open().openNeededResourcesTab();
        assertThat(planPage.isIncludeStockChecked()).isTrue();
        double withStock = planPage.getNeededAmount(isolated.chain().getRaw().getName());

        planPage.setIncludeStock(false);
        double withoutStock = planPage.getNeededAmount(isolated.chain().getRaw().getName());
        assertThat(withoutStock).isGreaterThan(withStock);
        planPage.attachScreenshot("TC-PLN-NR-022 include stock");
    }

    @Test(priority = 90)
    @TestCaseId("TC-PLN-NR-023")
    @Story("Filters")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Чекбокс «Враховувати виготовлене» перемикає includeProduced.")
    public void includeProducedCheckbox() {
        IsolatedChain isolated = arrangeCanonicalUnderOwner();
        injectRoleSession(UserRole.OWNER_1, isolated.storageId());
        PlanExecutionPage planPage = new PlanExecutionPage(page).open().openNeededResourcesTab();
        double fullPlan = planPage.getNeededAmount(isolated.chain().getIntermediate().getName());

        planPage.setIncludeProduced(true);
        double remaining = planPage.getNeededAmount(isolated.chain().getIntermediate().getName());
        assertThat(remaining).isLessThan(fullPlan);
        planPage.attachScreenshot("TC-PLN-NR-023 include produced");
    }

    @Test(priority = 100)
    @TestCaseId("TC-PLN-NR-024")
    @Story("Filters")
    @Severity(SeverityLevel.MINOR)
    @Description("Сортування таблиці потреби за назвою.")
    public void sortByResourceName() {
        IsolatedChain isolated = arrangeCanonicalUnderOwner();
        injectRoleSession(UserRole.OWNER_1, isolated.storageId());
        PlanExecutionPage planPage = new PlanExecutionPage(page).open().openNeededResourcesTab();
        planPage.clickNeededSort("Ресурс");
        List<String> asc = planPage.neededResourceNames();
        planPage.clickNeededSort("Ресурс");
        List<String> desc = planPage.neededResourceNames();
        assertThat(asc).isNotEmpty();
        assertThat(desc).containsExactlyElementsOf(asc.reversed());
        planPage.attachScreenshot("TC-PLN-NR-024 sort");
    }

    @Test(priority = 110)
    @TestCaseId("TC-PLN-NR-025")
    @Story("Filters")
    @Severity(SeverityLevel.MINOR)
    @Description("«Скопіювати» — TSV потреби.")
    public void copyNeededTableTsv() {
        IsolatedChain isolated = arrangeCanonicalUnderOwner();
        injectRoleSession(UserRole.OWNER_1, isolated.storageId());
        PlanExecutionPage planPage = new PlanExecutionPage(page).open().openNeededResourcesTab();
        planPage.installClipboardCapture();
        planPage.clickCopyNeeded();
        planPage.waitForNeededCopiedFeedback();
        String copied = planPage.getCapturedClipboardText();
        assertThat(copied).contains("Ресурс");
        assertThat(copied).contains("Категорія");
        assertThat(copied).contains(isolated.chain().getRaw().getName());
        planPage.attachScreenshot("TC-PLN-NR-025 copy");
    }

    @Test(priority = 120)
    @TestCaseId("TC-PLN-NR-033")
    @Story("Drill-down")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Розгортання рядка показує «Потрібно для виробів» з техкартами.")
    public void expandRowShowsSources() {
        IsolatedChain isolated = arrangeCanonicalUnderOwner();
        injectRoleSession(UserRole.OWNER_1, isolated.storageId());
        PlanExecutionPage planPage = new PlanExecutionPage(page).open().openNeededResourcesTab();
        planPage.expandNeededRow(isolated.chain().getRaw().getName());
        String sources = planPage.getNeededSourcesText();
        assertThat(sources).contains("Потрібно для виробів");
        assertThat(sources).contains(isolated.chain().getProduct().getName());
        assertThat(sources).contains("техкарта");
        planPage.collapseNeededRow(isolated.chain().getRaw().getName());
        assertThat(planPage.isNeededSourcesVisible()).isFalse();
        planPage.attachScreenshot("TC-PLN-NR-033 expand");
    }

    @Test(priority = 130)
    @TestCaseId("TC-PLN-NR-044")
    @Story("Parent location")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Перемикання парент ↔ дитина оновлює таблицю потреби.")
    public void parentVersusChildAmounts() {
        StorageResponse parent = storageFixture.createUnitStorage(ownerStorageId, "nr-ui-par-");
        StorageResponse childA = storageFixture.createChildStorage(parent.getId(), "nr-ui-ca-");
        StorageResponse childB = storageFixture.createChildStorage(parent.getId(), "nr-ui-cb-");
        storagesNewestFirst.add(childB.getId());
        storagesNewestFirst.add(childA.getId());
        storagesNewestFirst.add(parent.getId());

        PlanNeededResourcesFixture.Chain chain = fixture.createTwoLevelChain(
                Set.of(childA.getId(), childB.getId()),
                testContext.get(com.erp.test_context.ContextKey.SHARED_RESOURCE_CATEGORY_ID),
                testContext.get(com.erp.test_context.ContextKey.SHARED_RESOURCE_CATEGORY_ID));
        maps.add(new CleanupMap(chain.getProductMap(), childA.getId()));
        maps.add(new CleanupMap(chain.getIntermediateMap(), childA.getId()));
        plans.add(fixture.createCurrentMonthPlan(childA.getId(), chain.getProduct().getId(), 100));
        plans.add(fixture.createCurrentMonthPlan(childB.getId(), chain.getProduct().getId(), 50));

        injectRoleSession(UserRole.ADMIN, parent.getId());
        PlanExecutionPage parentPage = new PlanExecutionPage(page).open().openNeededResourcesTab();
        double parentNeeded = parentPage.getNeededAmount(chain.getIntermediate().getName());
        parentPage.attachScreenshot("TC-PLN-NR-044 parent");

        injectRoleSession(UserRole.ADMIN, childA.getId());
        page.reload();
        PlanExecutionPage childPage = new PlanExecutionPage(page).open().openNeededResourcesTab();
        double childNeeded = childPage.getNeededAmount(chain.getIntermediate().getName());
        assertThat(parentNeeded).isGreaterThan(childNeeded);
        childPage.attachScreenshot("TC-PLN-NR-044 child");
    }

    private IsolatedChain arrangeCanonicalUnderOwner() {
        StorageResponse storage = storageFixture.createChildStorage(ownerStorageId, "nr-ui-can-");
        storagesNewestFirst.add(0, storage.getId());
        PlanNeededResourcesFixture.Chain chain = trackChain(storage.getId(), fixture.createTwoLevelChain(storage.getId()));
        productions.add(fixture.seedCanonicalPlanProductionAndStock(storage.getId(), chain));
        productionStorageId = storage.getId();
        fixture.techMaps().getLocationPlans(storage.getId()).stream()
                .filter(p -> YearMonth.now().getMonthValue() == p.getMonth()
                        && YearMonth.now().getYear() == p.getYear())
                .forEach(plans::add);
        return new IsolatedChain(storage.getId(), chain);
    }

    private PlanNeededResourcesFixture.Chain trackChain(Long storageId, PlanNeededResourcesFixture.Chain chain) {
        maps.add(new CleanupMap(chain.getProductMap(), storageId));
        maps.add(new CleanupMap(chain.getIntermediateMap(), storageId));
        return chain;
    }

    private void injectRoleSession(UserRole role, long selectedStorageId) {
        injectSessionCookies(cachedSessionCookies(role), sessionCookieDomain());
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
    }

    private void injectRoleSessionKeepingStorage(UserRole role) {
        injectSessionCookies(cachedSessionCookies(role), sessionCookieDomain());
    }

    private record IsolatedChain(Long storageId, PlanNeededResourcesFixture.Chain chain) {
    }

    private record CleanupMap(TechnologicalMapResponse techMap, Long storageId) {
    }
}
