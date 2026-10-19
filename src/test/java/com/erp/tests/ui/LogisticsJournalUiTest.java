package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.pages.LogisticsPage;
import com.erp.pages.RelocationPage;
import com.erp.utils.config.ConfigProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.Route;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** UI behavior of /logistics with stable read-only responses; role access is tested separately. */
@Epic("Relocation")
@Feature("Logistics journal — filters and export")
public class LogisticsJournalUiTest extends BaseUITest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long A = 910001;
    private static final long B = 910002;
    private static final long C = 910003;
    private static final long D = 910004;
    private static final String A_NAME = "Логістика тест A";
    private static final String B_NAME = "Логістика тест B";
    private static final String C_NAME = "Логістика тест C";
    private static final String D_NAME = "Логістика тест D";
    private static final String PRODUCT = "Тестовий кабель логістики";
    private static final String OTHER_PRODUCT = "Тестовий акумулятор логістики";
    private static final String ACTIVE_AC = "logistics-active-A-C";
    private static final String ACTIVE_AD = "logistics-active-A-D";
    private static final String ACTIVE_BC = "logistics-active-B-C";
    private static final String CANCELLED = "logistics-cancelled-A-C";
    private static final String FINISHED = "logistics-finished-A-C";
    private static final String AUTO_FINISHED = "logistics-auto-finished-B-D";
    private static final String RETURNED = "logistics-returned-B-C";
    private static final String LOST = "logistics-lost-A-C";

    @Test
    @TestCaseId("TC-LOG-UI-004")
    @Severity(SeverityLevel.CRITICAL)
    @Description("LOGIST sees CREATED/CANCELLED in transit and finished/returned records in history")
    public void logistSeesBothGlobalTabs() {
        loginAndMock(UserRole.LOGIST);
        assertTabsAndStates(new LogisticsPage(page).open());
    }

    @Test
    @TestCaseId("TC-LOG-UI-005")
    @Severity(SeverityLevel.CRITICAL)
    @Description("ADMIN sees the same two logistics tabs and state split")
    public void adminSeesBothGlobalTabs() {
        loginAndMock(UserRole.ADMIN);
        assertTabsAndStates(new LogisticsPage(page).open());
    }

    @Test
    @TestCaseId("TC-LOG-UI-006")
    @Severity(SeverityLevel.CRITICAL)
    @Description("LOGIST combines sender and receiver with AND; clearing restores all rows")
    public void logistFiltersByBothLocations() {
        loginAndMock(UserRole.LOGIST);
        LogisticsPage journal = new LogisticsPage(page).open();
        Response sender = waitForJournal(() -> journal.selectLocation("Відправник", A_NAME));
        assertThat(query(sender.url())).containsEntry("senderIds", List.of(String.valueOf(A)));
        Response receiver = waitForJournal(() -> journal.selectLocation("Отримувач", C_NAME));
        assertThat(query(receiver.url()))
                .containsEntry("senderIds", List.of(String.valueOf(A)))
                .containsEntry("receiverIds", List.of(String.valueOf(C)))
                .containsEntry("isOr", List.of("false"));
        waitForRow(journal, ACTIVE_AC);
        assertThat(journal.hasRowContaining(ACTIVE_AD)).isFalse();
        assertThat(journal.hasRowContaining(ACTIVE_BC)).isFalse();

        Response cleared = waitForJournal(journal::clearFilters);
        assertThat(query(cleared.url())).doesNotContainKeys("senderIds", "receiverIds");
        waitForRow(journal, ACTIVE_BC);
    }

    @Test
    @TestCaseId("TC-LOG-UI-007")
    @Severity(SeverityLevel.NORMAL)
    @Description("ADMIN product search filters after debounce; Reset removes productName")
    public void adminSearchesAndClearsProductName() {
        loginAndMock(UserRole.ADMIN);
        LogisticsPage journal = new LogisticsPage(page).open();
        Response searched = waitForJournal(() -> journal.searchProduct("кабель"));
        assertThat(query(searched.url())).containsEntry("productName", List.of("кабель"));
        waitForRow(journal, ACTIVE_AC);
        assertThat(journal.hasRowContaining(ACTIVE_AD)).isFalse();

        Response empty = waitForJournal(() -> journal.searchProduct("немає-такого-ресурсу"));
        assertThat(query(empty.url())).containsKey("productName");
        page.getByText("Немає записів").waitFor();

        Response cleared = waitForJournal(journal::clearFilters);
        assertThat(query(cleared.url())).doesNotContainKey("productName");
        waitForRow(journal, ACTIVE_AD);
    }

    @Test
    @TestCaseId("TC-LOG-UI-008")
    @Severity(SeverityLevel.NORMAL)
    @Description("LOGIST column choices are separate for the two table IDs")
    public void logistControlsColumns() {
        loginAndMock(UserRole.LOGIST);
        LogisticsPage journal = new LogisticsPage(page).open();
        assertThat(journal.hasHeader("Примітки")).isTrue();
        journal.setColumnVisible("Примітки", false);
        assertThat(journal.hasHeader("Примітки")).isFalse();

        journal.openHistory();
        assertThat(journal.hasHeader("Примітки")).isTrue();
        assertThat(journal.hasHeader("Отримано")).isFalse();
        journal.setColumnVisible("Отримано", true);
        assertThat(journal.hasHeader("Отримано")).isTrue();

        journal.openActive();
        assertThat(journal.hasHeader("Примітки")).isFalse();
    }

    @Test
    @TestCaseId("TC-LOG-UI-009")
    @Severity(SeverityLevel.CRITICAL)
    @Description("LOGIST export passes current tab states and location filters")
    public void logistExportsBothTabs() {
        assertExports(UserRole.LOGIST);
    }

    @Test
    @TestCaseId("TC-LOG-UI-010")
    @Severity(SeverityLevel.CRITICAL)
    @Description("ADMIN export passes current tab states and location filters")
    public void adminExportsBothTabs() {
        assertExports(UserRole.ADMIN);
    }

    @Test
    @TestCaseId("TC-LOG-UI-011")
    @Severity(SeverityLevel.NORMAL)
    @Description("ADMIN sorting is sent to the backend; column visibility persists after reload")
    public void adminSortsAndPersistsColumns() {
        loginAndMock(UserRole.ADMIN);
        LogisticsPage journal = new LogisticsPage(page).open();
        Response sorted = waitForJournal(() -> journal.sortBy("Надіслано"));
        assertThat(query(sorted.url()).get("sort")).contains("dateTime,ASC");
        journal.setColumnVisible("Примітки", false);
        page.reload();
        journal.waitForLoaded();
        assertThat(journal.hasHeader("Примітки")).isFalse();
    }

    @Test
    @TestCaseId("TC-LOG-UI-012")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Mocked read-only journal: totals are grouped by unit across all filtered relocations; active and history use their own states")
    public void logisticsTotalsFollowTabsAndFilters() {
        loginAndMock(UserRole.ADMIN);
        Response active = waitForTotals(() -> new LogisticsPage(page).open());
        assertThat(query(active.url()).get("states")).containsExactly("CREATED", "CANCELLED");
        assertTotal("шт", "4");
        assertTotal("кг", "6");

        LogisticsPage journal = new LogisticsPage(page);
        Response filtered = waitForTotals(() -> journal.selectLocation("Від кого", A_NAME));
        assertThat(query(filtered.url()).get("fromIds")).containsExactly(String.valueOf(A));
        assertTotal("шт", "1");
        assertTotal("кг", "6");

        Response narrowed = waitForTotals(() -> journal.selectLocation("Кому", C_NAME));
        assertThat(query(narrowed.url()))
                .containsEntry("fromIds", List.of(String.valueOf(A)))
                .containsEntry("toIds", List.of(String.valueOf(C)));
        assertTotal("шт", "1");
        assertTotal("кг", "4");

        Response history = waitForTotals(journal::openHistory);
        assertThat(query(history.url()).get("states"))
                .containsExactly("RETURNED", "FINISHED", "AUTO_FINISHED");
        assertThat(query(history.url()).get("fromIds")).containsExactly(String.valueOf(A));
        assertThat(query(history.url()).get("toIds")).containsExactly(String.valueOf(C));
        assertTotal("шт", "5");
        assertThat(page.locator("span[title='кг']").count()).isZero();
        journal.attachScreenshot("TC-LOG-UI-012 — filtered history totals");
    }

    @Test
    @TestCaseId("TC-LOG-UI-013")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Mocked read-only journal: /relocations totals use the active, received and sent tab states and render separate units")
    public void relocationTotalsFollowTabs() {
        loginAndMock(UserRole.ADMIN);
        Response active = waitForTotals(() -> new RelocationPage(page).open());
        assertThat(query(active.url()).get("states")).containsExactly("CREATED", "CANCELLED");
        assertTotal("шт", "4");
        assertTotal("кг", "6");

        RelocationPage journal = new RelocationPage(page);
        Response received = waitForTotals(journal::openReceivedTab);
        assertThat(query(received.url()).get("states"))
                .containsExactly("RETURNED", "FINISHED", "AUTO_FINISHED");
        assertTotal("шт", "12");
        assertTotal("кг", "6");

        Response sent = waitForTotals(journal::openSentTab);
        assertThat(query(sent.url()).get("states"))
                .containsExactly("RETURNED", "FINISHED", "AUTO_FINISHED");
        assertTotal("шт", "12");
        assertTotal("кг", "6");
        journal.attachScreenshot("TC-LOG-UI-013 — sent totals by unit");
    }

    private void assertTabsAndStates(LogisticsPage journal) {
        waitForRow(journal, ACTIVE_AC);
        assertThat(journal.hasRowContaining(ACTIVE_BC)).isTrue();
        assertThat(journal.hasRowContaining(CANCELLED)).isTrue();
        assertThat(journal.hasRowContaining(FINISHED)).isFalse();
        assertThat(journal.hasRowContaining(LOST)).isFalse();
        assertThat(journal.hasHeader("Від")).isTrue();
        assertThat(journal.hasHeader("До")).isTrue();

        journal.openHistory();
        waitForRow(journal, FINISHED);
        assertThat(journal.hasRowContaining(AUTO_FINISHED)).isTrue();
        assertThat(journal.hasRowContaining(RETURNED)).isTrue();
        assertThat(journal.hasRowContaining(ACTIVE_AC)).isFalse();
        assertThat(journal.hasRowContaining(LOST)).isFalse();
        assertThat(journal.hasHeader("Статус")).isTrue();
    }

    private void assertExports(UserRole role) {
        AtomicReference<String> exportUrl = new AtomicReference<>();
        loginAndMock(role);
        page.onRequest(request -> {
            if (request.url().contains("/api/v1/relocations/export")) {
                exportUrl.set(request.url());
            }
        });
        LogisticsPage journal = new LogisticsPage(page).open();
        waitForJournal(() -> journal.selectLocation("Відправник", A_NAME));
        waitForJournal(() -> journal.selectLocation("Отримувач", C_NAME));

        LogisticsPage.ExportedFile active = journal.export();
        assertThat(active.filename()).endsWith(".xlsx");
        assertThat(active.sizeBytes()).isGreaterThan(0);
        assertThat(query(exportUrl.get()))
                .containsEntry("states", List.of("CREATED", "CANCELLED"))
                .containsEntry("senderIds", List.of(String.valueOf(A)))
                .containsEntry("receiverIds", List.of(String.valueOf(C)))
                .containsEntry("isOr", List.of("false"));

        journal.openHistory();
        LogisticsPage.ExportedFile history = journal.export();
        assertThat(history.sizeBytes()).isGreaterThan(0);
        assertThat(query(exportUrl.get()).get("states"))
                .containsExactly("RETURNED", "FINISHED", "AUTO_FINISHED");
    }

    private void loginAndMock(UserRole role) {
        browserContext.clearCookies();
        injectSessionCookies(cachedSessionCookies(role), sessionCookieDomain());
        page.addInitScript("""
                localStorage.setItem('selectedStorageId', 'all');
                if (!sessionStorage.getItem('logistics-test-initialized')) {
                    localStorage.setItem('pageSize_logistics-relocations-active', '50');
                    localStorage.setItem('pageSize_logistics-relocations-history', '50');
                    localStorage.removeItem('hiddenColumns_logistics-relocations-active');
                    localStorage.removeItem('hiddenColumns_logistics-relocations-history');
                    localStorage.removeItem('sorting_logistics-relocations-active');
                    localStorage.removeItem('sorting_logistics-relocations-history');
                    localStorage.removeItem('date-range-picker:default-preset:logistics');
                    sessionStorage.setItem('logistics-test-initialized', '1');
                }
                """);
        page.route("**/api/v1/storages/names*", route -> route.fulfill(new Route.FulfillOptions()
                .setContentType("application/json")
                .setBody(json(List.of(storage(A, A_NAME), storage(B, B_NAME),
                        storage(C, C_NAME), storage(D, D_NAME))))));
        page.route("**/api/v1/storages/hierarchy*", route -> route.fulfill(new Route.FulfillOptions()
                .setContentType("application/json")
                .setBody(json(List.of(storage(A, A_NAME), storage(B, B_NAME),
                        storage(C, C_NAME), storage(D, D_NAME))))));
        page.route("**/api/v1/relocations?**", this::fulfillJournal);
        page.route("**/api/v1/relocations/totals?**", this::fulfillTotals);
        page.route("**/api/v1/relocations/export?**", route -> route.fulfill(
                new Route.FulfillOptions()
                        .setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .setBodyBytes(workbookBytes())));
    }

    private void fulfillJournal(Route route) {
        Map<String, List<String>> params = query(route.request().url());
        List<Map<String, Object>> matches = new ArrayList<>(sampleRows());
        List<String> states = params.get("states");
        if (states != null) matches.removeIf(row -> !states.contains(row.get("state")));
        List<String> senders = params.getOrDefault("fromIds", params.get("senderIds"));
        if (senders != null) matches.removeIf(row -> !senders.contains(String.valueOf(locationId(row, "sender"))));
        List<String> receivers = params.getOrDefault("toIds", params.get("receiverIds"));
        if (receivers != null) matches.removeIf(row -> !receivers.contains(String.valueOf(locationId(row, "recipient"))));
        String productName = params.getOrDefault("productName", List.of("")).getFirst().toLowerCase();
        if (!productName.isBlank()) {
            matches.removeIf(row -> !String.valueOf(row.get("testProduct")).toLowerCase().contains(productName));
        }
        matches.forEach(row -> row.remove("testProduct"));
        route.fulfill(new Route.FulfillOptions().setContentType("application/json")
                .setBody(json(Map.of("content", matches,
                        "page", Map.of("size", 50, "number", 0, "totalElements", matches.size(),
                                "totalPages", 1)))));
    }

    private void fulfillTotals(Route route) {
        Map<String, List<String>> params = query(route.request().url());
        List<Map<String, Object>> matches = matchingRows(params);
        Map<String, Integer> units = new LinkedHashMap<>();
        for (Map<String, Object> row : matches) {
            @SuppressWarnings("unchecked")
            Map<String, Object> item = ((List<Map<String, Object>>) row.get("items")).getFirst();
            @SuppressWarnings("unchecked")
            Map<String, Object> resource = (Map<String, Object>) item.get("resource");
            @SuppressWarnings("unchecked")
            String unit = (String) ((Map<String, Object>) resource.get("unit")).get("shortName");
            units.merge(unit, ((Number) item.get("amount")).intValue(), Integer::sum);
        }
        List<Map<String, Object>> totals = units.entrySet().stream()
                .map(entry -> Map.<String, Object>of("unit", entry.getKey(), "amount", entry.getValue()))
                .toList();
        route.fulfill(new Route.FulfillOptions().setContentType("application/json")
                .setBody(json(Map.of("units", totals, "equipmentCount", 0))));
    }

    private static List<Map<String, Object>> matchingRows(Map<String, List<String>> params) {
        List<Map<String, Object>> matches = new ArrayList<>(sampleRows());
        List<String> states = params.get("states");
        if (states != null) matches.removeIf(row -> !states.contains(row.get("state")));
        List<String> senders = params.getOrDefault("fromIds", params.get("senderIds"));
        if (senders != null) matches.removeIf(row -> !senders.contains(String.valueOf(locationId(row, "sender"))));
        List<String> receivers = params.getOrDefault("toIds", params.get("receiverIds"));
        if (receivers != null) matches.removeIf(row -> !receivers.contains(String.valueOf(locationId(row, "recipient"))));
        String productName = params.getOrDefault("productName", List.of("")).getFirst().toLowerCase();
        if (!productName.isBlank()) {
            matches.removeIf(row -> !String.valueOf(row.get("testProduct")).toLowerCase().contains(productName));
        }
        return matches;
    }

    private static List<Map<String, Object>> sampleRows() {
        String today = LocalDate.now(ZoneId.of("Europe/Kyiv")).toString();
        return List.of(
                row(1, "CREATED", A, A_NAME, C, C_NAME, ACTIVE_AC, PRODUCT, today),
                row(2, "CREATED", A, A_NAME, D, D_NAME, ACTIVE_AD, OTHER_PRODUCT, today),
                row(3, "CREATED", B, B_NAME, C, C_NAME, ACTIVE_BC, PRODUCT, today),
                row(4, "CANCELLED", A, A_NAME, C, C_NAME, CANCELLED, PRODUCT, today),
                row(5, "FINISHED", A, A_NAME, C, C_NAME, FINISHED, PRODUCT, today),
                row(6, "AUTO_FINISHED", B, B_NAME, D, D_NAME, AUTO_FINISHED, OTHER_PRODUCT, today),
                row(7, "RETURNED", B, B_NAME, C, C_NAME, RETURNED, PRODUCT, today),
                row(8, "LOST", A, A_NAME, C, C_NAME, LOST, PRODUCT, today));
    }

    private static Map<String, Object> row(long id, String state, long senderId, String senderName,
                                            long recipientId, String recipientName, String marker,
                                            String product, String date) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("state", state);
        row.put("type", "RESOURCE");
        row.put("description", marker);
        row.put("invoiceNumber", "LOG-" + id);
        row.put("sender", storage(senderId, senderName));
        row.put("recipient", storage(recipientId, recipientName));
        row.put("items", List.of(Map.of("resource", Map.of("id", id, "name", product,
                "unit", Map.of("shortName", id % 2 == 0 ? "кг" : "шт")),
                "amount", id, "batches", List.of())));
        row.put("equipmentItems", List.of());
        row.put("date", date + "T12:00:00Z");
        row.put("receivedAt", date + "T13:00:00Z");
        row.put("canGenerateInvoice", false);
        row.put("hasExternalInvoicePhoto", false);
        row.put("isPaidByCash", false);
        row.put("paidAmount", 0);
        row.put("testProduct", product);
        return row;
    }

    private static Map<String, Object> storage(long id, String name) {
        return Map.of("id", id, "name", name, "kind", "STORAGE");
    }

    @SuppressWarnings("unchecked")
    private static long locationId(Map<String, Object> row, String side) {
        return ((Number) ((Map<String, Object>) row.get(side)).get("id")).longValue();
    }

    private static byte[] workbookBytes() {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            book.createSheet("Переміщення").createRow(0).createCell(0).setCellValue("Тестовий експорт");
            book.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create mock Excel export", e);
        }
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize logistics fixture", e);
        }
    }

    private static Map<String, List<String>> query(String url) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        String raw = URI.create(url).getRawQuery();
        if (raw == null || raw.isBlank()) return params;
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            params.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }
        return params;
    }

    private Response waitForJournal(Runnable action) {
        return page.waitForResponse(response -> response.url().contains("/api/v1/relocations?")
                && "GET".equals(response.request().method()), action);
    }

    private Response waitForTotals(Runnable action) {
        return page.waitForResponse(response -> response.url().contains("/api/v1/relocations/totals?")
                && "GET".equals(response.request().method()), action);
    }

    private void assertTotal(String unit, String amount) {
        String selector = "span[title='" + unit + "'] > span:first-child";
        page.waitForCondition(() -> page.locator(selector).count() > 0
                        && page.locator(selector).first().innerText().trim().equals(amount),
                new com.microsoft.playwright.Page.WaitForConditionOptions()
                        .setTimeout(ConfigProvider.getUiTimeoutSeconds() * 1000L));
        assertThat(page.locator(selector).first().innerText().trim()).isEqualTo(amount);
    }

    private void waitForRow(LogisticsPage journal, String marker) {
        page.waitForCondition(() -> journal.hasRowContaining(marker),
                new com.microsoft.playwright.Page.WaitForConditionOptions()
                        .setTimeout(ConfigProvider.getUiTimeoutSeconds() * 1000L));
    }
}
