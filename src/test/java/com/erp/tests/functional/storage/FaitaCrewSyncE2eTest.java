package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Controlled dev flight-log -> sync -> write-off flow. Requires use.database=true. */
@Epic("Integration")
@Feature("FAITA crew sync and write-off")
public class FaitaCrewSyncE2eTest extends BaseFunctionalTest {

    private String comment;
    private String sourceId;
    private String externalResourceId;
    private Integer teamId;
    private Instant flightTimestamp;

    @TestCaseId("TC-FAITA-SYNC-POS-E2E-001")
    @Description("Controlled dev FLIGHT log keeps raw position in PENDING write-off and is idempotent")
    @Severity(SeverityLevel.CRITICAL)
    @Test
    public void flightPositionIsPersistedAndExposedAfterSync() throws SQLException {
        if (!Boolean.getBoolean("faita.sync.e2e.enabled")) {
            throw new SkipException("Explicit opt-in required: faita.sync.e2e.enabled=true");
        }
        assertThat(System.getProperty("env")).as("controlled sync runs only on dev")
                .isEqualTo("dev");
        if (getDbHelper() == null) {
            throw new SkipException("Requires dev JDBC: use.database=true");
        }

        TeamCandidate team = findActiveTeam();
        String marker = UUID.randomUUID().toString().substring(0, 12);
        comment = "erp-auto-test-faita-pos-" + marker;
        String position = "ЖБД QA " + marker;
        externalResourceId = Integer.toString(-100_000_000 - Math.floorMod(marker.hashCode(), 800_000_000));
        teamId = team.id();
        flightTimestamp = Instant.now().minusSeconds(5).truncatedTo(ChronoUnit.MILLIS);
        sourceId = teamId + " " + flightTimestamp;

        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("flightTimestamp", flightTimestamp.toString());
        logEntry.put("unitName", team.unitName());
        logEntry.put("subUnitName", "QA");
        logEntry.put("teamId", teamId);
        logEntry.put("teamName", team.name());
        logEntry.put("ammunitionId", Integer.parseInt(externalResourceId));
        logEntry.put("ammunitionName", comment);
        logEntry.put("amount", 1);
        logEntry.put("comment", comment);
        logEntry.put("status", "NEW");
        logEntry.put("positionName", position);

        Response seeded = apiExecutor.execute(ApiEndpointDefinition.FAITA_DEV_LOG_POST,
                UserRole.ADMIN, logEntry);
        assertThat(seeded.statusCode()).as("dev FAITA log endpoint").isEqualTo(202);

        runSync();
        long writeOffId = findCreatedWriteOffId();
        assertThat(writeOffId).as("controlled flight log was processed").isPositive();
        assertPendingSingleWriteOff();

        runSync();
        assertPendingSingleWriteOff();

        assertThat(hasPositionColumn())
                .as("storage_item_write_off.position_name must store the journal snapshot")
                .isTrue();
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(
                "SELECT position_name FROM storage_item_write_off WHERE id = ?")) {
            ps.setLong(1, writeOffId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo(position);
            }
        }

        Response page = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.INVENTORY_WRITE_OFF_GET_PAGE,
                UserRole.ADMIN,
                Map.of("storageId", team.storageId(), "size", 100, "sort", "id,desc"));
        assertThat(page.statusCode()).isEqualTo(200);
        java.util.List<Map<String, Object>> rows = page.jsonPath().getList("content");
        Map<String, Object> apiRow = rows.stream()
                .filter(row -> ((Number) row.get("id")).longValue() == writeOffId)
                .findFirst().orElseThrow(() -> new AssertionError(
                        "Controlled write-off id=" + writeOffId + " is absent from API page"));
        assertThat(apiRow.get("positionName")).isEqualTo(position);
        assertThat(apiRow.get("status")).isEqualTo("PENDING");
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupControlledFlight() throws SQLException {
        if (getDbHelper() == null || comment == null) {
            return;
        }
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement("""
                SELECT count(*) FROM storage_item_write_off
                WHERE source = 'FLIGHT' AND source_id = ? AND external_resource_id = ?
                  AND operation_comment = ? AND status <> 'PENDING'
                """)) {
            ps.setString(1, sourceId);
            ps.setString(2, externalResourceId);
            ps.setString(3, comment);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                if (rs.getLong(1) > 0) {
                    throw new IllegalStateException("Test-owned write-off has terminal status; "
                            + "flight log and write-off were preserved for stock investigation: " + comment);
                }
            }
        }
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(
                """
                DELETE FROM crew.team_flight_log
                WHERE team_id = ? AND comment = ? AND ammunition_id = ? AND ammunition_name = ?
                """)) {
            ps.setInt(1, teamId);
            ps.setString(2, comment);
            ps.setInt(3, Integer.parseInt(externalResourceId));
            ps.setString(4, comment);
            assertThat(ps.executeUpdate()).as("at most one test-owned flight log").isLessThanOrEqualTo(1);
        }
        // The generated external ID is unmapped, so this row must remain PENDING and has no debit.
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement("""
                DELETE FROM storage_item_write_off
                WHERE source = 'FLIGHT' AND source_id = ? AND external_resource_id = ?
                  AND operation_comment = ? AND status = 'PENDING'
                """)) {
            ps.setString(1, sourceId);
            ps.setString(2, externalResourceId);
            ps.setString(3, comment);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(
                "SELECT count(*) FROM crew.team_flight_log WHERE comment = ?")) {
            ps.setString(1, comment);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).as("test-owned flight log cleanup").isZero();
            }
        }
    }

    private TeamCandidate findActiveTeam() throws SQLException {
        String sql = """
                SELECT t.team_id, t.team_name, t.unit_name, t.team_storage_id
                FROM crew.team t JOIN crew.unit u ON u.unit_id = t.unit_id
                WHERE t.team_status = 'ACTIVE' AND t.team_storage_id IS NOT NULL
                  AND u.sync_enabled IS TRUE
                ORDER BY (t.fly_point_id IS NULL) DESC, t.team_id
                LIMIT 1
                """;
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).as("active synced crew on dev").isTrue();
            return new TeamCandidate(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getLong(4));
        }
    }

    private void runSync() {
        Response response = apiExecutor.execute(ApiEndpointDefinition.FAITA_DEV_SYNC_TEAMS_POST,
                UserRole.ADMIN);
        assertThat(response.statusCode()).as("FAITA syncTeams endpoint").isEqualTo(200);
    }

    private long findCreatedWriteOffId() throws SQLException {
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement("""
                SELECT storage_write_off_id
                FROM crew.team_flight_log
                WHERE team_id = ? AND flight_timestamp = ? AND comment = ? AND status = 'CREATED'
                """)) {
            ps.setInt(1, teamId);
            ps.setTimestamp(2, Timestamp.from(flightTimestamp));
            ps.setString(3, comment);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    private void assertPendingSingleWriteOff() throws SQLException {
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement("""
                SELECT count(*), min(status::text)
                FROM storage_item_write_off
                WHERE source = 'FLIGHT' AND source_id = ? AND external_resource_id = ?
                  AND operation_comment = ?
                """)) {
            ps.setString(1, sourceId);
            ps.setString(2, externalResourceId);
            ps.setString(3, comment);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).as("one write-off after repeated sync").isEqualTo(1);
                assertThat(rs.getString(2)).as("unmapped resource stays pending").isEqualTo("PENDING");
            }
        }
    }

    private boolean hasPositionColumn() throws SQLException {
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement("""
                SELECT 1 FROM information_schema.columns
                WHERE table_name = 'storage_item_write_off' AND column_name = 'position_name'
                """); ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }

    private record TeamCandidate(int id, String name, String unitName, long storageId) {}
}
