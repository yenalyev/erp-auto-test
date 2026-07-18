package com.erp.tests.functional.resource_viewer;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
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
import com.erp.utils.config.ConfigProvider;
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
import java.sql.SQLException;
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

        techMapFixture.prepareContext();
        resourceFixture.prepareContext();
        relocationFixture.prepareContext();

        productionStorageId = ConfigProvider.getOwner1StorageId();
        // Resource Viewer forces recipient UnitType.UNIT — owner2 storage may not be UNIT on env.
        receiverUnitId = relocationFixture.resolveUnitStorageId(UserRole.ADMIN);
        techMapFixture.setMode(productionStorageId, StorageTechnologicalMapMode.EDIT_ALLOWED);
        SchemaRegistry.logSchemaCoverage();
        log.info("BOM suite storages: production={}, receiverUnit={}", productionStorageId, receiverUnitId);
    }

    @AfterClass(alwaysRun = true)
    public void teardown() {
        for (TechnologicalMapResponse map : createdMaps) {
            try {
                techMapFixture.deactivateTechMap(UserRole.OWNER_1, map.getId(), productionStorageId);
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

        ManufacturingItemResponse produced = produce(map, PRODUCE_AMOUNT);
        RelocationResponse sent = relocateProduced(product.getId(), RELOCATE_AMOUNT, produced.getBatchNumber());

        double expected = RELOCATE_AMOUNT * ALC_PER_UNIT;
        assertAmount(fetchSums(List.of(alcohol.getId())), alcohol.getId(), expected);

        ResourceRelocationViewerResponse row = findByRelocationId(
                fetchJournal(List.of(alcohol.getId())), sent.getId());
        assertThat(row.getIsProduct()).isTrue();
        assertThat(row.getProduct().getId()).isEqualTo(product.getId());
        assertThat(totallyUsageOf(row, alcohol.getId())).isCloseTo(expected, within(0.001));
    }

    @Test(priority = 30)
    @TestCaseId("TC-RVW-BOM-003")
    @Story("External finished good — tech map fallback")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Product без локального production: seed batch ззовні → relocate isProduced=false → BOM з tech map")
    public void testExternalFinishedGoodTechMapFallback() {
        String suffix = uniqueSuffix();
        ResourceResponse alcohol = resourceFixture.createUniqueResource("RVW-ALC-X-" + suffix);
        ResourceResponse product = resourceFixture.createUniqueResource("RVW-P-X-" + suffix);

        createMap(
                "RVW-BOM-EXT",
                List.of(new ResourceUsageRequest(alcohol.getId(), ALC_PER_UNIT)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)));

        String externalBatch = "EXT-BOM-" + suffix;
        relocationFixture.seedBatchOnStorage(
                productionStorageId, product.getId(), RELOCATE_AMOUNT + 10, externalBatch);

        RelocationResponse sent = relocationFixture.createSendWithBatch(
                UserRole.ADMIN,
                productionStorageId,
                receiverUnitId,
                product.getId(),
                RELOCATE_AMOUNT,
                externalBatch,
                false);

        double expected = RELOCATE_AMOUNT * ALC_PER_UNIT;
        List<ResourceRelocationViewerResponse> rows = fetchJournal(List.of(alcohol.getId()));
        ResourceRelocationViewerResponse row = findByRelocationId(rows, sent.getId());
        assertThat(row.getIsProduct()).isTrue();
        assertThat(totallyUsageOf(row, alcohol.getId()))
                .as("tech-map fallback must explode Alcohol for external Product batch")
                .isCloseTo(expected, within(0.001));
        assertAmount(fetchSums(List.of(alcohol.getId())), alcohol.getId(), expected);
    }

    @Test(priority = 40)
    @TestCaseId("TC-RVW-BOM-004")
    @Story("Mixed produced + ready-made scale path")
    @Severity(SeverityLevel.NORMAL)
    @Description("Produce 5 + supplier 5 → relocate 10 з партіями; scale path: sum = 10×usage (не лише 5×)")
    public void testMixedProducedAndReadyMadeScalePath() {
        String suffix = uniqueSuffix();
        ResourceResponse alcohol = resourceFixture.createUniqueResource("RVW-ALC-M-" + suffix);
        ResourceResponse product = resourceFixture.createUniqueResource("RVW-P-M-" + suffix);

        TechnologicalMapResponse map = createMap(
                "RVW-BOM-MIX",
                List.of(new ResourceUsageRequest(alcohol.getId(), ALC_PER_UNIT)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)));

        ManufacturingItemResponse produced = produce(map, PRODUCE_AMOUNT);
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

        double expected = relocateTotal * ALC_PER_UNIT; // scale: not just PRODUCE_AMOUNT * usage
        assertAmount(fetchSums(List.of(alcohol.getId())), alcohol.getId(), expected);

        ResourceRelocationViewerResponse row = findByRelocationId(
                fetchJournal(List.of(alcohol.getId())), sent.getId());
        assertThat(totallyUsageOf(row, alcohol.getId())).isCloseTo(expected, within(0.001));
    }

    // ───────────────────────────── Depth ─────────────────────────────

    @Test(priority = 50)
    @TestCaseId("TC-RVW-BOM-010")
    @Story("BOM depth 1")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Alcohol → Product (depth 1)")
    public void testBomDepth1() {
        String suffix = uniqueSuffix();
        ResourceResponse alcohol = resourceFixture.createUniqueResource("RVW-ALC-D10-" + suffix);
        ResourceResponse product = resourceFixture.createUniqueResource("RVW-P-D10-" + suffix);

        TechnologicalMapResponse map = createMap(
                "RVW-BOM-D10",
                List.of(new ResourceUsageRequest(alcohol.getId(), ALC_PER_UNIT)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)));

        ManufacturingItemResponse produced = produce(map, PRODUCE_AMOUNT);
        relocateProduced(product.getId(), RELOCATE_AMOUNT, produced.getBatchNumber());

        assertAmount(fetchSums(List.of(alcohol.getId())), alcohol.getId(),
                RELOCATE_AMOUNT * ALC_PER_UNIT);
    }

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
    @Story("Blend multiple productions into one batch")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Два виробництва одного Product з однаковим batch number і різними рецептами
            (Alcohol@2 на 4 од. + Alcohol@4 на 6 од.) → per-unit = зважене середнє 3.2,
            не сума рецептів. sum = relocate × 3.2.
            """)
    public void testBlendedBatchRecipesWeightedAverage() {
        String suffix = uniqueSuffix();
        ResourceResponse alcohol = resourceFixture.createUniqueResource("RVW-ALC-BL-" + suffix);
        ResourceResponse product = resourceFixture.createUniqueResource("RVW-P-BL-" + suffix);

        TechnologicalMapResponse mapLow = createMap(
                "RVW-BOM-BL-LO",
                List.of(new ResourceUsageRequest(alcohol.getId(), 2.0)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)));
        TechnologicalMapResponse mapHigh = createMap(
                "RVW-BOM-BL-HI",
                List.of(new ResourceUsageRequest(alcohol.getId(), 4.0)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)));

        String sharedBatch = "BL-" + java.time.LocalDate.now();
        double qtyLow = 4.0;
        double qtyHigh = 6.0;
        produceWithBatch(mapLow, qtyLow, sharedBatch);
        produceWithBatch(mapHigh, qtyHigh, sharedBatch);

        double relocateTotal = qtyLow + qtyHigh;
        RelocationResponse sent = relocateProduced(product.getId(), relocateTotal, sharedBatch);

        double expectedPerUnit = (qtyLow * 2.0 + qtyHigh * 4.0) / relocateTotal; // 3.2
        double expected = relocateTotal * expectedPerUnit;
        assertAmount(fetchSums(List.of(alcohol.getId())), alcohol.getId(), expected);

        ResourceRelocationViewerResponse row = findByRelocationId(
                fetchJournal(List.of(alcohol.getId())), sent.getId());
        assertThat(totallyUsageOf(row, alcohol.getId())).isCloseTo(expected, within(0.001));
    }

    @Test(priority = 120)
    @TestCaseId("TC-RVW-BOM-031")
    @Story("Item without batches — tech map fallback")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Видача Product без партій → resolveBatchWeights порожній → BOM з tech map на весь обсяг")
    public void testItemWithoutBatchesFallsBackToTechMap() {
        String suffix = uniqueSuffix();
        ResourceResponse alcohol = resourceFixture.createUniqueResource("RVW-ALC-NB-" + suffix);
        ResourceResponse product = resourceFixture.createUniqueResource("RVW-P-NB-" + suffix);

        createMap(
                "RVW-BOM-NB",
                List.of(new ResourceUsageRequest(alcohol.getId(), ALC_PER_UNIT)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)));

        relocationFixture.ensureStock(productionStorageId, product.getId(), STOCK_PAD, UserRole.ADMIN);
        RelocationResponse sent = relocationFixture.createSend(
                UserRole.ADMIN, productionStorageId, receiverUnitId, product.getId(), RELOCATE_AMOUNT);

        double expected = RELOCATE_AMOUNT * ALC_PER_UNIT;
        assertAmount(fetchSums(List.of(alcohol.getId())), alcohol.getId(), expected);

        ResourceRelocationViewerResponse row = findByRelocationId(
                fetchJournal(List.of(alcohol.getId())), sent.getId());
        assertThat(row.getIsProduct()).isTrue();
        assertThat(totallyUsageOf(row, alcohol.getId())).isCloseTo(expected, within(0.001));
    }

    @Test(priority = 130)
    @TestCaseId("TC-RVW-BOM-032")
    @Story("Tech map version by relocation date")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Історичний вибір техкарти (на дату видачі). LocalDate → startOfDay UTC:
            карти, створені «сьогодні», мають createdAt після startOfDay «сьогодні».
            Тому: дата вчора (обидві карти ще «не існували») → fallback MIN(id)=V1 (usage 2);
            дата завтра (обидві вже існували) → newest V2 (usage 5).
            """)
    public void testTechMapLookupByRelocationDate() {
        String suffix = uniqueSuffix();
        ResourceResponse alcohol = resourceFixture.createUniqueResource("RVW-ALC-DT-" + suffix);
        ResourceResponse product = resourceFixture.createUniqueResource("RVW-P-DT-" + suffix);

        createMap(
                "RVW-BOM-DT-V1",
                List.of(new ResourceUsageRequest(alcohol.getId(), 2.0)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)));
        try {
            Thread.sleep(1_200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        createMap(
                "RVW-BOM-DT-V2",
                List.of(new ResourceUsageRequest(alcohol.getId(), 5.0)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)));

        String pastBatch = "EXT-PAST-" + suffix;
        String futureBatch = "EXT-FUT-" + suffix;
        relocationFixture.seedBatchOnStorage(
                productionStorageId, product.getId(), RELOCATE_AMOUNT + 5, pastBatch);
        relocationFixture.seedBatchOnStorage(
                productionStorageId, product.getId(), RELOCATE_AMOUNT + 5, futureBatch);

        java.time.LocalDate yesterday = java.time.LocalDate.now().minusDays(1);
        java.time.LocalDate tomorrow = java.time.LocalDate.now().plusDays(1);

        RelocationResponse sentPast = sendWithBatchAndDate(
                product.getId(), RELOCATE_AMOUNT, pastBatch, yesterday);
        RelocationResponse sentFuture = sendWithBatchAndDate(
                product.getId(), RELOCATE_AMOUNT, futureBatch, tomorrow);

        List<ResourceRelocationViewerResponse> rows = fetchJournal(List.of(alcohol.getId()));
        assertThat(totallyUsageOf(findByRelocationId(rows, sentPast.getId()), alcohol.getId()))
                .as("дата до createdAt обох карт → MIN(id)=V1, usage=2")
                .isCloseTo(RELOCATE_AMOUNT * 2.0, within(0.001));
        assertThat(totallyUsageOf(findByRelocationId(rows, sentFuture.getId()), alcohol.getId()))
                .as("дата після createdAt обох карт → newest V2, usage=5")
                .isCloseTo(RELOCATE_AMOUNT * 5.0, within(0.001));
    }

    @Test(priority = 140)
    @TestCaseId("TC-RVW-BOM-033")
    @Story("Recipe cycle does not hang or double-count")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Цикл техкарт A←B←A: видача A без production → expand зупиняється на path,
            ingredient B враховується один раз (не infinite / не дубль).
            """)
    public void testRecipeCycleDoesNotInfiniteExpand() {
        String suffix = uniqueSuffix();
        ResourceResponse resourceA = resourceFixture.createUniqueResource("RVW-CYC-A-" + suffix);
        ResourceResponse resourceB = resourceFixture.createUniqueResource("RVW-CYC-B-" + suffix);

        createMap(
                "RVW-BOM-CYC-A",
                List.of(new ResourceUsageRequest(resourceB.getId(), 1.0)),
                List.of(new ResourceUsageRequest(resourceA.getId(), 1.0)));
        createMap(
                "RVW-BOM-CYC-B",
                List.of(new ResourceUsageRequest(resourceA.getId(), 1.0)),
                List.of(new ResourceUsageRequest(resourceB.getId(), 1.0)));

        relocationFixture.ensureStock(productionStorageId, resourceA.getId(), STOCK_PAD, UserRole.ADMIN);
        RelocationResponse sent = relocationFixture.createSend(
                UserRole.ADMIN, productionStorageId, receiverUnitId, resourceA.getId(), RELOCATE_AMOUNT);

        List<ResourceRelocationViewerResponse> rows = fetchJournal(List.of(resourceB.getId()));
        ResourceRelocationViewerResponse row = findByRelocationId(rows, sent.getId());
        assertThat(row.getIsProduct()).isTrue();
        assertThat(totallyUsageOf(row, resourceB.getId()))
                .as("B з'являється один раз з usage=1 × amount")
                .isCloseTo(RELOCATE_AMOUNT, within(0.001));
        assertAmount(fetchSums(List.of(resourceB.getId())), resourceB.getId(), RELOCATE_AMOUNT);
    }

    @Test(priority = 150)
    @TestCaseId("TC-RVW-BOM-034")
    @Story("Scale-down when batch amounts overshoot moved amount")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Legacy drift: сума партій (producedQty) > amount рядка видачі.
            API зараз відхиляє такий mismatch; симулюємо через DB UPDATE amount вниз.
            Очікування: sum alcohol = amount × usage (scale-down), не producedQty × usage.
            """)
    public void testScaleDownWhenProducedQtyExceedsMovedAmount() {
        if (getDbHelper() == null) {
            throw new SkipException(
                    "TC-RVW-BOM-034 потребує БД для симуляції legacy amount < batch sum "
                            + "(use.database=true або -Denv=local)");
        }

        String suffix = uniqueSuffix();
        ResourceResponse alcohol = resourceFixture.createUniqueResource("RVW-ALC-SD-" + suffix);
        ResourceResponse product = resourceFixture.createUniqueResource("RVW-P-SD-" + suffix);

        TechnologicalMapResponse map = createMap(
                "RVW-BOM-SD",
                List.of(new ResourceUsageRequest(alcohol.getId(), ALC_PER_UNIT)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)));

        ManufacturingItemResponse produced = produce(map, PRODUCE_AMOUNT);
        RelocationResponse sent = relocateProduced(product.getId(), PRODUCE_AMOUNT, produced.getBatchNumber());

        double driftedAmount = PRODUCE_AMOUNT / 2.0; // 2.5; batches still claim 5
        shrinkRelocationItemAmount(sent.getId(), driftedAmount);

        double expected = driftedAmount * ALC_PER_UNIT; // 5.0, not 10.0
        // Unique end busts ResourceViewer BomKey soft-cache after DB mutate.
        Map<String, Object> params = viewerParams(List.of(alcohol.getId()));
        params.put("end", java.time.LocalDate.now().plusDays(1).toString());

        assertAmount(fetchSumsWithParams(params), alcohol.getId(), expected);

        ResourceRelocationViewerResponse row = findByRelocationId(
                fetchJournalWithParams(params), sent.getId());
        assertThat(totallyUsageOf(row, alcohol.getId()))
                .as("scale-down: totallyUsage = driftedAmount × usage")
                .isCloseTo(expected, within(0.001));
        assertThat(row.getAmount().doubleValue())
                .as("journal amount reflects drifted item amount")
                .isCloseTo(driftedAmount, within(0.001));
    }

    private void shrinkRelocationItemAmount(Long relocationId, double newAmount) {
        Allure.step(
                "DB: UPDATE relocation_item.amount=" + newAmount + " WHERE relocation_id=" + relocationId,
                () -> {
                    String sql = "UPDATE relocation_item SET amount = ? WHERE relocation_id = ?";
                    try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(sql)) {
                        ps.setBigDecimal(1, BigDecimal.valueOf(newAmount));
                        ps.setLong(2, relocationId);
                        int updated = ps.executeUpdate();
                        assertThat(updated)
                                .as("має оновитись ≥1 рядок relocation_item для relocation=%s", relocationId)
                                .isGreaterThanOrEqualTo(1);
                    } catch (SQLException e) {
                        throw new IllegalStateException(
                                "Не вдалося зменшити amount relocation_item id="
                                        + relocationId + ": " + e.getMessage(), e);
                    }
                });
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

    private ManufacturingItemResponse produce(TechnologicalMapResponse map, double amount) {
        return Allure.step("Виробництво " + amount + " за TM " + map.getId(), () -> {
            productionFixture.ensureStockForTechMapInputs(productionStorageId, map, STOCK_PAD);
            ManufacturingItemResponse created = productionFixture.createWithUniqueBatch(
                    UserRole.ADMIN, productionStorageId, map, amount);
            assertThat(created.getBatchNumber()).isNotBlank();
            return created;
        });
    }

    private ManufacturingItemResponse produceWithBatch(
            TechnologicalMapResponse map, double amount, String batchNumber) {
        return Allure.step("Виробництво " + amount + " партія " + batchNumber, () -> {
            productionFixture.ensureStockForTechMapInputs(productionStorageId, map, STOCK_PAD);
            return productionFixture.createAs(
                    UserRole.ADMIN, productionStorageId, map, amount, batchNumber);
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

    private RelocationResponse sendWithBatchAndDate(
            Long productId, double amount, String batchNumber, java.time.LocalDate date) {
        return Allure.step("Видача партії " + batchNumber + " датою " + date, () -> {
            var request = com.erp.data.factories.relocation.RelocationDataFactory
                    .buildSendWithBatch(
                            productionStorageId, receiverUnitId, productId, amount, batchNumber, false)
                    .toBuilder()
                    .date(date)
                    .build();
            Response response = apiExecutor.execute(
                    ApiEndpointDefinition.RELOCATION_POST_SEND, UserRole.ADMIN, request);
            assertThat(response.statusCode()).isEqualTo(200);
            return response.as(RelocationResponse.class);
        });
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
