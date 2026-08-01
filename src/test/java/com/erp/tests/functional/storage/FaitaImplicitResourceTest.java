package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.models.request.ResourceReconciliationRequest;
import com.erp.models.request.SaveImplicitResourcesRequest;
import com.erp.models.response.FaitaResourceResponse;
import com.erp.models.response.ResourceReconciliationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.utils.helpers.ApiResponseHelper;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.utils.helpers.RelocationStockAssertions;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Step;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Додаткова номенклатура (FAITA implicit resources) при використанні виробів екіпажами:
 * налаштування зв'язків виріб → додаткові ресурси + наявність додаткових write-off у журналі.
 * TC-FAITA-IMPL-002 потребує DB seed ({@code use.database=true}); без БД — SkipException.
 */
@Slf4j
@Epic("Inventory")
@Feature("FAITA Implicit Resources")
@Story("Additional nomenclature write-off on crew product usage")
public class FaitaImplicitResourceTest extends CrewApiTestBase {

    private static final String RESOURCE_PREFIX = "faita-impl-";
    private static final double ISSUE_AMOUNT = 15.0;
    private static final double WRITE_OFF_AMOUNT = 4.0;
    private static final UserRole STOCK_READER = UserRole.ADMIN;

    private static boolean faitaApiAvailable;

    private String productExternalId;
    private String productExternalName;
    private String implicit1ExternalId;
    private String implicit1ExternalName;
    private String implicit2ExternalId;
    private String implicit2ExternalName;

    private ResourceResponse productResource;
    private ResourceResponse implicitResource1;
    private ResourceResponse implicitResource2;

    private final List<Long> reconciliationIdsToCleanup = new ArrayList<>();

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    @Step("Підготовка ресурсів і FLIGHT reconciliations для FAITA implicit")
    public void setupFaitaImplicitTests() {
        Response probe = apiExecutor.execute(ApiEndpointDefinition.FAITA_RESOURCES_GET, UserRole.ADMIN);
        faitaApiAvailable = probe.statusCode() == 200;
        log.info("FAITA integrations API probe: status={} available={}",
                probe.statusCode(), faitaApiAvailable);
        if (!faitaApiAvailable) {
            log.warn("GET /integrations/faita/resources → {}. "
                    + "Тести будуть skipped (ендпоінт відсутній на цьому env).",
                    probe.statusCode());
            return;
        }

        storageFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        relocationFixture.prepareContext();

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        productExternalId = "erp-prod-" + suffix;
        productExternalName = "ERP FAITA product " + suffix;
        implicit1ExternalId = "erp-impl1-" + suffix;
        implicit1ExternalName = "ERP implicit 1 " + suffix;
        implicit2ExternalId = "erp-impl2-" + suffix;
        implicit2ExternalName = "ERP implicit 2 " + suffix;

        productResource = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "p-");
        implicitResource1 = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "i1-");
        implicitResource2 = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "i2-");

        reconciliationIdsToCleanup.addAll(createFlightReconciliation(
                productExternalId, productExternalName, productResource.getId()));
        reconciliationIdsToCleanup.addAll(createFlightReconciliation(
                implicit1ExternalId, implicit1ExternalName, implicitResource1.getId()));
        reconciliationIdsToCleanup.addAll(createFlightReconciliation(
                implicit2ExternalId, implicit2ExternalName, implicitResource2.getId()));

        assertFaitaResourceVisible(productExternalId);

        refreshRoleSessions(UserRole.OWNER_1, UserRole.ADMIN);
    }

    @BeforeMethod(alwaysRun = true)
    public void ensureSenderStock() {
        if (!faitaApiAvailable || productResource == null) {
            return;
        }
        relocationFixture.ensureStock(owner1StorageId, productResource.getId(), 100.0);
        relocationFixture.ensureStock(owner1StorageId, implicitResource1.getId(), 100.0);
        relocationFixture.ensureStock(owner1StorageId, implicitResource2.getId(), 100.0);
        refreshRoleSessions(UserRole.OWNER_1);
    }

    @AfterClass(alwaysRun = true)
    @Step("Cleanup: clear implicit config + delete test reconciliations")
    public void cleanupFaitaImplicitArtifacts() {
        try {
            clearImplicitResources(productExternalId, productExternalName);
        } catch (Exception e) {
            log.warn("Failed to clear implicit resources for {}: {}", productExternalId, e.getMessage());
        }
        for (Long id : reconciliationIdsToCleanup) {
            try {
                apiExecutor.execute(
                        ApiEndpointDefinition.RESOURCE_RECONCILIATION_DELETE_BY_ID,
                        UserRole.ADMIN,
                        null,
                        String.valueOf(id));
            } catch (Exception e) {
                log.warn("Failed to delete reconciliation id={}: {}", id, e.getMessage());
            }
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-FAITA-IMPL-001")
    @Description(StorageRegionsAllureDescriptions.TC_FAITA_IMPL_001)
    @Severity(SeverityLevel.CRITICAL)
    public void testSaveMultipleImplicitResourcesForProduct() {
        requireFaitaApi();

        SaveImplicitResourcesRequest body = SaveImplicitResourcesRequest.builder()
                .externalId(productExternalId)
                .externalName(productExternalName)
                .implicitResources(List.of(
                        FaitaResourceResponse.builder()
                                .resourceId(implicit1ExternalId)
                                .resourceName(implicit1ExternalName)
                                .build(),
                        FaitaResourceResponse.builder()
                                .resourceId(implicit2ExternalId)
                                .resourceName(implicit2ExternalName)
                                .build()))
                .build();

        Response put = apiExecutor.execute(
                ApiEndpointDefinition.FAITA_IMPLICIT_RESOURCES_PUT,
                UserRole.ADMIN,
                body,
                productExternalId);
        assertThat(put.statusCode())
                .as("PUT implicit-resources має зберегти 2 додаткові ресурси. Body: %s",
                        put.getBody().asString())
                .isEqualTo(200);

        FaitaResourceResponse saved = put.as(FaitaResourceResponse.class);
        assertThat(saved.getResourceId()).isEqualTo(productExternalId);
        assertThat(saved.getImplicitResources())
                .extracting(FaitaResourceResponse::getResourceId)
                .containsExactlyInAnyOrder(implicit1ExternalId, implicit2ExternalId);

        Response get = apiExecutor.execute(ApiEndpointDefinition.FAITA_RESOURCES_GET, UserRole.ADMIN);
        assertThat(get.statusCode()).isEqualTo(200);
        List<FaitaResourceResponse> all = ApiResponseHelper.parseList(
                get, FaitaResourceResponse.class, "GET FAITA resources after PUT");
        FaitaResourceResponse fromList = all.stream()
                .filter(r -> productExternalId.equals(r.getResourceId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "FAITA list не містить product externalId=" + productExternalId));
        assertThat(fromList.getImplicitResources())
                .extracting(FaitaResourceResponse::getResourceId)
                .containsExactlyInAnyOrder(implicit1ExternalId, implicit2ExternalId);

        if (getDbHelper() != null) {
            assertImplicitConfigInDb(productExternalId, implicit1ExternalId, implicit2ExternalId);
        }
    }

    @Test(priority = 20, dependsOnMethods = "testSaveMultipleImplicitResourcesForProduct")
    @TestCaseId("TC-FAITA-IMPL-002")
    @Description(StorageRegionsAllureDescriptions.TC_FAITA_IMPL_002)
    @Severity(SeverityLevel.CRITICAL)
    public void testImplicitWriteOffsAppearInJournalAndDebitFlyPoint() {
        requireFaitaApi();
        if (getDbHelper() == null) {
            throw new SkipException(
                    "TC-FAITA-IMPL-002 потребує БД для seed storage_item_write_off "
                            + "(увімкніть use.database=true)");
        }

        CrewRegionScenario scenario = crewFixture.prepareAttachedCrewScenario("faita-wo-");
        refreshRoleSessions(UserRole.OWNER_1, UserRole.ADMIN);

        Long crewId = scenario.crew().getId();
        Long flyPointId = scenario.flyPoint().getId();
        Long memberStorageId = scenario.memberStorageId();

        for (ResourceResponse resource : List.of(productResource, implicitResource1, implicitResource2)) {
            relocationFixture.createSendAndFinishBySender(
                    UserRole.OWNER_1,
                    memberStorageId,
                    crewId,
                    resource.getId(),
                    ISSUE_AMOUNT);
        }

        Set<Long> resourceIds = Set.of(
                productResource.getId(),
                implicitResource1.getId(),
                implicitResource2.getId());
        ProductionStockAssertions.StockSnapshot beforeFp = RelocationStockAssertions.capture(
                apiExecutor, flyPointId, STOCK_READER, resourceIds, "fp before implicit write-offs");

        String sourceId = "erp-impl-src-" + UUID.randomUUID().toString().substring(0, 8);
        long productWoId = seedPendingCrewWriteOff(
                crewId, productResource.getId(), productResource.getName(),
                productExternalId, productExternalName, WRITE_OFF_AMOUNT, sourceId);
        long impl1WoId = seedPendingCrewWriteOff(
                crewId, implicitResource1.getId(), implicitResource1.getName(),
                implicit1ExternalId, implicit1ExternalName, WRITE_OFF_AMOUNT, sourceId);
        long impl2WoId = seedPendingCrewWriteOff(
                crewId, implicitResource2.getId(), implicitResource2.getName(),
                implicit2ExternalId, implicit2ExternalName, WRITE_OFF_AMOUNT, sourceId);

        Response page = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.INVENTORY_WRITE_OFF_GET_PAGE,
                UserRole.ADMIN,
                Map.of("storageId", crewId, "size", 100));
        assertThat(page.statusCode()).isEqualTo(200);

        List<Map<String, Object>> content = page.jsonPath().getList("content");
        assertThat(content).isNotNull();
        Set<String> externalIdsOnPage = content.stream()
                .map(row -> String.valueOf(row.get("externalResourceId")))
                .collect(Collectors.toSet());
        assertThat(externalIdsOnPage)
                .as("Журнал з Файти має містити виріб і обидві додаткові номенклатури")
                .contains(productExternalId, implicit1ExternalId, implicit2ExternalId);

        List<Map<String, Object>> seededRows = content.stream()
                .filter(row -> Set.of(productExternalId, implicit1ExternalId, implicit2ExternalId)
                        .contains(String.valueOf(row.get("externalResourceId"))))
                .toList();
        assertThat(seededRows)
                .as("Write-off виробу та додаткових номенклатур мають однакову кількість (як після SyncTeamProcess)")
                .hasSizeGreaterThanOrEqualTo(3)
                .allSatisfy(row -> assertThat(toDouble(row.get("amount")))
                        .isEqualTo(WRITE_OFF_AMOUNT));

        Response complete = apiExecutor.execute(
                ApiEndpointDefinition.INVENTORY_WRITE_OFF_PUT_COMPLETE,
                UserRole.ADMIN,
                Map.of("writeOffIdentifiers", List.of(productWoId, impl1WoId, impl2WoId)));
        assertThat(complete.statusCode())
                .as("PUT /write-off/complete має прийняти product + 2 implicit write-offs")
                .isEqualTo(200);

        ProductionStockAssertions.StockSnapshot afterFp = RelocationStockAssertions.capture(
                apiExecutor, flyPointId, STOCK_READER, resourceIds, "fp after implicit write-offs");

        RelocationStockAssertions.assertDebitedFromSender(
                beforeFp, afterFp, flyPointId, productResource.getId(), WRITE_OFF_AMOUNT,
                "complete product write-off списує з FLY_POINT");
        RelocationStockAssertions.assertDebitedFromSender(
                beforeFp, afterFp, flyPointId, implicitResource1.getId(), WRITE_OFF_AMOUNT,
                "complete implicit-1 write-off списує з FLY_POINT");
        RelocationStockAssertions.assertDebitedFromSender(
                beforeFp, afterFp, flyPointId, implicitResource2.getId(), WRITE_OFF_AMOUNT,
                "complete implicit-2 write-off списує з FLY_POINT");
    }

    private void requireFaitaApi() {
        if (!faitaApiAvailable) {
            throw new SkipException(
                    "FAITA integrations API недоступний на цьому env "
                            + "(GET /api/v1/integrations/faita/resources ≠ 200). "
                            + "Потрібен бекенд з FaitaResourceController (CPMA-629).");
        }
    }

    @Step("API: create FLIGHT reconciliation externalId={externalId} → resourceId={resourceId}")
    private List<Long> createFlightReconciliation(String externalId, String externalName, Long resourceId) {
        ResourceReconciliationRequest body = ResourceReconciliationRequest.builder()
                .source("FLIGHT")
                .externalId(externalId)
                .externalName(externalName)
                .resourceIds(List.of(resourceId))
                .build();
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.RESOURCE_RECONCILIATION_CREATE, UserRole.ADMIN, body);
        assertThat(response.statusCode())
                .as("POST /resources/reconciliations для %s. Body: %s",
                        externalId, response.getBody().asString())
                .isEqualTo(200);
        List<ResourceReconciliationResponse> created = ApiResponseHelper.parseList(
                response, ResourceReconciliationResponse.class, "Create FLIGHT reconciliation");
        assertThat(created)
                .as("create reconciliations має повернути хоча б один id для %s", externalId)
                .isNotEmpty();
        return created.stream().map(ResourceReconciliationResponse::getId).toList();
    }

    @Step("API: assert FAITA resource list contains externalId={externalId}")
    private void assertFaitaResourceVisible(String externalId) {
        Response get = apiExecutor.execute(ApiEndpointDefinition.FAITA_RESOURCES_GET, UserRole.ADMIN);
        assertThat(get.statusCode())
                .as("GET /integrations/faita/resources. Body: %s", get.getBody().asString())
                .isEqualTo(200);
        List<FaitaResourceResponse> all = ApiResponseHelper.parseList(
                get, FaitaResourceResponse.class, "GET FAITA resources");
        assertThat(all)
                .extracting(FaitaResourceResponse::getResourceId)
                .as("після FLIGHT reconciliation виріб має з'явитись у списку FAITA resources")
                .contains(externalId);
    }

    @Step("API: clear implicit resources for product {externalId}")
    private void clearImplicitResources(String externalId, String externalName) {
        if (externalId == null) {
            return;
        }
        SaveImplicitResourcesRequest body = SaveImplicitResourcesRequest.builder()
                .externalId(externalId)
                .externalName(externalName)
                .implicitResources(List.of())
                .build();
        apiExecutor.execute(
                ApiEndpointDefinition.FAITA_IMPLICIT_RESOURCES_PUT,
                UserRole.ADMIN,
                body,
                externalId);
    }

    @Step("DB: assert sync_process_config contains implicits for {productExternalId}")
    private void assertImplicitConfigInDb(
            String productExternalId, String implicit1, String implicit2) {
        String sql = """
                SELECT implicit_resource_usage::text
                FROM crew.sync_process_config
                WHERE process_name = 'sync_teams'
                """;
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).as("sync_process_config row for sync_teams").isTrue();
            String json = rs.getString(1);
            assertThat(json)
                    .as("global implicit_resource_usage JSON")
                    .contains(productExternalId)
                    .contains(implicit1)
                    .contains(implicit2);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read implicit_resource_usage: " + e.getMessage(), e);
        }
    }

    @Step("DB: seed PENDING write-off storageId={crewId} externalId={externalId} amount={amount}")
    private long seedPendingCrewWriteOff(
            long crewId,
            long resourceId,
            String resourceName,
            String externalId,
            String externalName,
            double amount,
            String sourceId) {
        String resourcesJson = "[{\"id\":%d,\"name\":%s}]".formatted(
                resourceId, toJsonString(resourceName));
        String sql = """
                INSERT INTO storage_item_write_off
                    (date_time, storage_id, resources, external_resource_id, external_resource_name,
                     amount, source, operation_comment, status, source_id)
                VALUES (?, ?, ?::jsonb, ?, ?, ?, 'FLIGHT', ?, 'PENDING', ?)
                RETURNING id
                """;
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setLong(2, crewId);
            ps.setString(3, resourcesJson);
            ps.setString(4, externalId);
            ps.setString(5, truncate(externalName != null ? externalName : "erp-resource", 100));
            ps.setBigDecimal(6, java.math.BigDecimal.valueOf(amount));
            ps.setString(7, "erp-auto-test FAITA implicit write-off");
            ps.setString(8, sourceId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("INSERT storage_item_write_off RETURNING id").isTrue();
                long id = rs.getLong(1);
                log.info("Seeded PENDING write-off id={} externalId={} sourceId={}", id, externalId, sourceId);
                return id;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Не вдалося seed storage_item_write_off для crewId=" + crewId + ": " + e.getMessage(), e);
        }
    }

    private static String toJsonString(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }
}
