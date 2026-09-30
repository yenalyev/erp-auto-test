package com.erp.tests.functional.inventory;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.IsolatedMultiLocationOwnerScope;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.MultiLocationStorageItemResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageAmountResponse;
import com.erp.models.response.UserMeResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.helpers.XlsxContentAssertions;
import com.erp.utils.helpers.XlsxWorkbookReader;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Multi-location owner export regression: «Всі локації» → GET /export-analytics/inventory.
 */
@Slf4j
@Epic("Inventory")
@Feature("REQ-WMS-007 Stock")
public class InventoryMultiLocationExportApiTest extends BaseFunctionalTest {

    private StorageFixture storageFixture;
    private UserFixture userFixture;
    private InventoryFixture inventoryFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;
    private IsolatedMultiLocationOwnerScope multiLocationScope;

    private IsolatedMultiLocationOwnerScope.Context ownerContext;
    private ResourceResponse resourceA;
    private ResourceResponse resourceB;
    private ResourceResponse sharedResource;
    private ResourceResponse decoyResource;
    private static final UserRole OWNER = UserRole.OWNER_2;
    private static final double STOCK_A = 12.0;
    private static final double STOCK_B = 18.0;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void arrangeMultiLocationOwnerWithStock() {
        storageFixture = new StorageFixture(testContext, apiExecutor);
        userFixture = new UserFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
        resourceFixture.prepareContext();

        multiLocationScope = new IsolatedMultiLocationOwnerScope(
                storageFixture,
                userFixture,
                apiExecutor,
                getPlaywrightSessionProvider());
        ownerContext = multiLocationScope.acquire(OWNER);

        resourceA = resourceFixture.createUniqueResource("mloc-exp-a-");
        resourceB = resourceFixture.createUniqueResource("mloc-exp-b-");
        sharedResource = resourceFixture.createUniqueResource("mloc-exp-shared-");
        decoyResource = resourceFixture.createUniqueResource("mloc-decoy-");
        relocationFixture.seedExactStock(ownerContext.storageAId(), resourceA.getId(), STOCK_A);
        relocationFixture.seedExactStock(ownerContext.storageBId(), resourceB.getId(), STOCK_B);
        relocationFixture.seedExactStock(ownerContext.storageAId(), sharedResource.getId(), 4.0);
        relocationFixture.seedExactStock(ownerContext.storageBId(), sharedResource.getId(), 9.0);

        long forbiddenStorageId = storageFixture.createUniqueStorage("mloc-forbidden-").getId();
        relocationFixture.seedExactStock(forbiddenStorageId, decoyResource.getId(), 99.0);

        inventoryFixture.requireItemForResourceWithRetry(
                ownerContext.storageAId(), resourceA.getId(), OWNER, 15_000);
        inventoryFixture.requireItemForResourceWithRetry(
                ownerContext.storageBId(), resourceB.getId(), OWNER, 15_000);
        inventoryFixture.requireItemForResourceWithRetry(
                ownerContext.storageAId(), sharedResource.getId(), OWNER, 15_000);
        inventoryFixture.requireItemForResourceWithRetry(
                ownerContext.storageBId(), sharedResource.getId(), OWNER, 15_000);
        inventoryFixture.requireItemForResourceWithRetry(
                forbiddenStorageId, decoyResource.getId(), UserRole.ADMIN, 15_000);
    }

    @AfterClass(alwaysRun = true)
    public void releaseMultiLocationScope() {
        if (multiLocationScope != null) {
            multiLocationScope.release();
        }
    }

    @Test
    @TestCaseId("TC-WMS-007-019")
    @Story("Multi-location owner exports all permitted remainders")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Arrange: ephemeral Keycloak owner з двома UNIT-локаціями; унікальні ресурси з залишками на кожній.
            Act (CPMA-762): GET /export-analytics/inventory?locations=storageA,storageB
            — як tk-ui у режимі «Всі локації» (allowedActiveStorageIds).
            Expect: 200, XLSX містить лише рядки з API inventory owner-локацій (resourceA/B + кількості).
            Control: decoy на чужій локації та resourceB відсутні там, де не мають бути.
            """)
    public void multiLocationOwnerExportsAllLocationsExcel() {
        UserMeResponse me = userFixture.getMe(OWNER);
        Allure.parameter("ownerUsername", ownerContext.owner().username());
        Allure.parameter("allowedStorageIds", me.getAllowedStorageIds());
        Allure.parameter("storageAId", ownerContext.storageAId());
        Allure.parameter("storageBId", ownerContext.storageBId());

        assertThat(me.getAllowedStorageIds())
                .as("Owner must have access to both isolated storages")
                .contains(ownerContext.storageAId(), ownerContext.storageBId());

        String locationsCsv = ownerContext.storageAId() + "," + ownerContext.storageBId();
        Response inventoryList = inventoryFixture.getMultiLocationInventory(OWNER, locationsCsv);
        assertThat(inventoryList.statusCode())
                .as("Multi-location inventory list must load for owner storages")
                .isEqualTo(200);

        Response allLocations = inventoryFixture.exportRemaindersByLocations(
                OWNER,
                List.of(ownerContext.storageAId(), ownerContext.storageBId()),
                null);
        assertThat(allLocations.statusCode())
                .as("All-locations export must succeed for multi-location owner")
                .isEqualTo(200);
        byte[] allBytes = allLocations.asByteArray();
        assertThat(allBytes.length).isGreaterThan(100);
        assertThat(allBytes[0]).as("XLSX ZIP magic").isEqualTo((byte) 'P');
        assertThat(allBytes[1]).as("XLSX ZIP magic").isEqualTo((byte) 'K');
        assertExportMatchesOwnerInventory(allBytes, inventoryList);
        assertThat(XlsxContentAssertions.zipContainsAmount(allBytes, STOCK_A))
                .as("All-locations XLSX must include stock quantity for resource A")
                .isTrue();
        assertThat(XlsxContentAssertions.zipContainsAmount(allBytes, STOCK_B))
                .as("All-locations XLSX must include stock quantity for resource B")
                .isTrue();
        assertThat(XlsxContentAssertions.zipContainsText(allBytes, decoyResource.getName()))
                .as("All-locations XLSX must not leak decoy from forbidden storage")
                .isFalse();
        assertThat(XlsxContentAssertions.zipContainsAmount(allBytes, 99.0))
                .as("All-locations XLSX must not include decoy stock quantity")
                .isFalse();

        Response singleA = inventoryFixture.exportRemaindersHierarchy(
                OWNER, ownerContext.storageAId(), null);
        assertThat(singleA.statusCode()).isEqualTo(200);
        byte[] singleABytes = singleA.asByteArray();
        assertThat(XlsxContentAssertions.zipContainsText(singleABytes, resourceA.getName())).isTrue();
        assertThat(XlsxContentAssertions.zipContainsText(singleABytes, resourceB.getName()))
                .as("Single-location export for A must not include storage-B-only resource")
                .isFalse();
        assertThat(XlsxContentAssertions.zipContainsText(singleABytes, decoyResource.getName()))
                .as("Single-location export for A must not include decoy from forbidden storage")
                .isFalse();
    }

    @Test
    @TestCaseId("TC-WMS-007-022")
    @Story("Inventory workbook contains a per-storage worksheet")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            На аркуші «По складах» один ресурс, що лежить на двох дозволених складах,
            має один рядок: загальний залишок 13, бронь 0, окремі числові колонки 4 і 9.
            П'ять фіксованих заголовків і назви складів перевіряються у завантаженому XLSX.
            """)
    public void exportContainsPerStorageSheetWithAggregatedResource() throws IOException {
        Response response = inventoryFixture.exportRemaindersByLocations(
                OWNER,
                List.of(ownerContext.storageAId(), ownerContext.storageBId()),
                Map.of("searchTerm", sharedResource.getName()));
        assertThat(response.statusCode()).isEqualTo(200);
        byte[] xlsx = response.asByteArray();
        assertThat(XlsxWorkbookReader.sheetNames(xlsx))
                .contains("Залишок", "По складах");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheet("По складах");
            Row header = sheet.getRow(0);
            assertThat(header).as("Аркуш має рядок заголовків").isNotNull();
            DataFormatter formatter = new DataFormatter();
            List<String> headings = java.util.stream.IntStream.range(0, header.getLastCellNum())
                    .mapToObj(index -> formatter.formatCellValue(header.getCell(index)).trim())
                    .toList();

            Response inventory = inventoryFixture.getMultiLocationInventory(
                    OWNER, ownerContext.storageAId() + "," + ownerContext.storageBId());
            assertThat(inventory.statusCode()).isEqualTo(200);
            List<MultiLocationStorageItemResponse> inventoryRows =
                    inventory.jsonPath().getList("content", MultiLocationStorageItemResponse.class);
            MultiLocationStorageItemResponse source = inventoryRows.stream()
                    .filter(item -> item.getResource() != null
                            && sharedResource.getId().equals(item.getResource().getId()))
                    .findFirst().orElseThrow();
            Map<Long, StorageAmountResponse> locations = source.getLocations().stream()
                    .filter(location -> location.getStorage() != null)
                    .collect(Collectors.toMap(location -> location.getStorage().getId(), location -> location));
            StorageAmountResponse a = locations.get(ownerContext.storageAId());
            StorageAmountResponse b = locations.get(ownerContext.storageBId());
            assertThat(a).isNotNull();
            assertThat(b).isNotNull();
            assertThat(a.getAmount()).isEqualTo(4.0);
            assertThat(b.getAmount()).isEqualTo(9.0);
            double bookedA = a.getBookedAmount() == null ? 0.0 : a.getBookedAmount();
            double bookedB = b.getBookedAmount() == null ? 0.0 : b.getBookedAmount();
            assertThat(bookedA).isZero();
            assertThat(bookedB).isZero();

            int aColumn = storageColumn(headings, a.getStorage().getName());
            int bColumn = storageColumn(headings, b.getStorage().getName());
            assertThat(aColumn).isNotEqualTo(bColumn);
            assertThat(headings.subList(5, headings.size())).hasSize(2);

            List<Row> matchingRows = java.util.stream.IntStream.rangeClosed(1, sheet.getLastRowNum())
                    .mapToObj(sheet::getRow)
                    .filter(Objects::nonNull)
                    .filter(row -> sharedResource.getName().equals(formatter.formatCellValue(row.getCell(0))))
                    .toList();
            assertThat(matchingRows).as("Ресурс має рівно один агрегований рядок").hasSize(1);
            assertThat(sheet.getPhysicalNumberOfRows())
                    .as("Пошук у експорті залишає тільки заголовок і ресурс")
                    .isEqualTo(2);
            Row row = matchingRows.getFirst();
            assertNumericCell(row, 1, a.getAmount() + b.getAmount());
            assertNumericCell(row, 2, bookedA + bookedB);
            assertNumericCell(row, aColumn, a.getAmount());
            assertNumericCell(row, bColumn, b.getAmount());
            assertThat(formatter.formatCellValue(row.getCell(3)))
                    .isIn(source.getResource().getUnit().getShortName(),
                            source.getResource().getUnit().getName());
            assertThat(formatter.formatCellValue(row.getCell(4)))
                    .isEqualTo(source.getResource().getCategory().getName());
            assertThat(XlsxContentAssertions.zipContainsText(xlsx, decoyResource.getName()))
                    .as("Чужий ресурс відсутній у книзі")
                    .isFalse();
            assertThat(headings).startsWith(
                    "Назва", "Залишок", "Заброньовано", "Одиниця Виміру", "Категорія");
        }
    }

    private static int storageColumn(List<String> headings, String storageName) {
        List<Integer> matches = java.util.stream.IntStream.range(5, headings.size())
                .filter(index -> headings.get(index).contains(storageName))
                .boxed().toList();
        assertThat(matches).as("Одна колонка для складу %s", storageName).hasSize(1);
        return matches.getFirst();
    }

    private static void assertNumericCell(Row row, int column, double expected) {
        Cell cell = row.getCell(column);
        assertThat(cell).as("Числова комірка %s у рядку %s", column, row.getRowNum() + 1).isNotNull();
        assertThat(cell.getCellType()).isEqualTo(CellType.NUMERIC);
        assertThat(cell.getNumericCellValue()).isEqualTo(expected);
    }

    private void assertExportMatchesOwnerInventory(byte[] xlsx, Response inventoryList) {
        Set<Long> allowedStorages = Set.of(ownerContext.storageAId(), ownerContext.storageBId());
        List<MultiLocationStorageItemResponse> rows =
                inventoryList.jsonPath().getList("content", MultiLocationStorageItemResponse.class);
        assertThat(rows).isNotNull();

        Set<String> visibleResourceNames = rows.stream()
                .filter(row -> row.getResource() != null && row.getResource().getName() != null)
                .filter(row -> hasPositiveAmountOnStorages(row, allowedStorages))
                .map(row -> row.getResource().getName())
                .collect(Collectors.toSet());

        assertThat(visibleResourceNames)
                .as("Owner inventory API must expose both seeded resources")
                .contains(resourceA.getName(), resourceB.getName());
        assertThat(visibleResourceNames)
                .as("Owner inventory API must not expose decoy from forbidden storage")
                .doesNotContain(decoyResource.getName());

        for (String name : visibleResourceNames) {
            assertThat(XlsxContentAssertions.zipContainsText(xlsx, name))
                    .as("XLSX must include every resource visible in owner inventory API: %s", name)
                    .isTrue();
        }
    }

    private static boolean hasPositiveAmountOnStorages(
            MultiLocationStorageItemResponse row, Set<Long> storageIds) {
        if (row.getLocations() == null) {
            return false;
        }
        return row.getLocations().stream()
                .filter(Objects::nonNull)
                .anyMatch(loc -> isPositiveOnStorage(loc, storageIds));
    }

    private static boolean isPositiveOnStorage(StorageAmountResponse loc, Set<Long> storageIds) {
        if (loc.getStorage() == null || loc.getStorage().getId() == null) {
            return false;
        }
        return storageIds.contains(loc.getStorage().getId())
                && loc.getAmount() != null
                && loc.getAmount() > 0;
    }
}
