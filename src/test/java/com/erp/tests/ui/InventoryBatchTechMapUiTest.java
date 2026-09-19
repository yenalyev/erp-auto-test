package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.pages.UnitManagementPage;
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
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Inventory")
@Feature("REQ-WMS-011 — Tech maps in the resource batches popup")
public class InventoryBatchTechMapUiTest extends BaseUITest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BATCHES_ROUTE = "**/api/v1/storage-items/batches**";
    private static final String INVENTORY_ROUTE = "**/api/v1/storages/inventory**";
    private static final long MOCK_RESOURCE_ID = 9_110_001L;
    private static final String RESOURCE_NAME = "WMS11 контрольний виріб";
    private static final String LONG_MAP_NAME =
            "Техкарта для надзвичайно довгої назви виробу з контрольованим переносом тексту";

    private long storageId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storageId = ConfigProvider.getOwner1StorageId();
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + storageId + "');");
    }

    @Test(priority = 10)
    @TestCaseId("TC-WMS-011-002")
    @Story("Tech map column and missing value")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            У попапі «Партії ресурсу» колонка «Техкарта» стоїть одразу після «Номер партії».
            Назва техкарти відображається в рядку партії та може переноситися; для партії без
            техкарти відображається «-». Оскільки є дві групи, над таблицею є відповідні чіпи
            із залишком та одиницею вимірювання ресурсу.
            """)
    public void batchDialogShowsTechMapColumnLongNameAndDash() {
        AtomicReference<String> payload = mockBatches(List.of(
                batch(101, 2, "WMS11-LONG", 11L, LONG_MAP_NAME),
                batch(102, 4, "WMS11-NONE", null, null)));

        UnitManagementPage stock = openBatchDialog("WMS11-LONG");

        assertThat(stock.isTechMapHeaderImmediatelyAfterBatchNumber())
                .as("«Техкарта» має йти одразу після «Номер партії»")
                .isTrue();
        assertThat(stock.getBatchTechMapText("WMS11-LONG")).isEqualTo(LONG_MAP_NAME);
        assertThat(stock.isBatchTechMapNameWrappable("WMS11-LONG"))
                .as("Довга назва техкарти має white-space: normal")
                .isTrue();
        assertThat(stock.getBatchTechMapText("WMS11-NONE")).isEqualTo("-");
        assertThat(stock.getTechMapChipTexts())
                .containsExactlyInAnyOrder(LONG_MAP_NAME + " (2 шт)", "- (4 шт)");

        stock.attachScreenshot("TC-WMS-011-002 — tech map column, long name and dash");
        assertThat(payload.get()).contains("WMS11-LONG");
    }

    @Test(priority = 20)
    @TestCaseId("TC-WMS-011-003")
    @Story("Tech map chips OR filter and reset")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Техкарти з різними id, але однаковою назвою, формують один чіп із сумарним
            залишком. Чіп показує назву, суму та одиницю вимірювання ресурсу.
            Один клік фільтрує, два активні чіпи працюють як OR, повторний клік знімає вибір.
            «-» фільтрує партії без техкарти. Після закриття і повторного відкриття фільтр порожній.
            """)
    public void techMapChipsFilterWithOrToggleAndResetAfterClose() {
        mockBatches(List.of(
                batch(201, 2, "WMS11-A1", 21L, "ТК Альфа"),
                batch(202, 3, "WMS11-A2", 23L, "ТК Альфа"),
                batch(203, 4, "WMS11-B1", 22L, "ТК Бета"),
                batch(204, 5, "WMS11-N1", null, null)));

        UnitManagementPage stock = openBatchDialog("WMS11-A1");
        assertThat(stock.getTechMapChipTexts())
                .containsExactlyInAnyOrder("ТК Альфа (5 шт)", "ТК Бета (4 шт)", "- (5 шт)");

        stock.clickTechMapChip("ТК Альфа (5 шт)");
        assertThat(stock.isTechMapChipSelected("ТК Альфа (5 шт)")).isTrue();
        assertThat(stock.getVisibleBatchNumbers()).containsExactly("WMS11-A1", "WMS11-A2");

        stock.clickTechMapChip("ТК Бета (4 шт)");
        assertThat(stock.isTechMapChipSelected("ТК Бета (4 шт)")).isTrue();
        assertThat(stock.getVisibleBatchNumbers())
                .containsExactly("WMS11-A1", "WMS11-A2", "WMS11-B1")
                .doesNotContain("WMS11-N1");
        stock.attachScreenshot("TC-WMS-011-003 — two tech map chips use OR");

        stock.clickTechMapChip("ТК Альфа (5 шт)");
        assertThat(stock.isTechMapChipSelected("ТК Альфа (5 шт)")).isFalse();
        assertThat(stock.getVisibleBatchNumbers()).containsExactly("WMS11-B1");

        stock.clickTechMapChip("- (5 шт)");
        assertThat(stock.getVisibleBatchNumbers()).containsExactly("WMS11-B1", "WMS11-N1");
        stock.clickTechMapChip("ТК Бета (4 шт)");
        assertThat(stock.getVisibleBatchNumbers()).containsExactly("WMS11-N1");
        stock.clickTechMapChip("- (5 шт)");
        assertThat(stock.getVisibleBatchNumbers())
                .containsExactly("WMS11-A1", "WMS11-A2", "WMS11-B1", "WMS11-N1");

        stock.clickTechMapChip("ТК Альфа (5 шт)").closeBatchDialog();
        stock.clickResourceAmountLink(RESOURCE_NAME).waitForBatchNumber("WMS11-A1");
        assertThat(stock.isTechMapChipSelected("ТК Альфа (5 шт)"))
                .as("Фільтр не зберігається після закриття попапа")
                .isFalse();
        assertThat(stock.getVisibleBatchNumbers())
                .containsExactly("WMS11-A1", "WMS11-A2", "WMS11-B1", "WMS11-N1");
        stock.attachScreenshot("TC-WMS-011-003 — filter reset after reopen");
    }

    @Test(priority = 30)
    @TestCaseId("TC-WMS-011-004")
    @Story("No redundant chip for one tech map group")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Якщо всі партії мають техкарти з однаковою назвою (навіть із різними id),
            чіпів немає. Якщо всі партії без техкарти, чіпів також немає; у колонці
            кожного рядка лишається «-».
            """)
    public void chipsHiddenForSingleTechMapOrAllWithoutTechMap() {
        AtomicReference<String> payload = mockBatches(List.of(
                batch(301, 2, "WMS11-ONE1", 31L, "Єдина ТК"),
                batch(302, 3, "WMS11-ONE2", 32L, "Єдина ТК")));

        UnitManagementPage stock = openBatchDialog("WMS11-ONE1");
        assertThat(stock.getTechMapChipTexts()).isEmpty();
        assertThat(stock.getBatchTechMapText("WMS11-ONE1")).isEqualTo("Єдина ТК");
        stock.closeBatchDialog();

        payload.set(json(List.of(
                batch(303, 4, "WMS11-EMPTY1", null, null),
                batch(304, 5, "WMS11-EMPTY2", null, null))));
        stock.clickResourceAmountLink(RESOURCE_NAME).waitForBatchNumber("WMS11-EMPTY1");
        assertThat(stock.getTechMapChipTexts()).isEmpty();
        assertThat(stock.getBatchTechMapText("WMS11-EMPTY1")).isEqualTo("-");
        assertThat(stock.getBatchTechMapText("WMS11-EMPTY2")).isEqualTo("-");
        stock.attachScreenshot("TC-WMS-011-004 — chips hidden for one grouping");
    }

    @Test(priority = 40)
    @TestCaseId("TC-WMS-011-005")
    @Story("Group different tech maps by the same UI name")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Дві техкарти з різними id та однаковою назвою утворюють один чіп. Його
            залишок дорівнює сумі партій обох техкарт, а клік показує партії обох id.
            """)
    public void techMapsWithSameNameAreGroupedIntoOneChip() {
        mockBatches(List.of(
                batch(401, 2, "WMS11-SAME1", 41L, "ТК Спільна назва"),
                batch(402, 3, "WMS11-SAME2", 42L, "ТК Спільна назва"),
                batch(403, 4, "WMS11-OTHER", 43L, "ТК Інша")));

        UnitManagementPage stock = openBatchDialog("WMS11-SAME1");
        List<String> chipTexts = stock.getTechMapChipTexts();
        assertThat(chipTexts).hasSize(2);
        assertThat(chipTexts.stream().filter(text -> text.startsWith("ТК Спільна назва (")).toList())
                .as("Різні id з однаковою назвою утворюють рівно один чіп")
                .singleElement()
                .isEqualTo("ТК Спільна назва (5 шт)");
        assertThat(chipTexts).contains("ТК Інша (4 шт)");

        stock.clickTechMapChipByName("ТК Спільна назва");
        assertThat(stock.getVisibleBatchNumbers())
                .containsExactly("WMS11-SAME1", "WMS11-SAME2")
                .doesNotContain("WMS11-OTHER");
        stock.attachScreenshot("TC-WMS-011-005 — same-name tech maps grouped by name");
    }

    private UnitManagementPage openBatchDialog(String expectedBatch) {
        UnitManagementPage stock = new UnitManagementPage(page)
                .openForStorage(storageId)
                .searchAndWaitForResource(RESOURCE_NAME, RESOURCE_NAME)
                .clickResourceAmountLink(RESOURCE_NAME)
                .waitForBatchNumber(expectedBatch);
        assertThat(stock.isBatchDialogVisible()).isTrue();
        return stock;
    }

    private AtomicReference<String> mockBatches(List<Map<String, Object>> batches) {
        mockInventoryRow();
        AtomicReference<String> payload = new AtomicReference<>(json(batches));
        page.route(BATCHES_ROUTE, route -> route.fulfill(new Route.FulfillOptions()
                .setStatus(200)
                .setContentType("application/json")
                .setBody(payload.get())));
        return payload;
    }

    private void mockInventoryRow() {
        page.route(INVENTORY_ROUTE, route -> {
            String url = route.request().url();
            Object response;
            if (url.contains("/tag-statistics")) {
                response = List.of();
            } else if (url.contains("/totals")) {
                response = List.of(Map.of("unit", "шт", "amount", 12));
            } else {
                response = Map.of(
                        "content", List.of(Map.of(
                                "resource", Map.of(
                                        "id", MOCK_RESOURCE_ID,
                                        "name", RESOURCE_NAME,
                                        "unit", Map.of("id", 1, "name", "Штука", "shortName", "шт"),
                                        "category", Map.of("id", 1, "name", "Тестова категорія"),
                                        "properties", List.of(),
                                        "imagePath", "",
                                        "updatedAt", "2026-09-18T08:00:00Z"),
                                "locations", List.of(Map.of(
                                        "storage", Map.of("id", storageId, "name", "Тестова локація"),
                                        "amount", 12,
                                        "bookedAmount", 0,
                                        "weight", 100,
                                        "alertLimit", 0)))),
                        "page", Map.of("size", 10, "number", 0, "totalElements", 1, "totalPages", 1));
            }
            route.fulfill(new Route.FulfillOptions()
                    .setStatus(200)
                    .setContentType("application/json")
                    .setBody(json(response)));
        });
    }

    private Map<String, Object> batch(long id,
                                      double amount,
                                      String batchNumber,
                                      Long techMapId,
                                      String techMapName) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id);
        value.put("amount", amount);
        value.put("date", "2026-09-18T08:00:00Z");
        value.put("batchNumber", batchNumber);
        value.put("isProduced", true);
        value.put("techmap", techMapId == null ? null : Map.of("id", techMapId, "name", techMapName));
        value.put("storage", Map.of("id", storageId, "name", "Тестова локація"));
        return value;
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize mocked batch response", e);
        }
    }
}
