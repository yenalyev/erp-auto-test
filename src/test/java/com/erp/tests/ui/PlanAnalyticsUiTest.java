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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Route;
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
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI: аналітика «Для плану» і підказка залишку при створенні глобального плану.
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
    @Story("Plan create product stocks")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-PLAN-ANL / AC-04.
            Створення глобального плану, крок «Заплановано»: після вибору виробу
            під рядком видно «Залишок усього» та «Залишок (Цукрарня)».
            Детальні вироблено/видано/використано за місяць і 3 місяці
            доступні на сторінці «Для плану».
            План не зберігається.
            """)
    public void creatingPlanShowsStocksForSelectedProduct() {
        GlobalPlanWizardPage wizard = new GlobalPlanWizardPage(page).openCreate();
        assertThat(wizard.isWizardHeadingVisible()).as("Візард глобального плану").isTrue();
        wizard.attachScreenshot("TC-PLAN-ANL-UI-002 — create form");

        wizard.selectFirstPlannableProduct();
        wizard.attachScreenshot("TC-PLAN-ANL-UI-002 — product selected");
        assertThat(wizard.isStockSummaryVisible())
                .as("Під обраним виробом є обидва показники залишку")
                .isTrue();
        assertThat(wizard.stockSummaryText(GlobalPlanWizardPage.STOCK_TOTAL_LABEL))
                .as("Загальний залишок")
                .containsPattern("\\d");
        assertThat(wizard.stockSummaryText(GlobalPlanWizardPage.STOCK_ROOT_LABEL))
                .as("Залишок у Цукрарні")
                .containsPattern("\\d");
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

    private static String unitLabel(ResourceResponse resource) {
        if (resource.getUnit() == null) {
            return "";
        }
        String name = resource.getUnit().getName();
        return name == null ? "" : name.trim();
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
        List<Map<String, Object>> totals = rows.stream().map(row -> {
            Map<String, Object> byUnit = new LinkedHashMap<>();
            byUnit.put("unit", row.get("unit"));
            for (String key : METRIC_KEYS) {
                byUnit.put(key, row.get(key));
            }
            return byUnit;
        }).toList();
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
        String query = URI.create(url).getRawQuery();
        if (query == null) {
            return Set.of();
        }
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (String parameter : query.split("&")) {
            String[] parts = parameter.split("=", 2);
            if (!URLDecoder.decode(parts[0], StandardCharsets.UTF_8).replace("[]", "")
                    .equals("resourceIds") || parts.length < 2) {
                continue;
            }
            for (String id : URLDecoder.decode(parts[1], StandardCharsets.UTF_8).split(",")) {
                if (!id.isBlank()) {
                    ids.add(Long.parseLong(id));
                }
            }
        }
        return ids;
    }

    private static void assertQuantity(String cardText, int amount, String unit, String label) {
        assertThat(cardText)
                .as("«%s» має показувати %s %s окремо: %s", label, amount, unit, cardText)
                .containsPattern("(?<!\\d)" + amount + "(?:[.,]0+)?\\s*" + Pattern.quote(unit));
    }
}
