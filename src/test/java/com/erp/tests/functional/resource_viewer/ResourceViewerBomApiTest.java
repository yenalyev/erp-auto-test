package com.erp.tests.functional.resource_viewer;

import com.erp.annotations.DynamicResourceViewer;
import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.LocationProfile;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.LocationProfileFixture;
import com.erp.fixtures.ProductionFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.request.RelocationItemBatchRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.PagedResourceRelocationViewerResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceRelocationSumViewerResponse;
import com.erp.models.response.ResourceRelocationViewerResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Resource Viewer (wolf) BOM / «декомпозер» — origin + nesting + aggregation.
 * <p>
 * Tracked resource «Alcohol» is synthetic ({@code RVW-ALC-*}); business analogy = спирт у складі.
 */
@Slf4j
@Epic("Resource Viewer")
@Feature("BOM decomposer")
@DynamicResourceViewer
public class ResourceViewerBomApiTest extends BaseFunctionalTest {

    private static final double ALC_PER_UNIT = 2.0;
    private static final double SEMI_PER_PRODUCT = 3.0;
    private static final double SF1_PER_SF2 = 2.0;
    private static final double SF2_PER_PRODUCT = 3.0;
    private static final double PRODUCE_AMOUNT = 5.0;
    private static final double RELOCATE_AMOUNT = 5.0;
    private static final double STOCK_PAD = 200.0;

    private TechnologicalMapFixture techMapFixture;
    private ProductionFixture productionFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;
    private InventoryFixture inventoryFixture;
    private LocationProfileFixture locationProfileFixture;

    private Long productionStorageId;
    private Long receiverUnitId;

    private final List<TechnologicalMapResponse> createdMaps = new ArrayList<>();

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    @Step("Підготовка fixtures для Resource Viewer BOM")
    public void setupBomSuite() {
        productionFixture = new ProductionFixture(testContext, apiExecutor);
        techMapFixture = productionFixture.getTechMapFixture();
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        locationProfileFixture = new LocationProfileFixture(testContext, apiExecutor);

        techMapFixture.prepareContext();
        resourceFixture.prepareContext();
        relocationFixture.prepareContext();
        inventoryFixture.prepareContext();

        productionStorageId = locationProfileFixture
                .create(LocationProfile.TSUK_PRODUCTION, 1)
                .locations().getFirst().getId();
        receiverUnitId = locationProfileFixture
                .create(LocationProfile.BATTALION_UNIT, 1)
                .locations().getFirst().getId();
        techMapFixture.setMode(productionStorageId, StorageTechnologicalMapMode.EDIT_ALLOWED);
        SchemaRegistry.logSchemaCoverage();
        log.info("BOM suite route: TSUK child production={} -> outside UNIT={}",
                productionStorageId, receiverUnitId);
    }

    @AfterClass(alwaysRun = true)
    public void teardown() {
        for (TechnologicalMapResponse map : createdMaps) {
            try {
                techMapFixture.deactivateTechMap(UserRole.ADMIN, map.getId(), productionStorageId);
            } catch (RuntimeException e) {
                log.warn("Tech map deactivate failed id={}: {}", map.getId(), e.getMessage());
            }
        }
        if (techMapFixture != null && productionStorageId != null) {
            try {
                techMapFixture.setMode(productionStorageId, StorageTechnologicalMapMode.READ_ONLY);
            } catch (RuntimeException e) {
                log.warn("Restore READ_ONLY mode failed for storage {}: {}",
                        productionStorageId, e.getMessage());
            }
        }
        if (locationProfileFixture != null) {
            locationProfileFixture.cleanup();
        }
    }

    // ───────────────────────────── Origin ─────────────────────────────

    @Test(priority = 10)
    @TestCaseId("TC-RVW-BOM-001")
    @Story("Direct issue of tracked resource")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Пряма видача Alcohol STORAGE→UNIT: journal isProduct=false; sum == sendAmount")
    public void testDirectIssueOfTrackedResource() {
        String suffix = uniqueSuffix();
        ResourceResponse alcohol = resourceFixture.createUniqueResource("RVW-ALC-D1-" + suffix);

        relocationFixture.ensureStock(productionStorageId, alcohol.getId(), STOCK_PAD, UserRole.ADMIN);
        RelocationResponse sent = relocationFixture.createSend(
                UserRole.ADMIN, productionStorageId, receiverUnitId, alcohol.getId(), RELOCATE_AMOUNT);

        List<ResourceRelocationSumViewerResponse> sums = fetchSums(List.of(alcohol.getId()));
        assertAmount(sums, alcohol.getId(), RELOCATE_AMOUNT);

        List<ResourceRelocationViewerResponse> rows = fetchJournal(List.of(alcohol.getId()));
        ResourceRelocationViewerResponse row = findByRelocationId(rows, sent.getId());
        assertThat(row.getIsProduct()).as("direct Alcohol row isProduct=false").isFalse();
        assertThat(row.getProduct().getId()).isEqualTo(alcohol.getId());
        assertThat(totallyUsageOf(row, alcohol.getId()))
                .isCloseTo(RELOCATE_AMOUNT, within(0.001));
    }

    @Test(priority = 20)
    @TestCaseId("TC-RVW-BOM-002")
    @Story("Self-produced product with tracked component")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Виробили Product(Alcohol@2) → видача партії → sum = relocate × 2; ingredient у journal")
    public void testSelfProducedProductWithTrackedComponent() {
        String suffix = uniqueSuffix();
        ResourceResponse alcohol = resourceFixture.createUniqueResource("RVW-ALC-P-" + suffix);
        ResourceResponse product = resourceFixture.createUniqueResource("RVW-P-P-" + suffix);

        TechnologicalMapResponse map = createMap(
                "RVW-BOM-D1",
                List.of(new ResourceUsageRequest(alcohol.getId(), ALC_PER_UNIT)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)));

        double producedAmount = 10.0;
        double movedAmount = 4.0;
        ManufacturingItemResponse produced = produce(map, producedAmount);
        RelocationResponse sent = relocateProduced(product.getId(), movedAmount, produced.getBatchNumber());

        double expected = movedAmount * ALC_PER_UNIT;
        assertAmount(fetchSums(List.of(alcohol.getId())), alcohol.getId(), expected);

        ResourceRelocationViewerResponse row = findByRelocationId(
                fetchJournal(List.of(alcohol.getId())), sent.getId());
        assertThat(row.getIsProduct()).isTrue();
        assertThat(row.getProduct().getId()).isEqualTo(product.getId());
        assertThat(totallyUsageOf(row, alcohol.getId())).isCloseTo(expected, within(0.001));
    }

    @Test(priority = 30)
    @TestCaseId("TC-RVW-BOM-003")
    @Story("External finished good uses the historical tech-map fallback")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Підтверджена зовнішня партія розкладається за найновішою картою на момент переміщення; alternative group використовує default")
    public void testExternalFinishedGoodUsesHistoricalTechMapFallback() {
        String suffix = uniqueSuffix();
        ResourceResponse fixed = resourceFixture.createUniqueResource("RVW-FIX-X-" + suffix);
        ResourceResponse defaultAlt = resourceFixture.createUniqueResource("RVW-DEF-X-" + suffix);
        ResourceResponse otherAlt = resourceFixture.createUniqueResource("RVW-OTH-X-" + suffix);
        ResourceResponse product = resourceFixture.createUniqueResource("RVW-P-X-" + suffix);

        createMapWithAlternativeGroup("RVW-BOM-EXT", fixed, defaultAlt, otherAlt, product);

        String externalBatch = "EXT-BOM-" + suffix;
        double receivedAmount = 10.0;
        double movedAmount = 4.0;
        relocationFixture.seedBatchOnStorage(
                productionStorageId, product.getId(), receivedAmount, externalBatch);

        RelocationResponse sent = relocationFixture.createSendWithBatch(
                UserRole.ADMIN, productionStorageId, receiverUnitId, product.getId(), movedAmount,
                externalBatch, false);

        List<ResourceRelocationSumViewerResponse> sums = fetchSums(
                List.of(fixed.getId(), defaultAlt.getId(), otherAlt.getId()));
        assertAmount(sums, fixed.getId(), movedAmount);
        assertAmount(sums, defaultAlt.getId(), movedAmount * 2.0);
        assertAmount(sums, otherAlt.getId(), 0.0);

        List<ResourceRelocationViewerResponse> productRows = rowsForRelocation(
                fetchJournal(List.of(product.getId())), sent.getId());
        assertThat(productRows).hasSize(1);
        ResourceRelocationViewerResponse productRow = productRows.getFirst();
        assertThat(productRow.getProduct().getId()).isEqualTo(product.getId());
        assertThat(productRow.getAmount().doubleValue()).isCloseTo(movedAmount, within(0.001));
        assertThat(productRow.getIsProduct())
                .as("рядок кореневого ресурсу не є ingredient/product decomposition row")
                .isFalse();
    }

    @Test(priority = 40)
    @TestCaseId("TC-RVW-BOM-004")
    @Story("Mixed produced and external origin")
    @Severity(SeverityLevel.NORMAL)
    @Description("Produced-частина використовує фактичну alternative, зовнішня — default alternative історичної карти")
    public void testMixedProducedAndExternalProductUseIndependentAlgorithms() {
        String suffix = uniqueSuffix();
        ResourceResponse fixed = resourceFixture.createUniqueResource("RVW-FIX-M-" + suffix);
        ResourceResponse defaultAlt = resourceFixture.createUniqueResource("RVW-DEF-M-" + suffix);
        ResourceResponse otherAlt = resourceFixture.createUniqueResource("RVW-OTH-M-" + suffix);
        ResourceResponse product = resourceFixture.createUniqueResource("RVW-P-M-" + suffix);

        TechnologicalMapResponse map = createMapWithAlternativeGroup(
                "RVW-BOM-MIX", fixed, defaultAlt, otherAlt, product);

        productionFixture.ensureStockForTechMapInputs(productionStorageId, map, STOCK_PAD);
        Long groupId = map.getGroups().getFirst().getId();
        ManufacturingItemResponse produced = productionFixture.createAsWithAlternatives(
                UserRole.ADMIN,
                productionStorageId,
                map,
                PRODUCE_AMOUNT,
                ProductionDataFactory.alternativeInputsChoosing(map, groupId, otherAlt.getId()));
        String externalBatch = "EXT-" + suffix;
        relocationFixture.seedBatchOnStorage(
                productionStorageId, product.getId(), RELOCATE_AMOUNT, externalBatch);

        double relocateTotal = PRODUCE_AMOUNT + RELOCATE_AMOUNT; // 10
        RelocationResponse sent = relocationFixture.createSendWithBatches(
                UserRole.ADMIN,
                productionStorageId,
                receiverUnitId,
                product.getId(),
                relocateTotal,
                List.of(
                        RelocationItemBatchRequest.builder()
                                .batchNumber(produced.getBatchNumber())
                                .amount(BigDecimal.valueOf(PRODUCE_AMOUNT))
                                .isProduced(true)
                                .build(),
                        RelocationItemBatchRequest.builder()
                                .batchNumber(externalBatch)
                                .amount(BigDecimal.valueOf(RELOCATE_AMOUNT))
                                .isProduced(false)
                                .build()));

        List<ResourceRelocationSumViewerResponse> sums = fetchSums(
                List.of(fixed.getId(), defaultAlt.getId(), otherAlt.getId()));
        assertAmount(sums, fixed.getId(), relocateTotal);
        assertAmount(sums, defaultAlt.getId(), RELOCATE_AMOUNT * 2.0);
        assertAmount(sums, otherAlt.getId(), PRODUCE_AMOUNT * 3.0);

        List<ResourceRelocationViewerResponse> componentRows = rowsForRelocation(
                fetchJournal(List.of(fixed.getId(), defaultAlt.getId(), otherAlt.getId())), sent.getId());
        assertThat(componentRows).hasSize(1);
        ResourceRelocationViewerResponse row = componentRows.getFirst();
        assertThat(totallyUsageOf(row, fixed.getId())).isCloseTo(relocateTotal, within(0.001));
        assertThat(totallyUsageOf(row, defaultAlt.getId()))
                .isCloseTo(RELOCATE_AMOUNT * 2.0, within(0.001));
        assertThat(totallyUsageOf(row, otherAlt.getId()))
                .isCloseTo(PRODUCE_AMOUNT * 3.0, within(0.001));

        List<ResourceRelocationViewerResponse> productRows = rowsForRelocation(
                fetchJournal(List.of(product.getId())), sent.getId());
        assertThat(productRows).hasSize(1);
        assertThat(productRows.getFirst().getAmount().doubleValue())
                .isCloseTo(relocateTotal, within(0.001));
    }

    // ───────────────────────────── Depth ─────────────────────────────

    @Test(priority = 60)
    @TestCaseId("TC-RVW-BOM-011")
    @Story("BOM depth 2")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Alcohol → Semi → Product; sum = relocate × semiPerProduct × alcPerSemi")
    public void testBomDepth2() {
        String suffix = uniqueSuffix();
        ResourceResponse alcohol = resourceFixture.createUniqueResource("RVW-ALC-D11-" + suffix);
        ResourceResponse semi = resourceFixture.createUniqueResource("RVW-SF-D11-" + suffix);
        ResourceResponse product = resourceFixture.createUniqueResource("RVW-P-D11-" + suffix);

        TechnologicalMapResponse mapSemi = createMap(
                "RVW-BOM-D11-SF",
                List.of(new ResourceUsageRequest(alcohol.getId(), ALC_PER_UNIT)),
                List.of(new ResourceUsageRequest(semi.getId(), 1.0)));
        TechnologicalMapResponse mapProduct = createMap(
                "RVW-BOM-D11-P",
                List.of(new ResourceUsageRequest(semi.getId(), SEMI_PER_PRODUCT)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)));

        produce(mapSemi, PRODUCE_AMOUNT * SEMI_PER_PRODUCT + 10);
        ManufacturingItemResponse produced = produce(mapProduct, PRODUCE_AMOUNT);
        relocateProduced(product.getId(), RELOCATE_AMOUNT, produced.getBatchNumber());

        double expected = RELOCATE_AMOUNT * SEMI_PER_PRODUCT * ALC_PER_UNIT;
        assertAmount(fetchSums(List.of(alcohol.getId())), alcohol.getId(), expected);
    }

    @Test(priority = 70)
    @TestCaseId("TC-RVW-BOM-012")
    @Story("BOM depth 3")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Alcohol → SF1 → SF2 → Product")
    public void testBomDepth3() {
        String suffix = uniqueSuffix();
        ResourceResponse alcohol = resourceFixture.createUniqueResource("RVW-ALC-D12-" + suffix);
        ResourceResponse sf1 = resourceFixture.createUniqueResource("RVW-SF1-D12-" + suffix);
        ResourceResponse sf2 = resourceFixture.createUniqueResource("RVW-SF2-D12-" + suffix);
        ResourceResponse product = resourceFixture.createUniqueResource("RVW-P-D12-" + suffix);

        TechnologicalMapResponse mapSf1 = createMap(
                "RVW-BOM-D12-1",
                List.of(new ResourceUsageRequest(alcohol.getId(), ALC_PER_UNIT)),
                List.of(new ResourceUsageRequest(sf1.getId(), 1.0)));
        TechnologicalMapResponse mapSf2 = createMap(
                "RVW-BOM-D12-2",
                List.of(new ResourceUsageRequest(sf1.getId(), SF1_PER_SF2)),
                List.of(new ResourceUsageRequest(sf2.getId(), 1.0)));
        TechnologicalMapResponse mapProduct = createMap(
                "RVW-BOM-D12-3",
                List.of(new ResourceUsageRequest(sf2.getId(), SF2_PER_PRODUCT)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)));

        double needSf2 = PRODUCE_AMOUNT * SF2_PER_PRODUCT + 10;
        double needSf1 = needSf2 * SF1_PER_SF2 + 10;
        produce(mapSf1, needSf1);
        produce(mapSf2, needSf2);
        ManufacturingItemResponse produced = produce(mapProduct, PRODUCE_AMOUNT);
        relocateProduced(product.getId(), RELOCATE_AMOUNT, produced.getBatchNumber());

        double expected = RELOCATE_AMOUNT * SF2_PER_PRODUCT * SF1_PER_SF2 * ALC_PER_UNIT;
        assertAmount(fetchSums(List.of(alcohol.getId())), alcohol.getId(), expected);
    }

    // ───────────────────────────── Extra ─────────────────────────────

    @Test(priority = 80)
    @TestCaseId("TC-RVW-BOM-020")
    @Story("Sum aggregates direct + nested issues")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Пряма видача Alcohol + видача Product зі Alcohol → одна sum = сума шляхів")
    public void testSumAggregatesDirectAndNestedIssues() {
        String suffix = uniqueSuffix();
        ResourceResponse alcohol = resourceFixture.createUniqueResource("RVW-ALC-A-" + suffix);
        ResourceResponse product = resourceFixture.createUniqueResource("RVW-P-A-" + suffix);

        TechnologicalMapResponse map = createMap(
                "RVW-BOM-AGG",
                List.of(new ResourceUsageRequest(alcohol.getId(), ALC_PER_UNIT)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)));

        ManufacturingItemResponse produced = produce(map, PRODUCE_AMOUNT);
        relocateProduced(product.getId(), RELOCATE_AMOUNT, produced.getBatchNumber());

        relocationFixture.ensureStock(productionStorageId, alcohol.getId(), STOCK_PAD, UserRole.ADMIN);
        double directAmount = 4.0;
        relocationFixture.createSend(
                UserRole.ADMIN, productionStorageId, receiverUnitId, alcohol.getId(), directAmount);

        double expected = RELOCATE_AMOUNT * ALC_PER_UNIT + directAmount;
        assertAmount(fetchSums(List.of(alcohol.getId())), alcohol.getId(), expected);
    }

    @Test(priority = 90)
    @TestCaseId("TC-RVW-BOM-021")
    @Story("Mid-level resource as product and ingredient")
    @Severity(SeverityLevel.NORMAL)
    @Description("resourceIds=[Semi]: (a) видача Semi → product row; (b) видача Product → Semi ingredient")
    public void testMidLevelResourceAsProductAndIngredient() {
        String suffix = uniqueSuffix();
        ResourceResponse alcohol = resourceFixture.createUniqueResource("RVW-ALC-MID-" + suffix);
        ResourceResponse semi = resourceFixture.createUniqueResource("RVW-SF-MID-" + suffix);
        ResourceResponse product = resourceFixture.createUniqueResource("RVW-P-MID-" + suffix);

        TechnologicalMapResponse mapSemi = createMap(
                "RVW-BOM-MID-SF",
                List.of(new ResourceUsageRequest(alcohol.getId(), ALC_PER_UNIT)),
                List.of(new ResourceUsageRequest(semi.getId(), 1.0)));
        TechnologicalMapResponse mapProduct = createMap(
                "RVW-BOM-MID-P",
                List.of(new ResourceUsageRequest(semi.getId(), SEMI_PER_PRODUCT)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)));

        produce(mapSemi, PRODUCE_AMOUNT * SEMI_PER_PRODUCT + 10);
        ManufacturingItemResponse producedProduct = produce(mapProduct, PRODUCE_AMOUNT);
        RelocationResponse sentProduct = relocateProduced(
                product.getId(), RELOCATE_AMOUNT, producedProduct.getBatchNumber());

        ManufacturingItemResponse producedSemiForIssue = produce(mapSemi, RELOCATE_AMOUNT + 5);
        RelocationResponse sentSemi = relocateProduced(
                semi.getId(), RELOCATE_AMOUNT, producedSemiForIssue.getBatchNumber());

        List<ResourceRelocationViewerResponse> rows = fetchJournal(List.of(semi.getId()));

        ResourceRelocationViewerResponse semiRow = findByRelocationId(rows, sentSemi.getId());
        assertThat(semiRow.getIsProduct()).as("Semi as tracked root → isProduct=false").isFalse();
        assertThat(semiRow.getProduct().getId()).isEqualTo(semi.getId());

        ResourceRelocationViewerResponse productRow = findByRelocationId(rows, sentProduct.getId());
        assertThat(productRow.getIsProduct()).as("Product containing Semi → isProduct=true").isTrue();
        assertThat(totallyUsageOf(productRow, semi.getId()))
                .isCloseTo(RELOCATE_AMOUNT * SEMI_PER_PRODUCT, within(0.001));

        double expectedSum = RELOCATE_AMOUNT + RELOCATE_AMOUNT * SEMI_PER_PRODUCT;
        assertAmount(fetchSums(List.of(semi.getId())), semi.getId(), expectedSum);
    }

    @Test(priority = 100)
    @TestCaseId("TC-RVW-BOM-022")
    @Story("Negative filter — unrelated product excluded")
    @Severity(SeverityLevel.NORMAL)
    @Description("Product без Alcohol не потрапляє в journal/sum при resourceIds=[Alcohol]")
    public void testUnrelatedProductExcludedByTrackedFilter() {
        String suffix = uniqueSuffix();
        ResourceResponse alcohol = resourceFixture.createUniqueResource("RVW-ALC-N-" + suffix);
        ResourceResponse unrelated = resourceFixture.createUniqueResource("RVW-P-N-" + suffix);

        relocationFixture.ensureStock(productionStorageId, unrelated.getId(), STOCK_PAD, UserRole.ADMIN);
        RelocationResponse sent = relocationFixture.createSend(
                UserRole.ADMIN, productionStorageId, receiverUnitId, unrelated.getId(), RELOCATE_AMOUNT);

        assertAmount(fetchSums(List.of(alcohol.getId())), alcohol.getId(), 0.0);

        List<ResourceRelocationViewerResponse> rows = fetchJournal(List.of(alcohol.getId()));
        assertThat(rows.stream().map(ResourceRelocationViewerResponse::getRelocationId))
                .as("unrelated Product relocation must not appear for Alcohol filter")
                .doesNotContain(sent.getId());
    }

    // ───────────────────────────── Edge cases ─────────────────────────────

    @Test(priority = 110)
    @TestCaseId("TC-RVW-BOM-030")
    @Story("Latest production record wins for a shared batch")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Два записи виробництва одного Product за тією самою техкартою і з однаковим batch number,
            але різними фактичними витратами Alcohol@2 та Alcohol@4.
            Декомпозер використовує лише останній запис: sum = relocate × 4.
            """)
    public void testSharedBatchUsesLatestProductionRecord() {
        String suffix = uniqueSuffix();
        ResourceResponse alcohol = resourceFixture.createUniqueResource("RVW-ALC-BL-" + suffix);
        ResourceResponse product = resourceFixture.createUniqueResource("RVW-P-BL-" + suffix);

        TechnologicalMapResponse map = createMap(
                "RVW-BOM-BL",
                List.of(new ResourceUsageRequest(alcohol.getId(), 2.0)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)));

        String sharedBatch = "BL-" + suffix;
        double qtyLow = 4.0;
        double qtyHigh = 6.0;
        productionFixture.ensureStockForTechMapInputs(productionStorageId, map, STOCK_PAD);
        productionFixture.createAsWithInputAmounts(
                UserRole.ADMIN, productionStorageId, map, qtyLow, sharedBatch,
                Map.of(alcohol.getId(), 2.0));
        productionFixture.createAsWithInputAmounts(
                UserRole.ADMIN, productionStorageId, map, qtyHigh, sharedBatch,
                Map.of(alcohol.getId(), 4.0));

        double relocateTotal = qtyLow + qtyHigh;
        RelocationResponse sent = relocateProduced(product.getId(), relocateTotal, sharedBatch);

        double expected = relocateTotal * 4.0;
        assertAmount(fetchSums(List.of(alcohol.getId())), alcohol.getId(), expected);

        ResourceRelocationViewerResponse row = findByRelocationId(
                fetchJournal(List.of(alcohol.getId())), sent.getId());
        assertThat(totallyUsageOf(row, alcohol.getId())).isCloseTo(expected, within(0.001));
    }

    @Test(priority = 120)
    @TestCaseId("TC-RVW-BOM-031")
    @Story("Inventory-created product uses historical tech map")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Інвентаризація створює службову партію «Без №»; FIFO-видача розкладається за картою, що існувала на дату переміщення")
    public void testInventoryCreatedProductFallsBackToHistoricalTechMap() {
        String suffix = uniqueSuffix();
        ResourceResponse alcohol = resourceFixture.createUniqueResource("RVW-ALC-NB-" + suffix);
        ResourceResponse product = resourceFixture.createUniqueResource("RVW-P-NB-" + suffix);

        createMap(
                "RVW-BOM-NB",
                List.of(new ResourceUsageRequest(alcohol.getId(), ALC_PER_UNIT)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)));

        createInventoryStock(product.getId(), 10.0);
        double movedAmount = 4.0;
        LocalDate movementDate = LocalDate.now();
        RelocationResponse sent = sendWithoutBatchAndDate(product.getId(), movedAmount, movementDate);

        Map<String, Object> params = viewerParams(List.of(alcohol.getId()));
        params.put("end", movementDate.plusDays(1).toString());
        double expected = movedAmount * ALC_PER_UNIT;
        assertAmount(fetchSumsWithParams(params), alcohol.getId(), expected);

        ResourceRelocationViewerResponse row = findByRelocationId(
                fetchJournalWithParams(params), sent.getId());
        assertThat(row.getIsProduct()).isTrue();
        assertThat(totallyUsageOf(row, alcohol.getId())).isCloseTo(expected, within(0.001));
    }

    @Test(priority = 130)
    @TestCaseId("TC-RVW-BOM-032")
    @Story("Historical tech map version by relocation date")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Інвентаризаційний залишок без виробничих фактів.
            Для кожного переміщення використовується найновіша техкарта,
            яка вже існувала на його дату; новіші карти не змінюють історію.
            """)
    public void testTechMapLookupByRelocationDate() {
        requireDatabase("TC-RVW-BOM-032 потребує БД для керування історичними датами техкарт");

        String suffix = uniqueSuffix();
        ResourceResponse alcohol = resourceFixture.createUniqueResource("RVW-ALC-DT-" + suffix);
        ResourceResponse product = resourceFixture.createUniqueResource("RVW-P-DT-" + suffix);

        createInventoryStock(product.getId(), 20.0);

        TechnologicalMapResponse mapV1 = createMap(
                "RVW-BOM-DT-V1",
                List.of(new ResourceUsageRequest(alcohol.getId(), 2.0)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)));
        TechnologicalMapResponse mapV2 = createMap(
                "RVW-BOM-DT-V2",
                List.of(new ResourceUsageRequest(alcohol.getId(), 5.0)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)));

        LocalDate mapV1Date = LocalDate.now().minusDays(20);
        LocalDate firstMovementDate = LocalDate.now().minusDays(15);
        LocalDate mapV2Date = LocalDate.now().minusDays(10);
        LocalDate secondMovementDate = LocalDate.now().minusDays(5);
        backdateTechMap(mapV1.getId(), mapV1Date);
        backdateTechMap(mapV2.getId(), mapV2Date);

        double movedAmount = 4.0;
        RelocationResponse sentV1 = sendWithoutBatchAndDate(
                product.getId(), movedAmount, firstMovementDate);
        RelocationResponse sentV2 = sendWithoutBatchAndDate(
                product.getId(), movedAmount, secondMovementDate);

        Map<String, Object> params = viewerParams(List.of(alcohol.getId()));
        params.put("start", firstMovementDate.minusDays(1).toString());
        params.put("end", secondMovementDate.plusDays(1).toString());
        List<ResourceRelocationViewerResponse> rows = fetchJournalWithParams(params);
        assertThat(totallyUsageOf(findByRelocationId(rows, sentV1.getId()), alcohol.getId()))
                .as("перше переміщення використовує V1")
                .isCloseTo(movedAmount * 2.0, within(0.001));
        assertThat(totallyUsageOf(findByRelocationId(rows, sentV2.getId()), alcohol.getId()))
                .as("друге переміщення використовує V2")
                .isCloseTo(movedAmount * 5.0, within(0.001));

        techMapFixture.deactivateTechMap(UserRole.ADMIN, mapV1.getId(), productionStorageId);
        techMapFixture.deactivateTechMap(UserRole.ADMIN, mapV2.getId(), productionStorageId);
        createMap(
                "RVW-BOM-DT-V3",
                List.of(new ResourceUsageRequest(alcohol.getId(), 9.0)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)));
        Map<String, Object> afterNewMap = new HashMap<>(params);
        afterNewMap.put("end", secondMovementDate.plusDays(2).toString());
        List<ResourceRelocationViewerResponse> unchangedRows = fetchJournalWithParams(afterNewMap);
        assertThat(totallyUsageOf(findByRelocationId(unchangedRows, sentV1.getId()), alcohol.getId()))
                .as("архівація V1/V2 і створення V3 не змінюють перше історичне переміщення")
                .isCloseTo(movedAmount * 2.0, within(0.001));
        assertThat(totallyUsageOf(findByRelocationId(unchangedRows, sentV2.getId()), alcohol.getId()))
                .as("архівація V1/V2 і створення V3 не змінюють друге історичне переміщення")
                .isCloseTo(movedAmount * 5.0, within(0.001));
    }

    @Test(priority = 135)
    @TestCaseId("TC-RVW-BOM-035")
    @Story("Resource moved before its first tech map remains atomic")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Пізніше створена техкарта не застосовується до інвентаризаційного залишку, переміщеного до її появи")
    public void testMovementBeforeFirstTechMapRemainsAtomic() {
        String suffix = uniqueSuffix();
        ResourceResponse component = resourceFixture.createUniqueResource("RVW-ALC-PRE-TM-" + suffix);
        ResourceResponse resource = resourceFixture.createUniqueResource("RVW-P-PRE-TM-" + suffix);

        double movedAmount = 5.0;
        createInventoryStock(resource.getId(), movedAmount);
        RelocationResponse sent = sendWithoutBatchAndDate(
                resource.getId(), movedAmount, LocalDate.now());

        createMap(
                "RVW-BOM-AFTER-MOVE",
                List.of(new ResourceUsageRequest(component.getId(), ALC_PER_UNIT)),
                List.of(new ResourceUsageRequest(resource.getId(), 1.0)));

        Map<String, Object> componentParams = viewerParams(List.of(component.getId()));
        componentParams.put("end", LocalDate.now().plusDays(1).toString());
        assertThat(fetchJournalWithParams(componentParams).stream()
                .map(ResourceRelocationViewerResponse::getRelocationId))
                .doesNotContain(sent.getId());
        assertAmount(fetchSumsWithParams(componentParams), component.getId(), 0.0);

        Map<String, Object> resourceParams = viewerParams(List.of(resource.getId()));
        resourceParams.put("end", LocalDate.now().plusDays(1).toString());
        List<ResourceRelocationViewerResponse> rows = rowsForRelocation(
                fetchJournalWithParams(resourceParams), sent.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getAmount().doubleValue()).isCloseTo(movedAmount, within(0.001));
        assertThat(rows.getFirst().getIsProduct()).as("до першої техкарти ресурс атомарний").isFalse();
    }

    @Test(priority = 140)
    @TestCaseId("TC-RVW-BOM-033")
    @Story("Recipe cycle does not hang or double-count")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Цикл техкарт A←B←C←A: видача A без production → expand повертає A-B-C,
            зупиняється перед повторним A та не дублює жоден компонент.
            """)
    public void testRecipeCycleDoesNotInfiniteExpand() {
        String suffix = uniqueSuffix();
        ResourceResponse resourceA = resourceFixture.createUniqueResource("RVW-CYC-A-" + suffix);
        ResourceResponse resourceB = resourceFixture.createUniqueResource("RVW-CYC-B-" + suffix);
        ResourceResponse resourceC = resourceFixture.createUniqueResource("RVW-CYC-C-" + suffix);

        createMap(
                "RVW-BOM-CYC-A",
                List.of(new ResourceUsageRequest(resourceB.getId(), 1.0)),
                List.of(new ResourceUsageRequest(resourceA.getId(), 1.0)));
        createMap(
                "RVW-BOM-CYC-B",
                List.of(new ResourceUsageRequest(resourceC.getId(), 1.0)),
                List.of(new ResourceUsageRequest(resourceB.getId(), 1.0)));
        createMap(
                "RVW-BOM-CYC-C",
                List.of(new ResourceUsageRequest(resourceA.getId(), 1.0)),
                List.of(new ResourceUsageRequest(resourceC.getId(), 1.0)));

        relocationFixture.ensureStock(productionStorageId, resourceA.getId(), STOCK_PAD, UserRole.ADMIN);
        RelocationResponse sent = relocationFixture.createSend(
                UserRole.ADMIN, productionStorageId, receiverUnitId, resourceA.getId(), RELOCATE_AMOUNT);

        List<ResourceRelocationViewerResponse> rows = fetchJournal(
                List.of(resourceA.getId(), resourceB.getId(), resourceC.getId()));
        ResourceRelocationViewerResponse row = findByRelocationId(rows, sent.getId());
        assertThat(row.getIsProduct()).isTrue();
        assertThat(row.getIngredients())
                .extracting(ResourceRelocationViewerResponse.ResourceIngredientResponse::getResourceId)
                .as("цикл A-B-C-A повертає A-B-C по одному разу")
                .containsExactlyInAnyOrder(resourceA.getId(), resourceB.getId(), resourceC.getId());
        assertThat(totallyUsageOf(row, resourceB.getId()))
                .as("B з'являється один раз з usage=1 × amount")
                .isCloseTo(RELOCATE_AMOUNT, within(0.001));
        assertThat(totallyUsageOf(row, resourceC.getId()))
                .as("C з'являється один раз з usage=1 × amount")
                .isCloseTo(RELOCATE_AMOUNT, within(0.001));
        assertThat(totallyUsageOf(row, resourceA.getId()))
                .as("кореневий A присутній у циклі один раз")
                .isCloseTo(RELOCATE_AMOUNT, within(0.001));
        List<ResourceRelocationSumViewerResponse> sums = fetchSums(
                List.of(resourceA.getId(), resourceB.getId(), resourceC.getId()));
        assertAmount(sums, resourceA.getId(), RELOCATE_AMOUNT * 2.0);
        assertAmount(sums, resourceB.getId(), RELOCATE_AMOUNT);
        assertAmount(sums, resourceC.getId(), RELOCATE_AMOUNT);
    }

    // ───────────────────────────── Helpers ─────────────────────────────

    private TechnologicalMapResponse createMap(
            String namePrefix,
            List<ResourceUsageRequest> inputs,
            List<ResourceUsageRequest> outputs) {
        TechnologicalMapRequest request = TechnologicalMapDataFactory
                .createProductionMapWithStorages(namePrefix, inputs, outputs, Set.of(productionStorageId))
                .build();
        TechnologicalMapResponse map = Allure.step(
                "Створити техкарту " + namePrefix,
                () -> techMapFixture.createTechMapWithRequest(UserRole.ADMIN, request));
        createdMaps.add(map);
        return map;
    }

    private TechnologicalMapResponse createMapWithAlternativeGroup(
            String namePrefix,
            ResourceResponse fixed,
            ResourceResponse defaultAlt,
            ResourceResponse otherAlt,
            ResourceResponse product) {
        var group = TechnologicalMapDataFactory.alternativeGroup(
                namePrefix + "-ALT",
                TechnologicalMapDataFactory.alternativeResource(defaultAlt.getId(), 2.0, true),
                TechnologicalMapDataFactory.alternativeResource(otherAlt.getId(), 3.0, false));
        TechnologicalMapRequest request = TechnologicalMapDataFactory
                .createProductionMapWithStorages(
                        namePrefix,
                        List.of(new ResourceUsageRequest(fixed.getId(), 1.0)),
                        List.of(new ResourceUsageRequest(product.getId(), 1.0)),
                        Set.of(productionStorageId))
                .groups(List.of(group))
                .build();
        TechnologicalMapResponse map = Allure.step(
                "Створити техкарту " + namePrefix + " з default alternative",
                () -> techMapFixture.createTechMapWithRequest(UserRole.ADMIN, request));
        createdMaps.add(map);
        return map;
    }

    private ManufacturingItemResponse produce(TechnologicalMapResponse map, double amount) {
        return Allure.step("Виробництво " + amount + " за TM " + map.getId(), () -> {
            productionFixture.ensureStockForTechMapInputs(productionStorageId, map, STOCK_PAD);
            ManufacturingItemResponse created = productionFixture.createWithUniqueBatch(
                    UserRole.ADMIN, productionStorageId, map, amount);
            assertThat(created.getBatchNumber()).isNotBlank();
            return created;
        });
    }

    private RelocationResponse relocateProduced(Long productId, double amount, String batchNumber) {
        return Allure.step("Видача продукції партії " + batchNumber, () ->
                relocationFixture.createSendWithBatch(
                        UserRole.ADMIN,
                        productionStorageId,
                        receiverUnitId,
                        productId,
                        amount,
                        batchNumber,
                        true));
    }

    private RelocationResponse sendWithoutBatchAndDate(
            Long productId, double amount, LocalDate date) {
        return Allure.step("FIFO-видача ресурсу " + productId + " датою " + date, () -> {
            var request = com.erp.data.factories.relocation.RelocationDataFactory
                    .buildSendRequest(productionStorageId, receiverUnitId, productId, amount)
                    .toBuilder()
                    .date(date)
                    .build();
            Response response = apiExecutor.execute(
                    ApiEndpointDefinition.RELOCATION_POST_SEND, UserRole.ADMIN, request);
            assertThat(response.statusCode()).isEqualTo(200);
            return response.as(RelocationResponse.class);
        });
    }

    private void createInventoryStock(Long resourceId, double amount) {
        Allure.step("Інвентаризація: створити службовий залишок ресурсу " + resourceId, () -> {
            inventoryFixture.ensureClosed(productionStorageId);
            inventoryFixture.openSession(productionStorageId);
            try {
                inventoryFixture.setResourceAmount(
                        productionStorageId, UserRole.ADMIN, resourceId, amount);
            } finally {
                inventoryFixture.closeSession(productionStorageId);
            }
            assertThat(inventoryFixture.getResourceStock(
                    productionStorageId, resourceId, UserRole.ADMIN))
                    .isCloseTo(amount, within(0.001));
        });
    }

    private void requireDatabase(String reason) {
        if (getDbHelper() == null) {
            throw new SkipException(reason + " (use.database=true)");
        }
    }

    private void backdateTechMap(Long mapId, LocalDate date) {
        Allure.step("DB: встановити історичну дату техкарти " + mapId + " = " + date, () -> {
            String metadataSql = """
                    SELECT c.table_schema, c.table_name, c.column_name
                    FROM information_schema.columns c
                    WHERE c.column_name IN ('date_time', 'created_at')
                      AND lower(c.table_name) LIKE '%technological%map%'
                      AND EXISTS (
                          SELECT 1 FROM information_schema.columns idc
                          WHERE idc.table_schema = c.table_schema
                            AND idc.table_name = c.table_name
                            AND idc.column_name = 'id'
                      )
                    """;
            int updated = 0;
            try (PreparedStatement metadata = getDbHelper().getConnection().prepareStatement(metadataSql);
                 ResultSet columns = metadata.executeQuery()) {
                while (columns.next()) {
                    String schema = columns.getString("table_schema");
                    String table = columns.getString("table_name");
                    String column = columns.getString("column_name");
                    if (!safeIdentifier(schema) || !safeIdentifier(table) || !safeIdentifier(column)) {
                        continue;
                    }
                    String updateSql = "UPDATE \"%s\".\"%s\" SET \"%s\" = ? WHERE id = ?"
                            .formatted(schema, table, column);
                    try (PreparedStatement update = getDbHelper().getConnection().prepareStatement(updateSql)) {
                        LocalDateTime noon = date.atTime(12, 0);
                        update.setTimestamp(1, Timestamp.valueOf(noon));
                        update.setLong(2, mapId);
                        updated += update.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException(
                        "Не вдалося змінити історичну дату техкарти id=" + mapId, e);
            }
            assertThat(updated)
                    .as("має бути оновлено date_time/created_at для tech map id=%s", mapId)
                    .isPositive();
        });
    }

    private static boolean safeIdentifier(String value) {
        return value != null && value.matches("[A-Za-z0-9_]+");
    }

    private List<ResourceRelocationSumViewerResponse> fetchSums(List<Long> resourceIds) {
        return fetchSumsWithParams(viewerParams(resourceIds));
    }

    private List<ResourceRelocationViewerResponse> fetchJournal(List<Long> resourceIds) {
        return fetchJournalWithParams(viewerParams(resourceIds));
    }

    private List<ResourceRelocationSumViewerResponse> fetchSumsWithParams(Map<String, Object> params) {
        return Allure.step("GET relocations → sums як wolf params=" + params, () -> {
            Response response = apiExecutor.executeWithQueryParams(
                    ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_GET,
                    UserRole.RESOURCE_VIEWER,
                    params);
            assertThat(response.statusCode()).isEqualTo(200);
            SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_GET);
            PagedResourceRelocationViewerResponse page = response.as(PagedResourceRelocationViewerResponse.class);
            return page.getSums() != null ? page.getSums() : List.of();
        });
    }

    private List<ResourceRelocationViewerResponse> fetchJournalWithParams(Map<String, Object> params) {
        return Allure.step("GET relocations journal як wolf params=" + params, () -> {
            Response response = apiExecutor.executeWithQueryParams(
                    ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_GET,
                    UserRole.RESOURCE_VIEWER,
                    params);
            assertThat(response.statusCode()).isEqualTo(200);
            SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_GET);
            PagedResourceRelocationViewerResponse page = response.as(PagedResourceRelocationViewerResponse.class);
            return page.getContent() != null ? page.getContent() : List.of();
        });
    }

    private Map<String, Object> viewerParams(List<Long> resourceIds) {
        Map<String, Object> params = new HashMap<>();
        params.put("resourceIds", resourceIds);
        params.put("receiverIds", receiverUnitId);
        return params;
    }

    private static void assertAmount(
            List<ResourceRelocationSumViewerResponse> sums, Long resourceId, double expected) {
        Allure.step("Assert sum for resourceId=" + resourceId + " == " + expected, () ->
                assertThat(amountOf(sums, resourceId))
                        .as("sum for resourceId=%s", resourceId)
                        .isCloseTo(expected, within(0.001)));
    }

    private static double amountOf(List<ResourceRelocationSumViewerResponse> sums, Long resourceId) {
        return sums.stream()
                .filter(s -> resourceId.equals(s.getResourceId()))
                .map(ResourceRelocationSumViewerResponse::getAmount)
                .filter(a -> a != null)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();
    }

    private static ResourceRelocationViewerResponse findByRelocationId(
            List<ResourceRelocationViewerResponse> rows, Long relocationId) {
        return rows.stream()
                .filter(r -> relocationId.equals(r.getRelocationId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Relocation " + relocationId + " not found in wolf journal"));
    }

    private static List<ResourceRelocationViewerResponse> rowsForRelocation(
            List<ResourceRelocationViewerResponse> rows, Long relocationId) {
        return rows.stream()
                .filter(r -> relocationId.equals(r.getRelocationId()))
                .toList();
    }

    private static double totallyUsageOf(ResourceRelocationViewerResponse row, Long resourceId) {
        if (row.getIngredients() == null) {
            return 0.0;
        }
        return row.getIngredients().stream()
                .filter(i -> resourceId.equals(i.getResourceId()))
                .map(ResourceRelocationViewerResponse.ResourceIngredientResponse::getTotallyUsage)
                .filter(u -> u != null)
                .map(u -> u.setScale(6, RoundingMode.HALF_UP))
                .mapToDouble(BigDecimal::doubleValue)
                .sum();
    }

    private static String uniqueSuffix() {
        return String.valueOf(System.currentTimeMillis());
    }
}
