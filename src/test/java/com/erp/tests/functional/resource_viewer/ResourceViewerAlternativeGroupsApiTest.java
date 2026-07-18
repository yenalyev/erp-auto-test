package com.erp.tests.functional.resource_viewer;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProductionFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.request.AlternativeInputRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.PagedResourceRelocationViewerResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceRelocationSumViewerResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Resource Viewer (wolf) BOM / «декомпозер» coverage for tech maps with alternative groups.
 * <p>
 * After a production batch exists, BOM must attribute usage to the <b>actually used</b>
 * alternative from {@code production_process_input} (default or not). CPMA-620: wolf reads
 * batch inputs when production record exists.
 */
@Slf4j
@Epic("Resource Viewer")
@Feature("BOM — alternative groups")
public class ResourceViewerAlternativeGroupsApiTest extends BaseFunctionalTest {

    private static final double FIXED_AMOUNT = 1.0;
    private static final double DEFAULT_ALT_AMOUNT = 2.0;
    private static final double OTHER_ALT_AMOUNT = 3.0;
    private static final double PRODUCE_AMOUNT = 5.0;
    private static final double RELOCATE_AMOUNT = 5.0;
    private static final double TOLERANCE = 0.001;

    private TechnologicalMapFixture techMapFixture;
    private ProductionFixture productionFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;

    private Long productionStorageId;
    private Long receiverUnitId;
    private TechnologicalMapResponse techMap;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    @Step("Підготовка fixtures для Resource Viewer alt-group BOM")
    public void setupResourceViewerAltGroupSuite() {
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
        log.info("RVW alt-group storages: production={}, receiverUnit={}",
                productionStorageId, receiverUnitId);
    }

    @AfterClass(alwaysRun = true)
    public void teardown() {
        if (techMap != null && techMapFixture != null && productionStorageId != null) {
            try {
                techMapFixture.deactivateTechMap(UserRole.OWNER_1, techMap.getId(), productionStorageId);
            } catch (RuntimeException e) {
                log.warn("Tech map deactivate failed: {}", e.getMessage());
            }
            techMapFixture.setMode(productionStorageId, StorageTechnologicalMapMode.READ_ONLY);
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-RVW-ALT-001")
    @Story("BOM counts actually used alternative after production")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** декомпозер Resource Viewer (wolf) рахує **фактично використаний**
            ресурс альтернативної групи з партії виробництва.
            
            **Сценарій:**
            1. Техкарта F + {D default@2, E@3} → P.
            2. ADMIN виробляє P з вибором non-default E.
            3. Видача P (партія виробництва) STORAGE→UNIT.
            4. GET /resources-viewer/relocations як wolf (resourceIds=F,D,E) → читати sums.
            
            **Очікування:** E = relocate × 3; D = 0; F = relocate × 1.
            
            **CPMA-620:** ResourceViewerService uses production_process_input for batches with production.
            """)
    public void testBomCountsActuallyUsedAlternativeAfterNonDefaultProduction() {
        String suffix = String.valueOf(System.currentTimeMillis());

        ResourceResponse product = resourceFixture.createUniqueResource("RVW-ALT-P-" + suffix);
        ResourceResponse defaultAlt = resourceFixture.createUniqueResource("RVW-ALT-D-" + suffix);
        ResourceResponse otherAlt = resourceFixture.createUniqueResource("RVW-ALT-E-" + suffix);
        ResourceResponse fixed = resourceFixture.createUniqueResource("RVW-ALT-F-" + suffix);

        techMap = Allure.step("Arrange: техкарта F + {D default, E}", () -> {
            var group = TechnologicalMapDataFactory.alternativeGroup(
                    "Клей",
                    TechnologicalMapDataFactory.alternativeResource(
                            defaultAlt.getId(), DEFAULT_ALT_AMOUNT, true),
                    TechnologicalMapDataFactory.alternativeResource(
                            otherAlt.getId(), OTHER_ALT_AMOUNT, false));
            TechnologicalMapRequest request = TechnologicalMapDataFactory
                    .createProductionMapWithStorages(
                            "RVW-ALT-M",
                            List.of(new ResourceUsageRequest(fixed.getId(), FIXED_AMOUNT)),
                            List.of(new ResourceUsageRequest(product.getId(), 1.0)),
                            Set.of(productionStorageId))
                    .groups(List.of(group))
                    .build();
            return techMapFixture.createTechMapWithRequest(UserRole.ADMIN, request);
        });

        Long groupId = techMap.getGroups().getFirst().getId();

        ManufacturingItemResponse produced = Allure.step(
                "Виробництво P з non-default E", () -> {
                    productionFixture.ensureStockForTechMapInputs(productionStorageId, techMap, 200.0);
                    List<AlternativeInputRequest> choice = ProductionDataFactory.alternativeInputsChoosing(
                            techMap, groupId, otherAlt.getId());
                    ManufacturingItemResponse created = productionFixture.createAsWithAlternatives(
                            UserRole.ADMIN, productionStorageId, techMap, PRODUCE_AMOUNT, choice);
                    assertThat(created.getBatchNumber()).isNotBlank();
                    return created;
                });

        RelocationResponse sent = Allure.step(
                "Видача виробленої партії P на UNIT (visible у resource-viewer)", () ->
                        relocationFixture.createSendWithBatch(
                                UserRole.ADMIN,
                                productionStorageId,
                                receiverUnitId,
                                product.getId(),
                                RELOCATE_AMOUNT,
                                produced.getBatchNumber(),
                                true));
        assertThat(sent.getId()).isNotNull();

        List<ResourceRelocationSumViewerResponse> sums = fetchSums(
                List.of(fixed.getId(), defaultAlt.getId(), otherAlt.getId()));

        double expectedE = RELOCATE_AMOUNT * OTHER_ALT_AMOUNT;
        double expectedF = RELOCATE_AMOUNT * FIXED_AMOUNT;
        Allure.step("BOM: E (фактично використаний), D = 0, F present", () -> {
            assertThat(amountOf(sums, fixed.getId()))
                    .as("fixed F must appear — otherwise wolf does not see relocation")
                    .isCloseTo(expectedF, within(TOLERANCE));
            assertThat(amountOf(sums, otherAlt.getId()))
                    .as("wolf BOM має рахувати non-default E з production_process_input")
                    .isCloseTo(expectedE, within(TOLERANCE));
            assertThat(amountOf(sums, defaultAlt.getId()))
                    .as("default D не споживали — сума має бути 0")
                    .isCloseTo(0.0, within(TOLERANCE));
        });
    }

    private List<ResourceRelocationSumViewerResponse> fetchSums(List<Long> resourceIds) {
        return Allure.step("GET relocations → sums як wolf resourceIds=" + resourceIds, () -> {
            Map<String, Object> params = new HashMap<>();
            params.put("resourceIds", resourceIds);
            params.put("receiverIds", receiverUnitId);
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

    private static double amountOf(List<ResourceRelocationSumViewerResponse> sums, Long resourceId) {
        return sums.stream()
                .filter(s -> resourceId.equals(s.getResourceId()))
                .map(ResourceRelocationSumViewerResponse::getAmount)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();
    }

    @Test(priority = 11)
    @TestCaseId("TC-RVW-ALT-002")
    @Story("BOM counts default alternative after production")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Контрольний happy path: production з default alt → BOM показує D, E = 0; F present.
            """)
    public void testBomCountsDefaultAlternativeAfterDefaultProduction() {
        String suffix = String.valueOf(System.currentTimeMillis());

        ResourceResponse product = resourceFixture.createUniqueResource("RVW-ALT2-P-" + suffix);
        ResourceResponse defaultAlt = resourceFixture.createUniqueResource("RVW-ALT2-D-" + suffix);
        ResourceResponse otherAlt = resourceFixture.createUniqueResource("RVW-ALT2-E-" + suffix);
        ResourceResponse fixed = resourceFixture.createUniqueResource("RVW-ALT2-F-" + suffix);

        techMap = Allure.step("Arrange: техкарта F + {D default, E}", () -> {
            var group = TechnologicalMapDataFactory.alternativeGroup(
                    "Клей",
                    TechnologicalMapDataFactory.alternativeResource(
                            defaultAlt.getId(), DEFAULT_ALT_AMOUNT, true),
                    TechnologicalMapDataFactory.alternativeResource(
                            otherAlt.getId(), OTHER_ALT_AMOUNT, false));
            TechnologicalMapRequest request = TechnologicalMapDataFactory
                    .createProductionMapWithStorages(
                            "RVW-ALT2-M",
                            List.of(new ResourceUsageRequest(fixed.getId(), FIXED_AMOUNT)),
                            List.of(new ResourceUsageRequest(product.getId(), 1.0)),
                            Set.of(productionStorageId))
                    .groups(List.of(group))
                    .build();
            return techMapFixture.createTechMapWithRequest(UserRole.ADMIN, request);
        });

        ManufacturingItemResponse produced = Allure.step("Виробництво P з default D", () -> {
            productionFixture.ensureStockForTechMapInputs(productionStorageId, techMap, 200.0);
            ManufacturingItemResponse created = productionFixture.createAs(
                    UserRole.ADMIN, productionStorageId, techMap, PRODUCE_AMOUNT,
                    ProductionDataFactory.uniqueBatchNumber());
            assertThat(created.getBatchNumber()).isNotBlank();
            return created;
        });

        RelocationResponse sent = Allure.step("Видача партії P на UNIT", () ->
                relocationFixture.createSendWithBatch(
                        UserRole.ADMIN,
                        productionStorageId,
                        receiverUnitId,
                        product.getId(),
                        RELOCATE_AMOUNT,
                        produced.getBatchNumber(),
                        true));
        assertThat(sent.getId()).isNotNull();

        List<ResourceRelocationSumViewerResponse> sums = fetchSums(
                List.of(fixed.getId(), defaultAlt.getId(), otherAlt.getId()));

        double expectedD = RELOCATE_AMOUNT * DEFAULT_ALT_AMOUNT;
        double expectedF = RELOCATE_AMOUNT * FIXED_AMOUNT;
        Allure.step("BOM: D (default), E = 0, F present", () -> {
            assertThat(amountOf(sums, fixed.getId()))
                    .as("fixed F must appear — otherwise wolf does not see relocation")
                    .isCloseTo(expectedF, within(TOLERANCE));
            assertThat(amountOf(sums, defaultAlt.getId()))
                    .as("default D from production_process_input")
                    .isCloseTo(expectedD, within(TOLERANCE));
            assertThat(amountOf(sums, otherAlt.getId()))
                    .as("non-default E not used")
                    .isCloseTo(0.0, within(TOLERANCE));
        });
    }
}
