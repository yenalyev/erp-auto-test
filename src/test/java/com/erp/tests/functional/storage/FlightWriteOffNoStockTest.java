package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.FaitaResourceFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.utils.helpers.RelocationStockAssertions;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.http.Method;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** Isolated source-log fixture and API assertions for FLIGHT/ЖБД write-off rules. */
@Epic("Inventory")
@Feature("ЖБД write-off: no stock and statuses")
public class FlightWriteOffNoStockTest extends CrewApiTestBase {

    private ResourceResponse resource;
    private FaitaResourceFixture faitaFixture;
    private List<Long> reconciliationIds = List.of();
    private int ammunitionId;
    private Integer teamId;
    private Long flightLogId;
    private String sourceId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    public void prepareMappedResource() {
        if (getDbHelper() == null) {
            throw new SkipException("FLIGHT tests require -Duse.database=true");
        }
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        resource = resourceFixture.createUniqueResource("flight-no-stock-");
        ammunitionId = ThreadLocalRandom.current().nextInt(100_000_000, 900_000_000);
        faitaFixture = new FaitaResourceFixture(testContext, apiExecutor);
        reconciliationIds = faitaFixture.createFlightReconciliation(
                Integer.toString(ammunitionId), resource.getName(), resource.getId());
    }

    @AfterMethod(alwaysRun = true)
    public void removeSourceFixtures() {
        if (getDbHelper() == null) {
            return;
        }
        try {
            if (flightLogId != null) {
                executeDelete("DELETE FROM crew.team_flight_log WHERE entry_id = ?", flightLogId);
                flightLogId = null;
            }
            if (sourceId != null) {
                executeDelete("DELETE FROM storage_item_write_off WHERE source_id = ?", sourceId);
                sourceId = null;
            }
            if (teamId != null) {
                executeDelete("DELETE FROM crew.team WHERE team_id = ?", teamId);
                teamId = null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot clean isolated FLIGHT fixtures", e);
        }
    }

    @AfterClass(alwaysRun = true)
    public void removeMappedResource() {
        if (faitaFixture != null) {
            faitaFixture.deleteReconciliationsQuietly(reconciliationIds);
        }
        if (resource != null) {
            Response response = resourceFixture.deactivate(UserRole.ADMIN, resource.getId());
            assertThat(response.statusCode()).isIn(200, 204);
        }
    }

    @Test
    @TestCaseId("TC-FLIGHT-NOSTOCK-001")
    @Description("ЖБД event with mapped resource and zero stock must create no write-off journal row")
    @Severity(SeverityLevel.CRITICAL)
    public void noStockEventIsAbsentFromWriteOffJournal() {
        var scenario = crewFixture.prepareSingleCrewScenario("flight-no-stock-");
        long crewId = scenario.crew().getId();
        refreshRoleSessions(UserRole.ADMIN);
        ProductionStockAssertions.StockSnapshot before = stock(crewId, "before ЖБД sync");
        assertThat(before.amountOf(resource.getId())).isZero();

        insertFlightSource(scenario.unit().getId(), scenario.unit().getName(), crewId);
        Response sync = sessionClient.executeWithCookies(
                Method.POST,
                "/api/v1/integrations/faita/syncTeams",
                null,
                authService.getSessionForUser(UserRole.ADMIN.getUsername(), UserRole.ADMIN.getPassword()));
        assertThat(sync.statusCode())
                .as("dev Fight sync endpoint response: %s", sync.getBody().asString())
                .isEqualTo(200);
        assertThat(flightLogStatus())
                .as("Source log must be processed; a NEW row means syncTeams is disabled on dev")
                .isNotEqualTo("NEW");

        assertThat(countWriteOffsForSource())
                .as("No stock: no FLIGHT write-off row for sourceId=%s", sourceId)
                .isZero();
        assertThat(journalRows(crewId))
                .noneMatch(row -> Integer.toString(ammunitionId).equals(row.get("externalResourceId")));
        RelocationStockAssertions.assertUnchanged(before, stock(crewId, "after ЖБД sync"),
                crewId, resource.getId(), "ignored ЖБД event does not debit stock");
    }

    @Test
    @TestCaseId("TC-FLIGHT-STATUS-002")
    @Description("Explicit rejection of a PENDING FLIGHT row produces REJECTED_USER without debit")
    @Severity(SeverityLevel.NORMAL)
    public void explicitRejectHasDistinctStatus() {
        long crewId = crewFixture.prepareSingleCrewScenario("flight-reject-").crew().getId();
        refreshRoleSessions(UserRole.ADMIN);
        ProductionStockAssertions.StockSnapshot before = stock(crewId, "before reject");
        sourceId = "erp-flight-status-" + UUID.randomUUID().toString().substring(0, 12);
        long writeOffId = seedPendingFlightWriteOff(crewId);

        Response reject = apiExecutor.execute(
                ApiEndpointDefinition.INVENTORY_WRITE_OFF_PUT_REJECT,
                UserRole.ADMIN,
                Map.of("writeOffIdentifiers", List.of(writeOffId)));
        assertThat(reject.statusCode()).isEqualTo(200);
        assertThat(journalRows(crewId).stream()
                .filter(row -> ((Number) row.get("id")).longValue() == writeOffId)
                .map(row -> (String) row.get("status"))
                .findFirst()).contains("REJECTED_USER");
        RelocationStockAssertions.assertUnchanged(before, stock(crewId, "after reject"),
                crewId, resource.getId(), "explicit reject does not debit stock");
    }

    private ProductionStockAssertions.StockSnapshot stock(long storageId, String phase) {
        return RelocationStockAssertions.capture(apiExecutor, storageId, UserRole.ADMIN,
                Set.of(resource.getId()), phase);
    }

    private List<Map<String, Object>> journalRows(long crewId) {
        Response page = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.INVENTORY_WRITE_OFF_GET_PAGE,
                UserRole.ADMIN,
                Map.of("size", 100, "storageId", crewId));
        assertThat(page.statusCode()).isEqualTo(200);
        List<Map<String, Object>> rows = page.jsonPath().getList("content");
        assertThat(rows).isNotNull();
        return rows;
    }

    private void insertFlightSource(long unitStorageId, String unitName, long crewId) {
        teamId = ThreadLocalRandom.current().nextInt(100_000_000, 900_000_000);
        Instant timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        sourceId = teamId + " " + DateTimeFormatter.ISO_INSTANT.format(timestamp);
        String marker = "erp-flight-no-stock-" + UUID.randomUUID().toString().substring(0, 12);
        String teamSql = """
                INSERT INTO crew.team
                    (team_id, team_name, team_status, unit_id, unit_name,
                     team_storage_id, unit_storage_id)
                VALUES (?, ?, 'ACTIVE', ?, ?, ?, ?)
                """;
        String logSql = """
                INSERT INTO crew.team_flight_log
                    (flight_timestamp, unit_name, sub_unit_name, team_id, team_name,
                     ammunition_id, ammunition_name, amount, comment, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, 'NEW')
                RETURNING entry_id
                """;
        try (PreparedStatement team = getDbHelper().getConnection().prepareStatement(teamSql)) {
            team.setInt(1, teamId);
            team.setString(2, marker);
            team.setInt(3, teamId);
            team.setString(4, unitName);
            team.setLong(5, crewId);
            team.setLong(6, unitStorageId);
            team.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot seed isolated Fight team", e);
        }
        try (PreparedStatement log = getDbHelper().getConnection().prepareStatement(logSql)) {
            log.setTimestamp(1, Timestamp.from(timestamp));
            log.setString(2, unitName);
            log.setString(3, unitName);
            log.setInt(4, teamId);
            log.setString(5, marker);
            log.setInt(6, ammunitionId);
            log.setString(7, resource.getName());
            log.setString(8, marker);
            try (ResultSet rs = log.executeQuery()) {
                assertThat(rs.next()).isTrue();
                flightLogId = rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot seed isolated Fight flight log", e);
        }
    }

    private String flightLogStatus() {
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(
                "SELECT status FROM crew.team_flight_log WHERE entry_id = ?")) {
            ps.setLong(1, flightLogId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read source flight status", e);
        }
    }

    private long countWriteOffsForSource() {
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(
                "SELECT COUNT(*) FROM storage_item_write_off WHERE source = 'FLIGHT' AND source_id = ?")) {
            ps.setString(1, sourceId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot count source write-offs", e);
        }
    }

    private long seedPendingFlightWriteOff(long crewId) {
        String resources = "[{\"id\":" + resource.getId() + ",\"name\":"
                + com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .valueToTree(resource.getName()).toString() + "}]";
        String sql = """
                INSERT INTO storage_item_write_off
                    (date_time, storage_id, resources, external_resource_id, external_resource_name,
                     amount, source, operation_comment, status, source_id)
                VALUES (?, ?, ?::jsonb, ?, ?, 1, 'FLIGHT', ?, 'PENDING', ?)
                RETURNING id
                """;
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setLong(2, crewId);
            ps.setString(3, resources);
            ps.setString(4, Integer.toString(ammunitionId));
            ps.setString(5, resource.getName());
            ps.setString(6, "erp-auto-test: explicit reject status");
            ps.setString(7, sourceId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot seed FLIGHT write-off", e);
        }
    }

    private void executeDelete(String sql, Object value) throws SQLException {
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(sql)) {
            ps.setObject(1, value);
            ps.executeUpdate();
        }
    }
}
