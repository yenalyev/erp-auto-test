package com.erp.tests.functional.relocation;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.UserRole;
import com.erp.enums.RelocationState;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.fixtures.ProductionFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.fixtures.InventoryFixture;
import com.erp.models.request.RelocationItemBatchRequest;
import com.erp.models.request.RelocationOutputRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.utils.helpers.XlsxWorkbookReader;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.assertj.core.api.SoftAssertions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Relocation")
@Feature("Batch and tech map in relocation Excel export")
public class RelocationBatchTechMapExportTest extends BaseFunctionalTest {

    private ProductionFixture productions;
    private RelocationFixture relocations;
    private ResourceFixture resources;
    private InventoryFixture inventory;
    private TechnologicalMapFixture techMaps;
    private TechnologicalMapFixture.IsolatedTechMapContext isolated;
    private TechnologicalMapResponse secondMap;
    private final List<ManufacturingItemResponse> createdProductions = new ArrayList<>();
    private final List<RelocationResponse> createdRelocations = new ArrayList<>();
    private final Set<Long> createdResourceIds = new LinkedHashSet<>();
    private long senderId;
    private long recipientId;
    private long productId;
    private String productName;
    private String batchA;
    private String batchB;
    private String batchC;
    private String externalBatch;
    private ResourceResponse otherResource;
    private String otherBatch;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareExportScenario() {
        senderId = ConfigProvider.getOwner1StorageId();
        recipientId = ConfigProvider.getOwner2StorageId();
        relocations = new RelocationFixture(testContext, apiExecutor);
        relocations.prepareContext();
        productions = new ProductionFixture(testContext, apiExecutor);
        techMaps = productions.getTechMapFixture();
        resources = new ResourceFixture(testContext, apiExecutor);
        inventory = new InventoryFixture(testContext, apiExecutor);
        techMaps.prepareContext();

        isolated = techMaps.createIsolatedProductionTechMap(
                UserRole.ADMIN, senderId, "REL-XLS-A");
        secondMap = techMaps.createAlternateActiveTechMap(UserRole.ADMIN, isolated.getTechMap());
        productId = isolated.getProduct().getId();
        productName = isolated.getProduct().getName();
        createdResourceIds.add(productId);
        isolated.getTechMap().getInput().stream()
                .filter(input -> input.getResource() != null)
                .map(input -> input.getResource().getId())
                .forEach(createdResourceIds::add);
        techMaps.seedStockForIsolatedTechMap(productions, senderId, isolated.getTechMap(), 60.0);

        batchA = ProductionDataFactory.uniqueBatchNumber() + "-A";
        batchB = ProductionDataFactory.uniqueBatchNumber() + "-B";
        batchC = ProductionDataFactory.uniqueBatchNumber() + "-C";
        externalBatch = ProductionDataFactory.uniqueBatchNumber() + "-EXT";
        createdProductions.add(productions.createAs(UserRole.ADMIN, senderId,
                isolated.getTechMap(), 5.0, batchA));
        createdProductions.add(productions.createAs(UserRole.ADMIN, senderId,
                isolated.getTechMap(), 2.0, batchB));
        createdProductions.add(productions.createAs(UserRole.ADMIN, senderId,
                secondMap, 8.0, batchC));

        otherResource = resources.createUniqueResource("REL-XLS-OTHER-");
        createdResourceIds.add(otherResource.getId());
        otherBatch = "REL-XLS-OTHER-" + UUID.randomUUID();
        createdRelocations.add(relocations.createExternalReceive(
                UserRole.ADMIN, senderId, otherResource.getId(), 3.0, otherBatch));
        UUID otherUuid = ProductionStockAssertions.requireBatchUuid(
                apiExecutor, senderId, UserRole.ADMIN, otherResource.getId(), otherBatch, false);

        RelocationOutputRequest send = RelocationDataFactory.buildSendMultiItem(
                senderId, recipientId,
                List.of(ResourceUsageRequest.builder()
                                .resourceId(productId)
                                .amount(new BigDecimal("15"))
                                .batches(List.of(batchRequest(batchA, 5.0),
                                        batchRequest(batchB, 2.0), batchRequest(batchC, 8.0)))
                                .build(),
                        RelocationDataFactory.usageWithBatch(otherResource.getId(),
                                3.0, otherUuid, otherBatch, false)),
                "REL-XLS multi-resource " + UUID.randomUUID());
        Response sendResponse = relocations.sendRaw(UserRole.ADMIN, send);
        assertThat(sendResponse.statusCode()).isBetween(200, 299);
        createdRelocations.add(sendResponse.as(RelocationResponse.class));
        createdRelocations.add(relocations.createExternalReceive(
                UserRole.ADMIN, senderId, productId, 4.0, externalBatch));
    }

    private RelocationItemBatchRequest batchRequest(String number, double amount) {
        UUID uuid = ProductionStockAssertions.requireBatchUuid(
                apiExecutor, senderId, UserRole.ADMIN, productId, number, true);
        return RelocationItemBatchRequest.builder()
                .batchUuid(uuid)
                .batchNumber(number)
                .amount(BigDecimal.valueOf(amount))
                .isProduced(true)
                .build();
    }

    @Test(priority = 10)
    @TestCaseId("TC-REL-XLS-001")
    @Severity(SeverityLevel.CRITICAL)
    public void exportHasBatchAndTechMapColumns() {
        List<List<String>> rows = exportedRows(Map.of("senderIds", senderId,
                "productIds", List.of(productId)));
        List<String> headers = headers(rows);
        assertThat(headers).contains("Назва", "Кількість", "Одиниця виміру", "№ накладної");
        assertThat(headers.stream().filter("Партія"::equals).count()).isEqualTo(1);
        assertThat(headers.stream().filter("Тех карта"::equals).count()).isEqualTo(1);
        assertThat(headers.indexOf("Одиниця виміру"))
                .isLessThan(headers.indexOf("Партія"));
        assertThat(headers.indexOf("Партія")).isLessThan(headers.indexOf("Тех карта"));
        assertThat(headers.indexOf("Тех карта")).isLessThan(headers.indexOf("№ накладної"));
    }

    @Test(priority = 20)
    @TestCaseId("TC-REL-XLS-002")
    @Severity(SeverityLevel.CRITICAL)
    public void oneResourceExportsOneRowPerProducedBatchWithItsTechMap() {
        List<List<String>> rows = exportedRows(Map.of("senderIds", senderId,
                "productIds", List.of(productId)));
        List<String> headers = headers(rows);
        List<List<String>> productRows = dataRows(rows).stream()
                .filter(row -> row.contains(productName)).toList();
        assertThat(productRows).as("Rows for unique product: %s", rows).hasSize(3);
        SoftAssertions softly = new SoftAssertions();
        assertBatchRow(softly, productRows, headers, batchA, "5", isolated.getTechMap().getName());
        assertBatchRow(softly, productRows, headers, batchB, "2", isolated.getTechMap().getName());
        assertBatchRow(softly, productRows, headers, batchC, "8", secondMap.getName());
        BigDecimal sum = productRows.stream()
                .map(row -> new BigDecimal(cell(row, headers, "Кількість")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        softly.assertThat(sum).as("Sum of exported batch quantities").isEqualByComparingTo("15");
        softly.assertAll();
    }

    @Test(priority = 30)
    @TestCaseId("TC-REL-XLS-004")
    public void externalBatchExportsWithoutUnrelatedTechMap() {
        List<List<String>> rows = exportedRows(Map.of("receiverIds", senderId,
                "productIds", List.of(productId)));
        List<String> headers = headers(rows);
        List<List<String>> matches = dataRows(rows).stream()
                .filter(row -> row.contains(externalBatch)).toList();
        assertThat(matches).as("External batch in received export: %s", rows).hasSize(1);
        assertThat(new BigDecimal(cell(matches.getFirst(), headers, "Кількість")))
                .isEqualByComparingTo("4");
        assertThat(cell(matches.getFirst(), headers, "Тех карта")).isIn("", "-");
    }

    @Test(priority = 25)
    @TestCaseId("TC-REL-XLS-003")
    @Severity(SeverityLevel.CRITICAL)
    public void multiResourceExportKeepsBatchRowsUnderTheirOwnResource() {
        List<List<String>> rows = exportedRows(Map.of("senderIds", senderId,
                "productIds", List.of(productId, otherResource.getId())));
        List<String> headers = headers(rows);
        List<List<String>> sendRows = dataRows(rows).stream()
                .filter(row -> row.contains(productName) || row.contains(otherResource.getName()))
                .toList();
        assertThat(sendRows).as("Target relocation rows: %s", sendRows).hasSize(4);
        assertThat(sendRows.stream().filter(row -> row.contains(productName)).toList())
                .hasSize(3)
                .allSatisfy(row -> assertThat(cell(row, headers, "Партія"))
                        .isIn(batchA, batchB, batchC));
        List<List<String>> otherRows = sendRows.stream()
                .filter(row -> row.contains(otherResource.getName())).toList();
        assertThat(otherRows).hasSize(1);
        assertThat(cell(otherRows.getFirst(), headers, "Партія")).isEqualTo(otherBatch);
        assertThat(new BigDecimal(cell(otherRows.getFirst(), headers, "Кількість")))
                .isEqualByComparingTo("3");
    }

    private List<List<String>> exportedRows(Map<String, Object> filters) {
        return XlsxWorkbookReader.firstSheet(exportedBytes(filters));
    }

    private byte[] exportedBytes(Map<String, Object> filters) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RELOCATION_GET_EXPORT, UserRole.ADMIN, filters);
        assertThat(response.statusCode()).isEqualTo(200);
        return response.asByteArray();
    }

    private static List<String> headers(List<List<String>> rows) {
        return rows.stream().filter(row -> row.contains("Кількість"))
                .findFirst().orElseThrow(() -> new AssertionError("XLSX header not found: "
                        + rows.stream().limit(5).toList()));
    }

    private static List<List<String>> dataRows(List<List<String>> rows) {
        int headerIndex = rows.indexOf(headers(rows));
        return rows.subList(headerIndex + 1, rows.size());
    }

    private static String cell(List<String> row, List<String> headers, String column) {
        int index = XlsxWorkbookReader.columnIndex(headers, column);
        return index < row.size() ? row.get(index) : "";
    }

    private static void assertBatchRow(SoftAssertions softly, List<List<String>> rows, List<String> headers,
                                       String batch, String amount, String mapName) {
        List<List<String>> matches = rows.stream()
                .filter(row -> batch.equals(cell(row, headers, "Партія"))).toList();
        softly.assertThat(matches).as("Batch %s: %s", batch, rows).hasSize(1);
        if (matches.size() != 1) {
            return;
        }
        softly.assertThat(new BigDecimal(cell(matches.getFirst(), headers, "Кількість")))
                .as("Quantity of batch %s", batch).isEqualByComparingTo(amount);
        softly.assertThat(cell(matches.getFirst(), headers, "Тех карта"))
                .as("Tech map of batch %s", batch).isEqualTo(mapName);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupExportScenario() {
        if (relocations != null) {
            for (int i = createdRelocations.size() - 1; i >= 0; i--) {
                RelocationResponse relocation = createdRelocations.get(i);
                if (relocation.getState() == RelocationState.CREATED) {
                    try {
                        relocations.resolve(UserRole.ADMIN, relocation.getId(), recipientId,
                                RelocationState.CANCELLED);
                        relocations.resolve(UserRole.ADMIN, relocation.getId(), senderId,
                                RelocationState.RETURNED);
                    } catch (RuntimeException ignored) {
                        // Continue with deletion if a state transition is unavailable.
                    }
                }
                try {
                    relocations.deleteRelocation(UserRole.ADMIN, relocation.getId(), senderId);
                } catch (RuntimeException ignored) {
                    // Suite artifact cleanup retries registered relocation IDs.
                }
            }
        }
        if (productions != null) {
            for (int i = createdProductions.size() - 1; i >= 0; i--) {
                try {
                    productions.deleteAs(UserRole.ADMIN, createdProductions.get(i).getId(), senderId);
                } catch (RuntimeException ignored) {
                    // A failed relocation delete may keep this production in use.
                }
            }
        }
        if (techMaps != null && secondMap != null) {
            techMaps.deactivateTechMap(UserRole.ADMIN, secondMap.getId(), senderId);
        }
        if (techMaps != null && isolated != null) {
            techMaps.deactivateTechMap(UserRole.ADMIN, isolated.getTechMap().getId(), senderId);
        }
        for (Long resourceId : createdResourceIds) {
            if (inventory != null) {
                try {
                    inventory.removeResourceFromStorage(senderId, resourceId, UserRole.ADMIN);
                } catch (RuntimeException ignored) {
                    // Continue with remaining owned resources.
                }
            }
            if (resources != null) {
                try {
                    resources.deactivate(UserRole.ADMIN, resourceId);
                } catch (RuntimeException ignored) {
                    // The suite reports primary assertions independently of cleanup.
                }
            }
        }
        if (techMaps != null && senderId != 0) {
            try {
                techMaps.setMode(senderId, StorageTechnologicalMapMode.READ_ONLY);
            } catch (RuntimeException ignored) {
                // Preserve the main test result if restoring the mode is unavailable.
            }
        }
    }
}
