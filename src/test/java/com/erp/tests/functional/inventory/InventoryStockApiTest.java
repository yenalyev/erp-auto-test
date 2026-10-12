package com.erp.tests.functional.inventory;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.MultiLocationStorageItemResponse;
import com.erp.models.response.ProductionProcessTagStatisticResponse;
import com.erp.models.response.StorageItemResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.ResourceCategoryResponse;
import com.erp.utils.helpers.XlsxContentAssertions;
import com.erp.utils.helpers.XlsxWorkbookReader;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Inventory")
@Feature("REQ-WMS-007 Stock")
public class InventoryStockApiTest extends InventoryApiTestBase {

    @Test(priority = 10)
    @TestCaseId("TC-WMS-007-001")
    @Story("Roles can access inventory list API")
    @Severity(SeverityLevel.NORMAL)
    public void ownerAndAdminCanAccessStockList() {
        for (UserRole role : List.of(UserRole.OWNER_1, UserRole.ADMIN)) {
            List<StorageItemResponse> items = inventoryFixture.listItems(owner1StorageId, role);
            assertThat(items).isNotNull();
        }
        log.info("TC-WMS-007-001: Audit role skipped — no credentials in test config");
    }

    @Test(priority = 20)
    @TestCaseId("TC-WMS-007-002")
    @Story("View stock on selected storage")
    @Severity(SeverityLevel.NORMAL)
    public void viewStockOnStorage() {
        StorageItemResponse item = requireAnchorItem();
        assertThat(item.getResource()).isNotNull();
        assertThat(item.getAmount()).isGreaterThan(0);
        assertThat(item.getResource().getName()).isNotBlank();
    }

    @Test(priority = 30)
    @TestCaseId("TC-WMS-007-003")
    @Story("Filter stock by search term")
    @Severity(SeverityLevel.NORMAL)
    public void filterStockBySearchTerm() {
        StorageItemResponse anchor = requireAnchorItem();
        String searchTerm = anchor.getResource().getName().trim();
        long resourceId = anchor.getResource().getId();

        List<StorageItemResponse> filtered = inventoryFixture.listItems(
                owner1StorageId, UserRole.OWNER_1, Map.of("searchTerm", searchTerm));
        assertThat(filtered).isNotEmpty();
        assertThat(filtered.stream().anyMatch(i ->
                resourceId == i.getResource().getId())).isTrue();
    }

    @Test(priority = 40)
    @TestCaseId("TC-WMS-007-004")
    @Story("Multi-location inventory aggregate")
    @Severity(SeverityLevel.NORMAL)
    public void multiLocationInventoryView() {
        String locations = owner1StorageId + "," + owner2StorageId;
        Response response = inventoryFixture.getMultiLocationInventory(UserRole.ADMIN, locations);
        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.STORAGE_INVENTORY_MULTI_GET);
        List<?> content = response.jsonPath().getList("content");
        assertThat(content).isNotNull();
    }

    @Test(priority = 50)
    @TestCaseId("TC-WMS-007-006")
    @Story("Export remainders XLSX")
    @Severity(SeverityLevel.NORMAL)
    public void exportRemaindersExcel() {
        Response response = inventoryFixture.exportRemainders(owner1StorageId, UserRole.OWNER_1);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.getContentType()).contains("octet-stream");
        assertThat(response.asByteArray().length).isGreaterThan(100);
    }

    @Test(priority = 55)
    @TestCaseId("TC-WMS-007-020")
    @Story("Export remainders XLSX with and without zero stock")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            GET /export-analytics/inventory?parentStorageId= (як кнопка «Експорт в Excel» на /inventory).
            Arrange: унікальний ресурс з залишком і унікальний вичерпаний StorageItem (amount=0).
            Без showZeroStock (default false) — у XLSX є лише ненуль; з showZeroStock=true — обидва.
            Колонки кількості числові, одиниця виміру окрема («Одиниця Виміру»).
            """)
    public void exportExcelWithAndWithoutZeroStock() {
        String prefix = "InvExpZ_" + System.currentTimeMillis() + "_";
        ResourceResponse plus = inventoryFixture.createUniqueCatalogResourceAbsentFromStorage(
                owner1StorageId, UserRole.ADMIN, prefix + "p_");
        ResourceResponse zero = inventoryFixture.createUniqueCatalogResourceAbsentFromStorage(
                owner1StorageId, UserRole.ADMIN, prefix + "z_");
        trackStorageResourceForCleanup(plus.getId());
        trackStorageResourceForCleanup(zero.getId());

        relocationFixture.ensureStock(owner1StorageId, plus.getId(), 7.0);
        relocationFixture.ensureStock(owner1StorageId, zero.getId(), 3.0);
        inventoryFixture.requireItemForResourceWithRetry(
                owner1StorageId, plus.getId(), UserRole.ADMIN, 15_000);
        inventoryFixture.depleteToZero(owner1StorageId, zero.getId());

        Allure.parameter("plusResource", plus.getName());
        Allure.parameter("zeroResource", zero.getName());
        Allure.parameter("searchTerm", prefix);

        Allure.step("Експорт без нулів — є ненуль, немає вичерпаного", () -> {
            Response response = inventoryFixture.exportRemaindersHierarchy(
                    UserRole.OWNER_1, owner1StorageId, Map.of(
                            "searchTerm", prefix,
                            "showZeroStock", false));
            assertThat(response.statusCode()).isEqualTo(200);
            byte[] xlsx = response.asByteArray();
            assertThat(xlsx.length).isGreaterThan(100);
            assertThat(XlsxContentAssertions.zipContainsText(xlsx, "Одиниця Виміру"))
                    .as("XLSX має колонку одиниці виміру")
                    .isTrue();
            assertThat(XlsxContentAssertions.zipContainsText(xlsx, plus.getName()))
                    .as("Без нулів XLSX містить ресурс з залишком")
                    .isTrue();
            assertThat(XlsxContentAssertions.zipContainsAmount(xlsx, 7.0))
                    .as("Без нулів XLSX містить кількість ненульового ресурсу")
                    .isTrue();
            assertThat(XlsxContentAssertions.zipContainsText(xlsx, zero.getName()))
                    .as("Без нулів XLSX не містить вичерпаний ресурс")
                    .isFalse();
        });

        Allure.step("Експорт з нулями — обидва ресурси", () -> {
            Response response = inventoryFixture.exportRemaindersHierarchy(
                    UserRole.OWNER_1, owner1StorageId, Map.of(
                            "searchTerm", prefix,
                            "showZeroStock", true));
            assertThat(response.statusCode()).isEqualTo(200);
            byte[] xlsx = response.asByteArray();
            assertThat(xlsx.length).isGreaterThan(100);
            assertThat(XlsxContentAssertions.zipContainsText(xlsx, plus.getName()))
                    .as("З нулями XLSX містить ресурс з залишком")
                    .isTrue();
            assertThat(XlsxContentAssertions.zipContainsText(xlsx, zero.getName()))
                    .as("З нулями XLSX містить вичерпаний ресурс")
                    .isTrue();
            assertWorkbookResourceAmount(xlsx, "Залишок", zero.getName(), 1, 2, "0");
            assertWorkbookResourceAmount(xlsx, "По складах", zero.getName(), 0, 1, "0");
        });
    }

    private static void assertWorkbookResourceAmount(byte[] xlsx, String sheetName,
                                                     String resourceName, int nameColumn,
                                                     int amountColumn, String expectedAmount) {
        List<List<String>> rows = XlsxWorkbookReader.sheets(xlsx).get(sheetName);
        assertThat(rows).as("Аркуш %s у книзі залишків", sheetName).isNotNull();
        List<String> resourceRow = rows.stream().skip(1)
                .filter(row -> row.size() > amountColumn && resourceName.equals(row.get(nameColumn)))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "Аркуш " + sheetName + " не містить ресурс " + resourceName));
        assertThat(new java.math.BigDecimal(resourceRow.get(amountColumn)))
                .as("Кількість ресурсу %s на аркуші %s", resourceName, sheetName)
                .isEqualByComparingTo(expectedAmount);
    }

    @Test(priority = 56)
    @TestCaseId("TC-WMS-007-025")
    @Story("Per-storage Excel follows search, category, tag and zero-stock filters")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Обидва аркуші Excel відповідають пошуку, вибраній категорії, тегу та "
            + "перемикачу нульових залишків: у файлі є потрібні ресурси й немає зайвих.")
    public void perStorageExcelUsesCurrentFiltersAndZeroStockMode() {
        InventoryFixture.TagOrFilterSeed tagged =
                inventoryFixture.seedTagOrFilterResources(owner1StorageId, relocationFixture);
        for (ResourceResponse resource : List.of(
                tagged.resourceA(), tagged.resourceB(), tagged.resourceC())) {
            trackStorageResourceForCleanup(resource.getId());
        }
        ResourceFixture resources = new ResourceFixture(testContext, apiExecutor);
        List<ResourceCategoryResponse> categories = apiExecutor.execute(
                        ApiEndpointDefinition.RESOURCE_CATEGORY_GET_ALL, UserRole.ADMIN)
                .jsonPath().getList("", ResourceCategoryResponse.class);
        Long selectedCategory = tagged.resourceA().getCategory().getId();
        Long otherCategory = categories.stream().map(ResourceCategoryResponse::getId)
                .filter(id -> !id.equals(selectedCategory)).findFirst().orElseThrow();
        ResourceResponse other = resources.createUniqueResource("inv-excel-other-category-", otherCategory);
        trackStorageResourceForCleanup(other.getId());
        relocationFixture.seedExactStock(owner1StorageId, other.getId(), 5.0);
        ResourceResponse depleted = resources.createUniqueResource("inv-excel-zero-");
        trackStorageResourceForCleanup(depleted.getId());
        relocationFixture.seedExactStock(owner1StorageId, depleted.getId(), 3.0);
        inventoryFixture.depleteToZero(owner1StorageId, depleted.getId());

        assertWorkbookResourceSelection(
                inventoryFixture.exportRemaindersHierarchy(UserRole.ADMIN, owner1StorageId,
                        Map.of("searchTerm", tagged.resourceA().getName())),
                tagged.resourceA().getName(), tagged.resourceB().getName());
        assertWorkbookResourceSelection(
                inventoryFixture.exportRemaindersHierarchy(UserRole.ADMIN, owner1StorageId,
                        Map.of("categories", List.of(selectedCategory))),
                tagged.resourceA().getName(), other.getName());
        assertWorkbookResourceSelection(
                inventoryFixture.exportRemaindersHierarchy(UserRole.ADMIN, owner1StorageId,
                        Map.of("tags", List.of(tagged.tagA()))),
                tagged.resourceA().getName(), tagged.resourceB().getName());
        assertWorkbookResourceSelection(
                inventoryFixture.exportRemaindersHierarchy(UserRole.ADMIN, owner1StorageId,
                        Map.of("searchTerm", depleted.getName(), "showZeroStock", true)),
                depleted.getName(), tagged.resourceA().getName());

        Response withoutZeros = inventoryFixture.exportRemaindersHierarchy(
                UserRole.ADMIN, owner1StorageId,
                Map.of("searchTerm", depleted.getName(), "showZeroStock", false));
        assertThat(withoutZeros.statusCode()).isEqualTo(200);
        Map<String, List<List<String>>> workbook = XlsxWorkbookReader.sheets(withoutZeros.asByteArray());
        for (String sheet : List.of("Залишок", "По складах")) {
            assertThat(workbook.get(sheet)).as("Вичерпаний ресурс без показу нулів: %s", sheet)
                    .noneMatch(row -> row.contains(depleted.getName()));
        }
    }

    private static void assertWorkbookResourceSelection(
            Response response, String expectedResource, String excludedResource) {
        assertThat(response.statusCode()).isEqualTo(200);
        Map<String, List<List<String>>> workbook = XlsxWorkbookReader.sheets(response.asByteArray());
        for (String sheet : List.of("Залишок", "По складах")) {
            assertThat(workbook.get(sheet)).as("Аркуш %s", sheet).isNotNull();
            assertThat(workbook.get(sheet))
                    .as("Фільтри Excel на аркуші %s", sheet)
                    .anyMatch(row -> row.contains(expectedResource))
                    .noneMatch(row -> row.contains(excludedResource));
        }
    }

    @Test(priority = 60)
    @TestCaseId("TC-WMS-007-007")
    @Story("Inventory operations in history")
    @Severity(SeverityLevel.NORMAL)
    public void inventoryRecordedInOperationHistory() {
        inventoryFixture.openSession(owner1StorageId);
        double before = inventoryFixture.getResourceStock(owner1StorageId, anchorResourceId, UserRole.ADMIN);
        inventoryFixture.setResourceAmount(owner1StorageId, UserRole.ADMIN, anchorResourceId, before + 3.0);

        Response history = inventoryFixture.getOperationHistoryToday(owner1StorageId, UserRole.ADMIN);
        if (history.statusCode() == 403) {
            throw new SkipException("Current role lacks resource-operation-history read permission");
        }
        assertThat(history.statusCode()).isEqualTo(200);
        String body = history.getBody().asString();
        assertThat(body).containsAnyOf("ADDED_INV", "REMOVED_INV");
    }

    @Test(priority = 70)
    @TestCaseId("TC-WMS-007-018")
    @Story("Inventory tag chips filter with OR")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            GET /storages/inventory?parentStorageId=&tags=tagA,tagB (як UI інфочіпи на /inventory)
            повертає union (OR): ресурси з tagA або tagB. Ресурс без цих тегів відсутній.
            Один тег звужує список лише до відповідного ресурсу (не AND між чіпами).
            """)
    public void tagFilterTwoTagsUsesOrNotAnd() {
        InventoryFixture.TagOrFilterSeed seed =
                inventoryFixture.seedTagOrFilterResources(owner1StorageId, relocationFixture);
        trackStorageResourceForCleanup(seed.resourceA().getId());
        trackStorageResourceForCleanup(seed.resourceB().getId());
        trackStorageResourceForCleanup(seed.resourceC().getId());

        Allure.parameter("tagA", seed.tagA());
        Allure.parameter("tagB", seed.tagB());
        Allure.parameter("resourceA", seed.resourceA().getName());
        Allure.parameter("resourceB", seed.resourceB().getName());
        Allure.parameter("resourceC", seed.resourceC().getName());

        Allure.step("tag-statistics містить обидва унікальні теги", () -> {
            List<ProductionProcessTagStatisticResponse> stats =
                    inventoryFixture.getTagStatistics(owner1StorageId, UserRole.OWNER_1);
            Set<String> tags = stats.stream()
                    .map(ProductionProcessTagStatisticResponse::getTag)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            assertThat(tags)
                    .as("GET /storages/inventory/tag-statistics")
                    .contains(seed.tagA(), seed.tagB());
        });

        Allure.step("Два теги — union OR: A і B є, C немає", () -> {
            List<MultiLocationStorageItemResponse> union = inventoryFixture.listHierarchyByTags(
                    owner1StorageId, UserRole.OWNER_1, List.of(seed.tagA(), seed.tagB()));
            assertContainsResource(union, seed.resourceA().getId(), "union tags A+B");
            assertContainsResource(union, seed.resourceB().getId(), "union tags A+B");
            assertDoesNotContainResource(union, seed.resourceC().getId(), "union tags A+B");

            List<StorageItemResponse> single = inventoryFixture.listItems(
                    owner1StorageId, UserRole.OWNER_1, Map.of("tags", List.of(seed.tagA(), seed.tagB())));
            assertThat(single.stream().anyMatch(i -> resourceIdEquals(i, seed.resourceA().getId())))
                    .as("single-storage GET tags A+B містить A")
                    .isTrue();
            assertThat(single.stream().anyMatch(i -> resourceIdEquals(i, seed.resourceB().getId())))
                    .as("single-storage GET tags A+B містить B")
                    .isTrue();
            assertThat(single.stream().anyMatch(i -> resourceIdEquals(i, seed.resourceC().getId())))
                    .as("single-storage GET tags A+B не містить C")
                    .isFalse();
        });

        Allure.step("Один тег A — лише ресурс A", () -> {
            List<MultiLocationStorageItemResponse> onlyA = inventoryFixture.listHierarchyByTags(
                    owner1StorageId, UserRole.OWNER_1, List.of(seed.tagA()));
            assertContainsResource(onlyA, seed.resourceA().getId(), "tags=A");
            assertDoesNotContainResource(onlyA, seed.resourceB().getId(), "tags=A");
            assertDoesNotContainResource(onlyA, seed.resourceC().getId(), "tags=A");
        });

        Allure.step("Один тег B — лише ресурс B", () -> {
            List<MultiLocationStorageItemResponse> onlyB = inventoryFixture.listHierarchyByTags(
                    owner1StorageId, UserRole.OWNER_1, List.of(seed.tagB()));
            assertContainsResource(onlyB, seed.resourceB().getId(), "tags=B");
            assertDoesNotContainResource(onlyB, seed.resourceA().getId(), "tags=B");
            assertDoesNotContainResource(onlyB, seed.resourceC().getId(), "tags=B");
        });
    }

    private static boolean resourceIdEquals(StorageItemResponse item, Long resourceId) {
        return item.getResource() != null && Objects.equals(resourceId, item.getResource().getId());
    }

    private static void assertContainsResource(
            List<MultiLocationStorageItemResponse> rows, Long resourceId, String filterLabel) {
        assertThat(rows.stream().anyMatch(row -> row.getResource() != null
                && Objects.equals(resourceId, row.getResource().getId())))
                .as("%s має містити resourceId=%s", filterLabel, resourceId)
                .isTrue();
    }

    private static void assertDoesNotContainResource(
            List<MultiLocationStorageItemResponse> rows, Long resourceId, String filterLabel) {
        assertThat(rows.stream().anyMatch(row -> row.getResource() != null
                && Objects.equals(resourceId, row.getResource().getId())))
                .as("%s не повинен містити resourceId=%s", filterLabel, resourceId)
                .isFalse();
    }
}
