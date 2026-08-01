package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.models.response.ResourceResponse;
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
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Write-off після використання екіпажем: списання з parent FLY_POINT.
 * Fight sync (TC-CREW-FIGHT-*) — integration-only: {@code enabled=false}, не regression;
 * E2E делеговано tk SyncTeamProcessIT / стенд з Fight.
 * TC-FLY-WO-001 сіє PENDING через БД ({@code use.database=true}) і complete через API.
 */
@Slf4j
@Epic("Inventory")
@Feature("Crew Write-Off")
@Story("Fight reconciliation / FLY_POINT debit")
public class CrewWriteOffTest extends CrewApiTestBase {

    private static final String RESOURCE_PREFIX = "fly-wo-";
    private static final double ISSUE_AMOUNT = 12.0;
    private static final double WRITE_OFF_AMOUNT = 5.0;
    private static final UserRole STOCK_READER = UserRole.ADMIN;

    private static boolean fightIntegrationEnabled;
    private Long resourceId;
    private String resourceName;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    @Step("Probe Fight / write-off integration + підготовка ресурсу")
    public void setupWriteOffTests() {
        Response stats = apiExecutor.execute(
                ApiEndpointDefinition.INVENTORY_WRITE_OFF_GET_SHORT_STATS,
                UserRole.ADMIN);
        fightIntegrationEnabled = stats.statusCode() == 200;
        log.info("Fight write-off integration probe: enabled={}", fightIntegrationEnabled);

        storageFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        relocationFixture.prepareContext();

        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        resourceId = resource.getId();
        resourceName = resource.getName();
        refreshRoleSessions(UserRole.OWNER_1, UserRole.ADMIN);
    }

    @BeforeMethod(alwaysRun = true)
    public void ensureSenderStock() {
        if (resourceId == null) {
            return;
        }
        relocationFixture.ensureStock(owner1StorageId, resourceId, 100.0);
        refreshRoleSessions(UserRole.OWNER_1);
    }

    @Test(priority = 5)
    @TestCaseId("TC-CREW-WO-PROBE")
    @Description("GET /write-off/short-stats — contract probe для OWNER_1 з inventory-write-off read")
    @Severity(SeverityLevel.MINOR)
    public void testWriteOffShortStatsContract() {
        Response owner1 = apiExecutor.execute(
                ApiEndpointDefinition.INVENTORY_WRITE_OFF_GET_SHORT_STATS,
                UserRole.OWNER_1);
        assertThat(owner1.statusCode()).isIn(200, 403);
    }

    @Test(priority = 10, enabled = false)
    @TestCaseId("TC-CREW-FIGHT-001")
    @Description("Після Fight sync — write-off у GET /write-off для відомого crew (потребує Fight на dev)")
    @Severity(SeverityLevel.NORMAL)
    public void testWriteOffAppearsAfterFightSync() {
        if (!fightIntegrationEnabled) {
            throw new SkipException("Fight sync disabled on dev");
        }
        CrewRegionScenario scenario = crewFixture.prepareSingleCrewScenario("fight-wo-");
        Response page = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.INVENTORY_WRITE_OFF_GET_PAGE,
                UserRole.OWNER_1,
                Map.of("storageId", scenario.crew().getId()));
        assertThat(page.statusCode()).isEqualTo(200);
    }

    @Test(priority = 20, enabled = false)
    @TestCaseId("TC-CREW-FIGHT-002")
    @Description("Complete reconciliation зменшує crew stock (потребує Fight seed на dev)")
    @Severity(SeverityLevel.NORMAL)
    public void testWriteOffCompleteReducesCrewStock() {
        if (!fightIntegrationEnabled) {
            throw new SkipException("Fight sync disabled on dev");
        }
        // E2E reconciliation delegated to tk SyncTeamProcessIT
    }

    @Test(priority = 30)
    @TestCaseId("TC-FLY-WO-001")
    @Description(StorageRegionsAllureDescriptions.TC_FLY_WO_001)
    @Severity(SeverityLevel.CRITICAL)
    public void testCrewUsageWriteOffDebitsFlyPointNotCrew() {
        if (getDbHelper() == null) {
            throw new SkipException(
                    "TC-FLY-WO-001 потребує БД для seed storage_item_write_off "
                            + "(увімкніть use.database=true)");
        }

        CrewRegionScenario scenario = crewFixture.prepareAttachedCrewScenario("fly-wo-");
        refreshRoleSessions(UserRole.OWNER_1, UserRole.ADMIN);

        Long crewId = scenario.crew().getId();
        Long flyPointId = scenario.flyPoint().getId();

        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                crewId,
                resourceId,
                ISSUE_AMOUNT);

        ProductionStockAssertions.StockSnapshot beforeCrew = RelocationStockAssertions.capture(
                apiExecutor, crewId, STOCK_READER, Set.of(resourceId), "crew before write-off");
        ProductionStockAssertions.StockSnapshot beforeFp = RelocationStockAssertions.capture(
                apiExecutor, flyPointId, STOCK_READER, Set.of(resourceId), "fp before write-off");

        long writeOffId = seedPendingCrewWriteOff(crewId, resourceId, resourceName, WRITE_OFF_AMOUNT);

        Response complete = apiExecutor.execute(
                ApiEndpointDefinition.INVENTORY_WRITE_OFF_PUT_COMPLETE,
                UserRole.ADMIN,
                Map.of("writeOffIdentifiers", List.of(writeOffId)));
        assertThat(complete.statusCode())
                .as("PUT /write-off/complete має прийняти seed write-off id=%s", writeOffId)
                .isEqualTo(200);

        ProductionStockAssertions.StockSnapshot afterCrew = RelocationStockAssertions.capture(
                apiExecutor, crewId, STOCK_READER, Set.of(resourceId), "crew after write-off");
        ProductionStockAssertions.StockSnapshot afterFp = RelocationStockAssertions.capture(
                apiExecutor, flyPointId, STOCK_READER, Set.of(resourceId), "fp after write-off");

        RelocationStockAssertions.assertUnchanged(
                beforeCrew, afterCrew, crewId, resourceId,
                "використання екіпажем не списує з CREW (склад на точці)");
        RelocationStockAssertions.assertDebitedFromSender(
                beforeFp, afterFp, flyPointId, resourceId, WRITE_OFF_AMOUNT,
                "complete write-off списує з parent FLY_POINT");
    }

    @Step("DB: seed PENDING write-off storageId={crewId} amount={amount}")
    private long seedPendingCrewWriteOff(long crewId, long resourceId, String resourceName, double amount) {
        String externalId = "erp-auto-" + UUID.randomUUID().toString().substring(0, 8);
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
            ps.setString(5, resourceName != null ? truncate(resourceName, 100) : "erp-resource");
            ps.setBigDecimal(6, java.math.BigDecimal.valueOf(amount));
            ps.setString(7, "erp-auto-test crew usage write-off");
            ps.setString(8, externalId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("INSERT storage_item_write_off RETURNING id").isTrue();
                long id = rs.getLong(1);
                log.info("Seeded PENDING write-off id={} for crewId={}", id, crewId);
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
}
