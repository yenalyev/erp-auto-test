package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.GlobalPlanFixture;
import com.erp.models.response.GlobalPlanResponse;
import com.erp.pages.GlobalPlanWizardPage;
import com.erp.pages.PlanAnalyticsPage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Production Planning")
@Feature("Global Plans UI")
public class GlobalPlanInsightsUiTest extends BaseUITest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ROWS_ROUTE = "**/api/v1/analytics/plan/rows**";
    private static final String STOCK_ROUTE = "**/api/v1/analytics/plan/stock/*";
    private static final List<String> LABELS = List.of(
            "Вироблено", "Відвантажено", "Використано",
            GlobalPlanWizardPage.STOCK_TOTAL_LABEL, GlobalPlanWizardPage.STOCK_ROOT_LABEL);

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
        injectAllLocationsView();
    }

    @TestCaseId("TC-GP-UI-INSIGHTS-001")
    @Test
    @Story("Step 1 insights for selected products and completed-month periods")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Створення: фільтр лише за обраними виробами, п'ять показників для кожного, "
            + "чотири повні періоди, фактичні дати, середнє за місяць і локації залишку.")
    public void createFormShowsPerProductMetricsForEveryPeriodAndStockLocations() {
        AtomicReference<String> lastRowsUrl = new AtomicReference<>();
        AtomicReference<Long> firstId = new AtomicReference<>();
        AtomicReference<String> stockUrl = new AtomicReference<>();
        page.route(ROWS_ROUTE, route -> {
            String url = route.request().url();
            lastRowsUrl.set(url);
            List<Long> ids = selectedIds(url);
            if (!ids.isEmpty()) firstId.compareAndSet(null, ids.getFirst());
            LocalDate from = LocalDate.parse(query(url, "fromDate"));
            LocalDate to = LocalDate.parse(query(url, "toDate"));
            int months = (int) ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)) + 1;
            List<Map<String, Object>> rows = ids.stream()
                    .map(id -> id.equals(firstId.get())
                            ? row(id, months * 12, 6, 3, 18, 9)
                            : row(id, months * 24, 7, 4, 30, 12))
                    .toList();
            fulfillJson(route, 200, analyticPage(rows));
        });
        page.route(STOCK_ROUTE, route -> {
            stockUrl.set(route.request().url());
            fulfillJson(route, 200, json(List.of(
                    location(101, "Цех А", "Цукрарня", true, 4),
                    location(102, "Цех Б", "Цукрарня", true, 5),
                    location(201, "Склад В", "Зовнішній склад", false, 9))));
        });

        GlobalPlanWizardPage wizard = new GlobalPlanWizardPage(page).openCreate();
        assertThat(wizard.isInsightsPeriodVisible()).as("Перемикач прихований без виробу").isFalse();
        assertThat(lastRowsUrl.get()).as("Без вибору запит rows не потрібен").isNull();

        LocalDate today = new PlanAnalyticsPage(page).browserToday();
        wizard.selectFirstPlannableProduct();
        assertThat(wizard.isInsightsPeriodVisible()).isTrue();
        assertThat(page.getByText("В наявності", new com.microsoft.playwright.Page.GetByTextOptions()
                .setExact(true)).count()).as("Старий інвентарний блок видалено").isZero();
        assertThat(selectedIds(lastRowsUrl.get())).containsExactly(firstId.get());

        for (PeriodCase period : List.of(
                new PeriodCase("Місяць", 1), new PeriodCase("3 місяці", 3),
                new PeriodCase("Півроку", 6), new PeriodCase("Рік", 12))) {
            if (period.months() != 1) wizard.selectInsightsPeriod(period.label());
            page.waitForCondition(() -> lastRowsUrl.get() != null
                    && query(lastRowsUrl.get(), "fromDate").equals(
                            YearMonth.from(today).minusMonths(period.months()).atDay(1).toString())
                    && wizard.insightsMetricValue(0, "Вироблено").equals(String.valueOf(period.months() * 12)));
            assertThat(query(lastRowsUrl.get(), "toDate"))
                    .as("Останній день завершеного місяця")
                    .isEqualTo(YearMonth.from(today).minusMonths(1).atEndOfMonth().toString());
            if (period.months() > 1) {
                DateTimeFormatter display = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                String expectedRange = YearMonth.from(today).minusMonths(period.months())
                        .atDay(1).format(display) + " – "
                        + YearMonth.from(today).minusMonths(1).atEndOfMonth().format(display);
                assertThat(wizard.insightsRangeText()).isEqualTo(expectedRange);
            } else {
                assertThat(wizard.insightsRangeText()).as("Назва останнього завершеного місяця")
                        .contains(String.valueOf(YearMonth.from(today).minusMonths(1).getYear()));
            }
            assertThat(wizard.insightsMetricValue(0, "Вироблено")).isEqualTo(String.valueOf(period.months() * 12));
            assertThat(wizard.insightsMetricValue(0, "Відвантажено")).isEqualTo("6");
            assertThat(wizard.insightsMetricValue(0, "Використано")).isEqualTo("3");
            assertThat(wizard.insightsMetricValue(0, GlobalPlanWizardPage.STOCK_TOTAL_LABEL)).isEqualTo("18");
            assertThat(wizard.insightsMetricValue(0, GlobalPlanWizardPage.STOCK_ROOT_LABEL)).isEqualTo("9");
            assertAverage(wizard, "Вироблено", period.months() * 12, period.months());
            assertAverage(wizard, "Відвантажено", 6, period.months());
            assertAverage(wizard, "Використано", 3, period.months());
        }

        page.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions()
                .setName("Додати виріб")).click();
        wizard.selectFirstPlannableProductWithoutWaiting();
        page.waitForCondition(() -> lastRowsUrl.get() != null && selectedIds(lastRowsUrl.get()).size() == 2
                && wizard.insightsMetricValue(1, "Вироблено").equals("288"));
        assertThat(selectedIds(lastRowsUrl.get())).contains(firstId.get());
        assertThat(wizard.insightsMetricValue(0, "Вироблено")).isEqualTo("144");
        assertThat(wizard.insightsMetricValue(1, "Вироблено")).isEqualTo("288");
        assertThat(wizard.insightsMetricValue(1, "Відвантажено")).isEqualTo("7");
        assertThat(wizard.insightsMetricValue(1, "Використано")).isEqualTo("4");
        assertThat(wizard.insightsMetricValue(1, GlobalPlanWizardPage.STOCK_TOTAL_LABEL)).isEqualTo("30");
        assertThat(wizard.insightsMetricValue(1, GlobalPlanWizardPage.STOCK_ROOT_LABEL)).isEqualTo("12");

        wizard.openStockLocations(0, GlobalPlanWizardPage.STOCK_TOTAL_LABEL);
        Locator popover = page.locator("[data-radix-popper-content-wrapper]").last();
        popover.getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setName("Цукрарня"))
                .waitFor();
        assertThat(stockUrl.get()).endsWith("/api/v1/analytics/plan/stock/" + firstId.get());
        assertThat(popover.innerText()).contains("Цукрарня", "Інші локації", "Цех А", "Цех Б", "Склад В");
        assertThat(popover.getByRole(AriaRole.HEADING,
                new Locator.GetByRoleOptions().setName("Цукрарня"))
                .locator("xpath=parent::*").innerText()).contains("9");
        assertThat(popover.getByRole(AriaRole.HEADING,
                new Locator.GetByRoleOptions().setName("Інші локації"))
                .locator("xpath=parent::*").innerText()).contains("9");
    }

    @TestCaseId("TC-GP-UI-INSIGHTS-002")
    @Test
    @Story("Zero analytics values")
    @Severity(SeverityLevel.NORMAL)
    @Description("Якщо API не повертає рядок для виробу з нульовими показниками, всі картки показують тире.")
    public void missingAnalyticsRowShowsDashes() {
        page.route(ROWS_ROUTE, route -> fulfillJson(route, 200, analyticPage(List.of())));
        GlobalPlanWizardPage wizard = new GlobalPlanWizardPage(page).openCreate();
        wizard.selectFirstPlannableProduct();
        for (String label : LABELS) {
            assertThat(wizard.insightsMetricValue(0, label)).as(label).isEqualTo("—");
        }
        assertThat(page.getByRole(AriaRole.BUTTON,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Залишок усього: за локаціями"))
                .count()).isZero();
    }

    @TestCaseId("TC-GP-UI-INSIGHTS-003")
    @Test
    @Story("Insights loading and error do not block planning")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Поки rows очікує, є п'ять скелетонів; після HTTP 503 є повідомлення, форма лишається доступною.")
    public void loadingThenApiErrorLeavesFormUsable() {
        List<Route> held = new ArrayList<>();
        page.route(ROWS_ROUTE, held::add);
        GlobalPlanWizardPage wizard = new GlobalPlanWizardPage(page).openCreate();
        wizard.selectFirstPlannableProductWithoutWaiting();
        page.waitForCondition(() -> !held.isEmpty() && wizard.insightsSkeletonCount(0) == 5);
        assertThat(wizard.insightsSkeletonCount(0)).isEqualTo(5);
        fulfillJson(held.getFirst(), 503, "{}");
        page.getByText("Дані аналітики недоступні").waitFor();
        assertThat(page.getByText("Дані аналітики недоступні").isVisible()).isTrue();
        wizard.fillDescription("Планування доступне без аналітики");
        page.getByPlaceholder("Введіть кількість...").first().fill("5");
        assertThat(page.getByPlaceholder("Введіть кількість...").first().inputValue()).isEqualTo("5");
    }

    @TestCaseId("TC-GP-UI-INSIGHTS-004")
    @Test
    @Story("Insights on existing global plan")
    @Severity(SeverityLevel.NORMAL)
    @Description("На кроці 1 під час редагування плану показники також завантажуються для збереженого виробу.")
    public void editFormShowsInsightsForSavedProduct() {
        GlobalPlanFixture fixture = new GlobalPlanFixture(testContext, apiExecutor);
        fixture.prepareDecompositionChain();
        GlobalPlanResponse plan = fixture.createGlobalPlan(10);
        try {
            AtomicReference<String> rowsUrl = new AtomicReference<>();
            page.route(ROWS_ROUTE, route -> {
                rowsUrl.set(route.request().url());
                List<Map<String, Object>> rows = selectedIds(rowsUrl.get()).stream()
                        .map(id -> row(id, 12, 6, 3, 18, 9)).toList();
                fulfillJson(route, 200, analyticPage(rows));
            });
            GlobalPlanWizardPage wizard = new GlobalPlanWizardPage(page).openById(plan.getId());
            page.waitForCondition(() -> rowsUrl.get() != null
                    && wizard.insightsMetricValue(0, "Вироблено").equals("12"));
            assertThat(wizard.isInsightsPeriodVisible()).isTrue();
            assertThat(selectedIds(rowsUrl.get())).hasSize(1);
            assertThat(wizard.insightsMetricValue(0, GlobalPlanWizardPage.STOCK_TOTAL_LABEL)).isEqualTo("18");
        } finally {
            fixture.deleteGlobalPlan(plan.getId());
        }
    }

    @TestCaseId("TC-GP-UI-INSIGHTS-005")
    @Test
    @Story("Insights require analytics read permission")
    @Severity(SeverityLevel.NORMAL)
    @Description("У сесії з глобальним планом, але без analytics::read блок і запит rows відсутні. "
            + "Права моделюються на відповіді users/me, оскільки окремої тестової ролі немає.")
    public void missingAnalyticsPermissionHidesInsights() {
        AtomicInteger rowsRequests = new AtomicInteger();
        page.route(ROWS_ROUTE, route -> {
            rowsRequests.incrementAndGet();
            route.abort();
        });
        page.route("**/api/v1/users/me", route -> {
            APIResponse original = route.fetch();
            assertThat(original.status()).isEqualTo(200);
            ObjectNode session;
            try {
                session = (ObjectNode) JSON.readTree(original.text());
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Некоректний users/me", e);
            }
            ArrayNode permissions = JSON.createArrayNode();
            for (JsonNode permission : session.path("permissions")) {
                if (!permission.asText().startsWith("analytics::")) {
                    permissions.add(permission.asText());
                }
            }
            session.set("permissions", permissions);
            fulfillJson(route, 200, json(session));
        });

        GlobalPlanWizardPage wizard = new GlobalPlanWizardPage(page).openCreate();
        wizard.selectFirstPlannableProductWithoutWaiting();
        page.waitForTimeout(300);
        assertThat(wizard.isInsightsPeriodVisible()).isFalse();
        for (String label : LABELS) {
            assertThat(page.getByText(label, new com.microsoft.playwright.Page.GetByTextOptions()
                    .setExact(true)).count()).as("«%s» приховано", label).isZero();
        }
        assertThat(rowsRequests.get()).as("Без дозволу rows не запитується").isZero();
    }

    private record PeriodCase(String label, int months) {}

    private static void assertAverage(GlobalPlanWizardPage wizard, String label, int total, int months) {
        String tile = wizard.insightsMetricText(0, label);
        if (months == 1) {
            assertThat(tile).as("Середнє для одного місяця не потрібне").doesNotContain("/ міс");
            return;
        }
        String monthly = BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString().replace('.', ',');
        assertThat(tile).as("Середнє для «%s»", label).contains("≈ " + monthly + " / міс");
    }

    private static Map<String, Object> row(long id, int produced, int relocated,
                                           int used, int stockTotal, int stockInRoot) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("resourceId", id);
        row.put("resourceName", "Контрольний виріб " + id);
        row.put("unit", "шт");
        row.put("produced", produced);
        row.put("relocated", relocated);
        row.put("used", used);
        row.put("stockTotal", stockTotal);
        row.put("stockInRoot", stockInRoot);
        return row;
    }

    private static Map<String, Object> location(int id, String name, String parent, boolean inside, int amount) {
        return Map.of("storageId", id, "storageName", name, "parentName", parent,
                "insideRoot", inside, "amount", amount);
    }

    private static String analyticPage(List<Map<String, Object>> rows) {
        return json(Map.of("content", rows, "totals", List.of(),
                "page", Map.of("size", rows.size(), "number", 0,
                        "totalElements", rows.size(), "totalPages", rows.isEmpty() ? 0 : 1)));
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void fulfillJson(Route route, int status, String body) {
        route.fulfill(new Route.FulfillOptions().setStatus(status)
                .setContentType("application/json").setBody(body));
    }

    private static List<Long> selectedIds(String url) {
        if (url == null) return List.of();
        String raw = URI.create(url).getRawQuery();
        if (raw == null) return List.of();
        List<Long> ids = new ArrayList<>();
        for (String part : raw.split("&")) {
            String[] pair = part.split("=", 2);
            String name = URLDecoder.decode(pair[0], StandardCharsets.UTF_8).replace("[]", "");
            if (!name.equals("resourceIds") || pair.length != 2) continue;
            for (String id : URLDecoder.decode(pair[1], StandardCharsets.UTF_8).split(",")) {
                if (!id.isBlank()) ids.add(Long.parseLong(id));
            }
        }
        return ids;
    }

    private static String query(String url, String key) {
        String raw = URI.create(url).getRawQuery();
        if (raw == null) return null;
        for (String part : raw.split("&")) {
            String[] pair = part.split("=", 2);
            String name = URLDecoder.decode(pair[0], StandardCharsets.UTF_8).replace("[]", "");
            if (name.equals(key) && pair.length == 2) {
                return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
