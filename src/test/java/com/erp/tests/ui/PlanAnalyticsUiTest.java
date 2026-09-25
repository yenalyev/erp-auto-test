package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.GlobalPlanWizardPage;
import com.erp.pages.PlanAnalyticsPage;
import com.erp.pages.components.DateRangePickerComponent;
import com.erp.utils.config.ConfigProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.Response;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI: аналітика «Для плану» і показники на першому кроці глобального плану.
 */
@Slf4j
@Epic("Analytics")
@Feature("Plan analytics")
public class PlanAnalyticsUiTest extends BaseUITest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> METRIC_KEYS = List.of(
            "produced", "relocated", "used", "stockTotal", "stockInRoot");
    private static final int[] FIRST_UNIT_VALUES = {10, 4, 2, 7, 3};
    private static final int[] SECOND_UNIT_VALUES = {5, 1, 3, 8, 6};
    private static final String SUPPLIER_MOU = "МОУ";
    private static final String SUPPLIER_OTHER = "Інші";

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        long storageId = ConfigProvider.getOwner1StorageId();
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + storageId + "');");
        log.info("ADMIN session injected for plan analytics UI, storageId={}", storageId);
    }

    @Test(priority = 10)
    @TestCaseId("TC-PLAN-ANL-UI-001")
    @Story("Plan analytics page")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-PLAN-ANL / AC-03.
            Сайдбар «Аналітика» → «Для плану». Без вибору — порожній стан.
            Пресети «Місяць» (попередній повний місяць) і «3 місяці».
            Після вибору ресурсу — підсумки Вироблено / Відвантажено / Використано /
            Залишок усього / Залишок (Цукрарня); розгортання рядка — Цукрарня / Інші локації.
            """)
    public void planAnalyticsPageShowsMonthAndThreeMonthProductFigures() {
        page.navigate(ConfigProvider.getBaseUrl() + "/production");
        page.waitForLoadState();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        sidebar.openCollapsibleItem(AppSidebarPage.GROUP_ANALYTICS, AppSidebarPage.NAV_PLAN_ANALYTICS);

        PlanAnalyticsPage analytics = new PlanAnalyticsPage(page).waitForLoaded();
        assertThat(page.url()).contains("/analytics/plan");
        assertThat(analytics.isEmptyStateVisible())
                .as("Порожній стан без вибору ресурсу/категорії")
                .isTrue();
        analytics.attachScreenshot("TC-PLAN-ANL-UI-001 — empty state");

        DateRangePickerComponent picker = analytics.periodPicker();
        assertThat(picker.isVisible()).as("DateRangePicker «Період»").isTrue();

        LocalDate today = analytics.browserToday();
        LocalDate monthFrom = PlanAnalyticsPage.lastFullMonthFrom(today);
        LocalDate monthTo = PlanAnalyticsPage.lastFullMonthTo(today);
        assertThat(picker.getFromIso()).as("дефолт «Місяць»: from").isEqualTo(monthFrom.toString());
        assertThat(picker.getToIso()).as("дефолт «Місяць»: to").isEqualTo(monthTo.toString());

        picker.open();
        assertThat(picker.visiblePresetLabels(DateRangePickerComponent.PLAN_PRESET_LABELS))
                .as("Пресети плану")
                .containsExactlyElementsOf(DateRangePickerComponent.PLAN_PRESET_LABELS);
        analytics.attachScreenshot("TC-PLAN-ANL-UI-001 — presets");

        String initialTrigger = picker.getTriggerText();
        picker.clickPreset(DateRangePickerComponent.PRESET_3_MONTHS);
        analytics.waitUntilTriggerChanges(initialTrigger);
        analytics.attachScreenshot("TC-PLAN-ANL-UI-001 — 3 months");
        assertThat(picker.getFromIso())
                .as("«3 місяці»: from")
                .isEqualTo(PlanAnalyticsPage.threeFullMonthsFrom(today).toString());
        assertThat(picker.getToIso()).as("«3 місяці»: to").isEqualTo(monthTo.toString());

        picker.clickPreset(DateRangePickerComponent.PRESET_MONTH);
        picker.waitUntilRange(monthFrom, monthTo);

        analytics.selectFirstResource();
        analytics.attachScreenshot("TC-PLAN-ANL-UI-001 — resource selected");
        assertThat(analytics.hasTotals())
                .as("Підсумки Вироблено / Відвантажено / Використано / залишки")
                .isTrue();
        assertThat(analytics.hasColumnHeaders())
                .as("Колонки таблиці аналітики плану")
                .isTrue();
        assertThat(analytics.isEmptyStateVisible())
                .as("Порожній стан зникає після вибору")
                .isFalse();

        if (analytics.dataRowCount() > 0) {
            analytics.expandFirstRow();
            analytics.attachScreenshot("TC-PLAN-ANL-UI-001 — stock expanded");
            assertThat(analytics.hasStockLocationGroups())
                    .as("Розгортання: Цукрарня / Інші локації або «Залишків немає»")
                    .isTrue();
        }
    }

    @Test(priority = 20)
    @TestCaseId("TC-PLAN-ANL-UI-002")
    @Story("Plan create product insights")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-PLAN-ANL / AC-04.
            Створення глобального плану, крок «Заплановано»: після вибору виробу
            під рядком видно всі п'ять показників «Для плану», значення збігаються
            з реальною відповіддю rows для місяця, 3 місяців, півроку й року.
            Нульові значення показуються як тире.
            План не зберігається.
            """)
    public void creatingPlanShowsInsightsForSelectedProduct() {
        GlobalPlanWizardPage wizard = new GlobalPlanWizardPage(page).openCreate();
        assertThat(wizard.isWizardHeadingVisible()).as("Візард глобального плану").isTrue();
        wizard.attachScreenshot("TC-PLAN-ANL-UI-002 — create form");

        Response monthRows = page.waitForResponse(
                response -> response.url().contains("/api/v1/analytics/plan/rows")
                        && response.request().method().equals("GET"),
                wizard::selectFirstPlannableProduct);
        wizard.attachScreenshot("TC-PLAN-ANL-UI-002 — product selected");
        assertThat(wizard.isInsightsPeriodVisible()).isTrue();
        assertWizardMatchesRows(wizard, monthRows);
        for (String period : List.of("3 місяці", "Півроку", "Рік")) {
            Response rows = page.waitForResponse(
                    response -> response.url().contains("/api/v1/analytics/plan/rows")
                            && response.request().method().equals("GET"),
                    () -> wizard.selectInsightsPeriod(period));
            assertWizardMatchesRows(wizard, rows);
        }
    }

    @Test(priority = 30)
    @TestCaseId("TC-PLAN-ANL-UI-003")
    @Story("Totals keep measurement units separate")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Для двох одночасно вибраних ресурсів з різними одиницями кожна картка
            Вироблено / Відвантажено / Використано / Залишок усього /
            Залишок (Цукрарня) показує обидві величини окремо, без змішаної суми.
            Значення rows контрольовані, щоб тест не залежав від операцій на складах.
            """)
    public void totalsDoNotAddDifferentMeasurementUnits() {
        List<ResourceResponse> resources = new ResourceFixture(testContext, apiExecutor)
                .getPage(UserRole.ADMIN, true, null);
        ResourceResponse first = resources.stream()
                .filter(resource -> !unitLabel(resource).isBlank())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Немає активного ресурсу з одиницею вимірювання"));
        ResourceResponse second = resources.stream()
                .filter(resource -> !unitLabel(resource).isBlank())
                .filter(resource -> !unitLabel(resource).equals(unitLabel(first)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Потрібні два активні ресурси з різними одиницями"));

        AtomicReference<Set<Long>> requestedIds = new AtomicReference<>(Set.of());
        page.route("**/api/v1/analytics/plan/rows**", route -> {
            Set<Long> ids = selectedResourceIds(route.request().url());
            requestedIds.set(ids);
            List<Map<String, Object>> rows = new ArrayList<>();
            if (ids.contains(first.getId())) {
                rows.add(analyticRow(first, FIRST_UNIT_VALUES));
            }
            if (ids.contains(second.getId())) {
                rows.add(analyticRow(second, SECOND_UNIT_VALUES));
            }
            route.fulfill(new Route.FulfillOptions()
                    .setStatus(200)
                    .setContentType("application/json")
                    .setBody(analyticPage(rows)));
        });

        PlanAnalyticsPage analytics = new PlanAnalyticsPage(page).open()
                .selectResourcesByName(first.getName(), second.getName());
        page.waitForCondition(
                () -> requestedIds.get().containsAll(Set.of(first.getId(), second.getId()))
                        && analytics.dataRowCount() == 2,
                new com.microsoft.playwright.Page.WaitForConditionOptions()
                        .setTimeout(ConfigProvider.getUiTimeoutSeconds() * 1000));

        for (int index = 0; index < PlanAnalyticsPage.TOTAL_LABELS.size(); index++) {
            String label = PlanAnalyticsPage.TOTAL_LABELS.get(index);
            String cardText = analytics.totalCardText(label);
            assertQuantity(cardText, FIRST_UNIT_VALUES[index], unitLabel(first), label);
            assertQuantity(cardText, SECOND_UNIT_VALUES[index], unitLabel(second), label);
            int invalidMixedSum = FIRST_UNIT_VALUES[index] + SECOND_UNIT_VALUES[index];
            assertThat(cardText)
                    .as("«%s» не має показувати змішану суму %s", label, invalidMixedSum)
                    .doesNotContainPattern("(?<![\\d.,])" + invalidMixedSum + "(?:[.,]0+)?(?![\\d])");
        }
        analytics.attachScreenshot("TC-PLAN-ANL-UI-003 — separate units in totals");
    }

    @Test(priority = 40)
    @TestCaseId("TC-PLAN-ANL-UI-004")
    @Story("Supplier options and repeated query")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-PLAN-ANL-SUPPLIER / AC-01, AC-02, AC-06.
            Опції Постачальника унікальні, пошук працює, а один і два вибрані
            постачальники формують повторювані supplier та показують контрольовані rows.
            """)
    public void supplierOptionsSearchAndRepeatedQueryFilterRows() {
        List<ResourceResponse> selected = twoNamedResources();
        ResourceResponse first = selected.get(0);
        ResourceResponse second = selected.get(1);
        AtomicReference<List<String>> requestedSuppliers = new AtomicReference<>(List.of());

        page.route("**/api/v1/analytics/plan/rows**", route -> {
            List<String> suppliers = queryValues(route.request().url(), "supplier");
            requestedSuppliers.set(suppliers);
            List<Map<String, Object>> rows = new ArrayList<>();
            if (suppliers.isEmpty() || suppliers.contains(SUPPLIER_MOU)) {
                rows.add(analyticRow(first, FIRST_UNIT_VALUES));
            }
            if (suppliers.isEmpty() || suppliers.contains(SUPPLIER_OTHER)) {
                rows.add(analyticRow(second, SECOND_UNIT_VALUES));
            }
            route.fulfill(new Route.FulfillOptions()
                    .setStatus(200)
                    .setContentType("application/json")
                    .setBody(analyticPage(rows)));
        });

        PlanAnalyticsPage analytics = new PlanAnalyticsPage(page).open()
                .selectResourcesByName(first.getName(), second.getName());
        List<String> options = analytics.supplierOptions();
        assertThat(options)
                .as("Опції з category_property_options[Постачальник]")
                .contains(SUPPLIER_MOU, SUPPLIER_OTHER)
                .doesNotHaveDuplicates()
                .doesNotContain("");
        assertThat(analytics.searchSupplierOptions("МО"))
                .contains(SUPPLIER_MOU)
                .doesNotContain(SUPPLIER_OTHER);

        analytics.selectSuppliers(SUPPLIER_MOU);
        page.waitForCondition(
                () -> requestedSuppliers.get().equals(List.of(SUPPLIER_MOU))
                        && analytics.dataRowCount() == 1,
                new com.microsoft.playwright.Page.WaitForConditionOptions()
                        .setTimeout(ConfigProvider.getUiTimeoutSeconds() * 1000));
        assertThat(analytics.hasDataRowFor(first.getName())).isTrue();
        assertThat(analytics.hasDataRowFor(second.getName())).isFalse();

        analytics.selectSuppliers(SUPPLIER_OTHER);
        page.waitForCondition(
                () -> Set.copyOf(requestedSuppliers.get())
                        .equals(Set.of(SUPPLIER_MOU, SUPPLIER_OTHER))
                        && requestedSuppliers.get().size() == 2
                        && analytics.dataRowCount() == 2,
                new com.microsoft.playwright.Page.WaitForConditionOptions()
                        .setTimeout(ConfigProvider.getUiTimeoutSeconds() * 1000));
        assertThat(requestedSuppliers.get())
                .as("supplier передається двома повторюваними query-параметрами")
                .containsExactlyInAnyOrder(SUPPLIER_MOU, SUPPLIER_OTHER);
        assertThat(analytics.hasDataRowFor(first.getName())).isTrue();
        assertThat(analytics.hasDataRowFor(second.getName())).isTrue();
        analytics.attachScreenshot("TC-PLAN-ANL-UI-004 — supplier OR");
    }

    @Test(priority = 50)
    @TestCaseId("TC-PLAN-ANL-UI-005")
    @Story("Supplier clear and non-persistent state")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            REQ-PLAN-ANL-SUPPLIER / AC-03, AC-05.
            Зміна періоду зберігає supplier у поточному стані; очищення прибирає
            параметр; reload і повторне відкриття сторінки не відновлюють вибір.
            """)
    public void supplierSurvivesPeriodChangeButNotClearReloadOrReopen() {
        ResourceResponse resource = twoNamedResources().getFirst();
        AtomicReference<List<String>> requestedSuppliers = new AtomicReference<>(List.of());
        AtomicInteger rowsCalls = new AtomicInteger();
        page.route("**/api/v1/analytics/plan/rows**", route -> {
            requestedSuppliers.set(queryValues(route.request().url(), "supplier"));
            rowsCalls.incrementAndGet();
            route.fulfill(new Route.FulfillOptions()
                    .setStatus(200)
                    .setContentType("application/json")
                    .setBody(analyticPage(List.of(analyticRow(resource, FIRST_UNIT_VALUES)))));
        });

        PlanAnalyticsPage analytics = new PlanAnalyticsPage(page).open()
                .selectResourceByName(resource.getName())
                .selectSuppliers(SUPPLIER_MOU);
        page.waitForCondition(() -> requestedSuppliers.get().equals(List.of(SUPPLIER_MOU)));

        int beforePeriodChange = rowsCalls.get();
        analytics.periodPicker().clickPreset(DateRangePickerComponent.PRESET_3_MONTHS);
        page.waitForCondition(
                () -> rowsCalls.get() > beforePeriodChange
                        && requestedSuppliers.get().equals(List.of(SUPPLIER_MOU)));

        int beforeClear = rowsCalls.get();
        analytics.clearSuppliers(SUPPLIER_MOU);
        page.waitForCondition(
                () -> rowsCalls.get() > beforeClear && requestedSuppliers.get().isEmpty());

        analytics.selectSuppliers(SUPPLIER_MOU);
        page.waitForCondition(() -> requestedSuppliers.get().equals(List.of(SUPPLIER_MOU)));
        int beforeReload = rowsCalls.get();
        page.reload();
        analytics.waitForLoaded();
        page.waitForCondition(() -> analytics.isEmptyStateVisible() && analytics.isSupplierFilterEmpty());
        assertThat(rowsCalls.get()).as("reload без resource/category не викликає rows").isEqualTo(beforeReload);

        analytics.selectResourceByName(resource.getName());
        page.waitForCondition(
                () -> rowsCalls.get() > beforeReload && requestedSuppliers.get().isEmpty());

        analytics.selectSuppliers(SUPPLIER_MOU);
        page.waitForCondition(() -> requestedSuppliers.get().equals(List.of(SUPPLIER_MOU)));
        page.navigate(ConfigProvider.getBaseUrl() + "/production");
        page.waitForLoadState();
        int beforeReopen = rowsCalls.get();
        analytics.open();
        page.waitForCondition(() -> analytics.isEmptyStateVisible() && analytics.isSupplierFilterEmpty());
        assertThat(rowsCalls.get())
                .as("повторне відкриття без resource/category не викликає rows")
                .isEqualTo(beforeReopen);
    }

    @Test(priority = 60)
    @TestCaseId("TC-PLAN-ANL-UI-006")
    @Story("Supplier is not a tracking target")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            REQ-PLAN-ANL-SUPPLIER / AC-03.
            Вибір Постачальника без ресурсу/категорії не викликає rows API.
            Після вибору ресурсу перший rows-запит містить раніше вибраний supplier.
            """)
    public void supplierAloneDoesNotRequestRowsAndIsAppliedWhenTargetAppears() {
        ResourceResponse resource = twoNamedResources().getFirst();
        AtomicInteger rowsCalls = new AtomicInteger();
        AtomicReference<List<String>> requestedSuppliers = new AtomicReference<>(List.of());
        page.route("**/api/v1/analytics/plan/rows**", route -> {
            rowsCalls.incrementAndGet();
            requestedSuppliers.set(queryValues(route.request().url(), "supplier"));
            route.fulfill(new Route.FulfillOptions()
                    .setStatus(200)
                    .setContentType("application/json")
                    .setBody(analyticPage(List.of(analyticRow(resource, FIRST_UNIT_VALUES)))));
        });

        PlanAnalyticsPage analytics = new PlanAnalyticsPage(page).open();
        analytics.selectSuppliers(SUPPLIER_MOU);
        assertThat(rowsCalls.get()).as("supplier сам не формує вибірку").isZero();
        assertThat(analytics.isEmptyStateVisible()).isTrue();

        analytics.selectResourceByName(resource.getName());
        page.waitForCondition(
                () -> rowsCalls.get() == 1
                        && requestedSuppliers.get().equals(List.of(SUPPLIER_MOU)),
                new com.microsoft.playwright.Page.WaitForConditionOptions()
                        .setTimeout(ConfigProvider.getUiTimeoutSeconds() * 1000));
        assertThat(requestedSuppliers.get()).containsExactly(SUPPLIER_MOU);
    }

    private static String unitLabel(ResourceResponse resource) {
        if (resource.getUnit() == null) {
            return "";
        }
        String name = resource.getUnit().getName();
        return name == null ? "" : name.trim();
    }

    private void assertWizardMatchesRows(GlobalPlanWizardPage wizard, Response response) {
        assertThat(response.status()).as("GET plan/rows").isEqualTo(200);
        Set<Long> ids = selectedResourceIds(response.url());
        assertThat(ids).as("Фільтр rows містить один обраний виріб").hasSize(1);
        long id = ids.iterator().next();
        JsonNode body;
        try {
            body = JSON.readTree(response.text());
        } catch (JsonProcessingException e) {
            throw new AssertionError("Некоректна відповідь plan/rows", e);
        }
        JsonNode row = null;
        for (JsonNode candidate : body.path("content")) {
            if (candidate.path("resourceId").asLong() == id) {
                row = candidate;
                break;
            }
        }
        for (int i = 0; i < METRIC_KEYS.size(); i++) {
            String key = METRIC_KEYS.get(i);
            String label = PlanAnalyticsPage.TOTAL_LABELS.get(i);
            BigDecimal expected = row == null ? BigDecimal.ZERO : row.path(key).decimalValue();
            page.waitForCondition(() -> {
                try {
                    return shownMatches(wizard.insightsMetricValue(0, label), expected);
                } catch (RuntimeException e) {
                    return false;
                }
            });
            String shown = wizard.insightsMetricValue(0, label);
            if (expected.signum() == 0) {
                assertThat(shown).as(label).isEqualTo("—");
            } else {
                BigDecimal actual = new BigDecimal(shown.replaceAll("[\\p{Z}\\s]", "")
                        .replace(',', '.'));
                assertThat(actual).as(label).isEqualByComparingTo(expected);
            }
        }
    }

    private static boolean shownMatches(String shown, BigDecimal expected) {
        if (expected.signum() == 0) return shown.equals("—");
        try {
            return new BigDecimal(shown.replaceAll("[\\p{Z}\\s]", "")
                    .replace(',', '.')).compareTo(expected) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static Map<String, Object> analyticRow(ResourceResponse resource, int[] values) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("resourceId", resource.getId());
        row.put("resourceName", resource.getName());
        if (resource.getCategory() != null) {
            row.put("resourceCategoryId", resource.getCategory().getId());
            row.put("resourceCategoryName", resource.getCategory().getName());
        }
        row.put("unit", unitLabel(resource));
        for (int index = 0; index < METRIC_KEYS.size(); index++) {
            row.put(METRIC_KEYS.get(index), values[index]);
        }
        return row;
    }

    private static String analyticPage(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> totalsByUnit = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String unit = String.valueOf(row.get("unit"));
            Map<String, Object> byUnit = totalsByUnit.computeIfAbsent(unit, ignored -> {
                Map<String, Object> total = new LinkedHashMap<>();
                total.put("unit", unit);
                for (String key : METRIC_KEYS) {
                    total.put(key, BigDecimal.ZERO);
                }
                return total;
            });
            for (String key : METRIC_KEYS) {
                BigDecimal current = new BigDecimal(String.valueOf(byUnit.get(key)));
                BigDecimal value = new BigDecimal(String.valueOf(row.get(key)));
                byUnit.put(key, current.add(value));
            }
        }
        List<Map<String, Object>> totals = new ArrayList<>(totalsByUnit.values());
        Map<String, Object> body = Map.of(
                "content", rows,
                "page", Map.of("size", 20, "number", 0,
                        "totalElements", rows.size(), "totalPages", rows.isEmpty() ? 0 : 1),
                "totals", totals);
        try {
            return JSON.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не вдалося створити контрольну відповідь plan/rows", e);
        }
    }

    private static Set<Long> selectedResourceIds(String url) {
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (String id : queryValues(url, "resourceIds")) {
            ids.add(Long.parseLong(id));
        }
        return ids;
    }

    private static List<String> queryValues(String url, String name) {
        String query = URI.create(url).getRawQuery();
        if (query == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String parameter : query.split("&")) {
            String[] parts = parameter.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8).replace("[]", "");
            if (!key.equals(name) || parts.length < 2) {
                continue;
            }
            for (String value : URLDecoder.decode(parts[1], StandardCharsets.UTF_8).split(",")) {
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private List<ResourceResponse> twoNamedResources() {
        List<ResourceResponse> resources = new ResourceFixture(testContext, apiExecutor)
                .getPage(UserRole.ADMIN, true, null).stream()
                .filter(resource -> resource.getName() != null && !resource.getName().isBlank())
                .filter(resource -> !unitLabel(resource).isBlank())
                .limit(2)
                .toList();
        assertThat(resources).as("Потрібні два активні ресурси для UI rows").hasSize(2);
        return resources;
    }

    private static void assertQuantity(String cardText, int amount, String unit, String label) {
        assertThat(cardText)
                .as("«%s» має показувати %s %s окремо: %s", label, amount, unit, cardText)
                .containsPattern("(?<!\\d)" + amount + "(?:[.,]0+)?\\s*" + Pattern.quote(unit));
    }
}
