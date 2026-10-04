package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.fixtures.FaitaResourceFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.utils.helpers.ProductionStockAssertions.StockSnapshot;
import com.erp.utils.helpers.RelocationStockAssertions;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.Array;
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

/** Controlled spotter routing and debit checks. All storages/resources are test-owned. */
@Epic("Integration")
@Feature("FAITA spotter write-off")
public class FaitaSpotterWriteOffTest extends CrewApiTestBase {
    private static final double AMOUNT = 2.0;
    private ResourceResponse resource;
    private FaitaResourceFixture faitaFixture;
    private List<Long> reconciliationIds = List.of();
    private String externalId;
    private Long pendingWriteOffId;
    private Long sourceLogId;
    private String sourceMarker;
    private Integer firstTeamId;
    private Integer secondTeamId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    public void prepare() {
        assertThat(System.getProperty("env")).as("spotter integration suite is dev-only").isEqualTo("dev");
        if (getDbHelper() == null) {
            throw new SkipException("Spotter tests require -Duse.database=true");
        }
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        resource = resourceFixture.createUniqueResource("faita-spotter-");
        externalId = Integer.toString(100_000_000 + Math.floorMod(UUID.randomUUID().hashCode(), 800_000_000));
        faitaFixture = new FaitaResourceFixture(testContext, apiExecutor);
        reconciliationIds = faitaFixture.createFlightReconciliation(externalId, resource.getName(), resource.getId());
        relocationFixture.ensureStock(owner1StorageId, resource.getId(), 30);
    }

    @AfterMethod(alwaysRun = true)
    public void cleanSourceFixture() throws SQLException {
        if (getDbHelper() == null) return;
        // Never delete a completed write-off: its stock movement is historical evidence.
        if (sourceLogId != null) {
            try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(
                    "SELECT storage_write_off_id FROM crew.team_flight_log WHERE entry_id = ?")) {
                ps.setLong(1, sourceLogId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getObject(1) != null) pendingWriteOffId = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(
                    "DELETE FROM crew.team_flight_log WHERE entry_id = ?")) {
                ps.setLong(1, sourceLogId);
                ps.executeUpdate();
            }
            sourceLogId = null;
        }
        if (pendingWriteOffId != null) {
            try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(
                    "DELETE FROM storage_item_write_off WHERE id = ? AND status IN ('PENDING', 'FAILED')")) {
                ps.setLong(1, pendingWriteOffId);
                ps.executeUpdate();
            }
            pendingWriteOffId = null;
        }
        if (sourceMarker != null) {
            try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement("""
                    DELETE FROM storage_item_write_off
                    WHERE source = 'FLIGHT' AND operation_comment = ?
                      AND external_resource_name = ? AND status IN ('PENDING', 'FAILED')
                    """)) {
                ps.setString(1, sourceMarker);
                ps.setString(2, sourceMarker);
                ps.executeUpdate();
            }
        }
        for (Integer teamId : new Integer[]{firstTeamId, secondTeamId}) {
            if (teamId == null) continue;
            try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(
                    "DELETE FROM crew.team WHERE team_id = ?")) {
                ps.setInt(1, teamId);
                ps.executeUpdate();
            }
        }
        firstTeamId = null;
        secondTeamId = null;
        sourceMarker = null;
    }

    @AfterClass(alwaysRun = true)
    public void cleanMapping() {
        if (faitaFixture != null) faitaFixture.deleteReconciliationsQuietly(reconciliationIds);
        if (resource != null) resourceFixture.deactivate(UserRole.ADMIN, resource.getId());
    }

    @TestCaseId("TC-FAITA-SPOT-001")
    @Description("One spotter: debit its FLY_POINT, not the flight crew or CREW storage")
    @Severity(SeverityLevel.CRITICAL)
    @Test(priority = 10)
    public void oneSpotterDebitsItsFlyPoint() throws SQLException {
        CrewRegionScenario first = point("spot-one-");
        stockFirst(first);
        StockSnapshot beforePoint = stock(first.flyPoint().getId());
        StockSnapshot beforeCrew = stock(first.crew().getId());
        long id = seedPending(first.crew().getId(), null);

        complete(id);

        assertThat(status(id)).isEqualTo("COMPLETED");
        RelocationStockAssertions.assertDebitedFromSender(beforePoint, stock(first.flyPoint().getId()),
                first.flyPoint().getId(), resource.getId(), AMOUNT, "single spotter point");
        RelocationStockAssertions.assertUnchanged(beforeCrew, stock(first.crew().getId()),
                first.crew().getId(), resource.getId(), "spotter CREW is not debited");
    }

    @TestCaseId("TC-FAITA-SPOT-002")
    @Description("First spotter has no stock: debit the second spotter point")
    @Severity(SeverityLevel.CRITICAL)
    @Test(priority = 20)
    public void secondSpotterIsUsedWhenFirstHasNoStock() throws SQLException {
        CrewRegionScenario first = point("spot-empty-");
        CrewRegionScenario second = point("spot-stock-");
        stockFirst(second);
        StockSnapshot beforeFirst = stock(first.flyPoint().getId());
        StockSnapshot beforeSecond = stock(second.flyPoint().getId());
        long id = seedPending(first.crew().getId(), second.crew().getId());

        complete(id);

        assertThat(status(id)).isEqualTo("COMPLETED");
        RelocationStockAssertions.assertUnchanged(beforeFirst, stock(first.flyPoint().getId()),
                first.flyPoint().getId(), resource.getId(), "empty first spotter point");
        RelocationStockAssertions.assertDebitedFromSender(beforeSecond, stock(second.flyPoint().getId()),
                second.flyPoint().getId(), resource.getId(), AMOUNT, "second spotter point");
    }

    @TestCaseId("TC-FAITA-SPOT-002B")
    @Description("First spotter has only partial stock: debit the second point in full")
    @Severity(SeverityLevel.CRITICAL)
    @Test(priority = 25)
    public void secondSpotterIsUsedWhenFirstStockIsInsufficient() throws SQLException {
        CrewRegionScenario first = point("spot-partial-");
        CrewRegionScenario second = point("spot-full-");
        issue(first, 1);
        stockFirst(second);
        StockSnapshot beforeFirst = stock(first.flyPoint().getId());
        StockSnapshot beforeSecond = stock(second.flyPoint().getId());
        long id = seedPending(first.crew().getId(), second.crew().getId());

        complete(id);

        assertThat(status(id)).isEqualTo("COMPLETED");
        RelocationStockAssertions.assertUnchanged(beforeFirst, stock(first.flyPoint().getId()),
                first.flyPoint().getId(), resource.getId(), "partial first spotter point");
        RelocationStockAssertions.assertDebitedFromSender(beforeSecond, stock(second.flyPoint().getId()),
                second.flyPoint().getId(), resource.getId(), AMOUNT, "full second spotter point");
    }

    @TestCaseId("TC-FAITA-SPOT-003")
    @Description("Both spotter points have stock: debit only the first")
    @Severity(SeverityLevel.CRITICAL)
    @Test(priority = 30)
    public void firstSpotterWinsWhenBothHaveStock() throws SQLException {
        CrewRegionScenario first = point("spot-first-");
        CrewRegionScenario second = point("spot-second-");
        stockFirst(first);
        stockFirst(second);
        StockSnapshot beforeFirst = stock(first.flyPoint().getId());
        StockSnapshot beforeSecond = stock(second.flyPoint().getId());
        long id = seedPending(first.crew().getId(), second.crew().getId());

        complete(id);

        assertThat(status(id)).isEqualTo("COMPLETED");
        RelocationStockAssertions.assertDebitedFromSender(beforeFirst, stock(first.flyPoint().getId()),
                first.flyPoint().getId(), resource.getId(), AMOUNT, "first spotter point");
        RelocationStockAssertions.assertUnchanged(beforeSecond, stock(second.flyPoint().getId()),
                second.flyPoint().getId(), resource.getId(), "second spotter point");
    }

    @TestCaseId("TC-FAITA-SPOT-007")
    @Description("Journal position differs from spotter point: debit only the spotter point")
    @Severity(SeverityLevel.CRITICAL)
    @Test(priority = 35)
    public void journalPositionDoesNotOverrideSpotterPoint() throws SQLException {
        CrewRegionScenario spotter = point("spot-position-");
        CrewRegionScenario flight = point("flight-position-");
        stockFirst(spotter);
        stockFirst(flight);
        StockSnapshot beforeSpotter = stock(spotter.flyPoint().getId());
        StockSnapshot beforeFlight = stock(flight.flyPoint().getId());
        long id = seedPending(spotter.crew().getId(), null, flight.flyPoint().getId());

        complete(id);

        assertThat(status(id)).isEqualTo("COMPLETED");
        RelocationStockAssertions.assertDebitedFromSender(beforeSpotter, stock(spotter.flyPoint().getId()),
                spotter.flyPoint().getId(), resource.getId(), AMOUNT, "spotter point despite journal position");
        RelocationStockAssertions.assertUnchanged(beforeFlight, stock(flight.flyPoint().getId()),
                flight.flyPoint().getId(), resource.getId(), "flight position is a label, not debit target");
    }

    @TestCaseId("TC-FAITA-SPOT-004")
    @Description("Neither spotter has stock: no partial debit")
    @Severity(SeverityLevel.NORMAL)
    @Test(priority = 40)
    public void neitherSpotterHasStock() throws SQLException {
        CrewRegionScenario first = point("spot-zero-a-");
        CrewRegionScenario second = point("spot-zero-b-");
        StockSnapshot beforeFirst = stock(first.flyPoint().getId());
        StockSnapshot beforeSecond = stock(second.flyPoint().getId());
        long id = seedPending(first.crew().getId(), second.crew().getId());

        complete(id);

        assertThat(status(id)).isEqualTo("FAILED");
        RelocationStockAssertions.assertUnchanged(beforeFirst, stock(first.flyPoint().getId()),
                first.flyPoint().getId(), resource.getId(), "no stock at first point");
        RelocationStockAssertions.assertUnchanged(beforeSecond, stock(second.flyPoint().getId()),
                second.flyPoint().getId(), resource.getId(), "no stock at second point");
    }

    @TestCaseId("TC-FAITA-SPOT-005")
    @Description("Fight spotter_team_id order is retained in the generated FLIGHT write-off")
    @Severity(SeverityLevel.CRITICAL)
    @Test(priority = 50)
    public void fightLogRoutesToOrderedSpotters() throws SQLException {
        if (!Boolean.getBoolean("faita.spotter.sync.enabled")) {
            throw new SkipException("Global dev sync opt-in: -Dfaita.spotter.sync.enabled=true");
        }
        CrewRegionScenario first = point("spot-src-a-");
        CrewRegionScenario second = point("spot-src-b-");
        firstTeamId = uniqueTeamId();
        secondTeamId = uniqueTeamId();
        insertTeam(firstTeamId, first);
        insertTeam(secondTeamId, second);
        MainTeam main = findMainTeam();
        String marker = "erp-spotter-" + UUID.randomUUID().toString().substring(0, 12);
        sourceMarker = marker;
        Instant flightTime = Instant.now().minusSeconds(5);
        insertLog(main, marker, flightTime);

        Response sync = apiExecutor.execute(ApiEndpointDefinition.FAITA_DEV_SYNC_TEAMS_POST, UserRole.ADMIN);
        assertThat(sync.statusCode()).as("syncTeams: %s", sync.getBody().asString()).isEqualTo(200);
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement("""
                SELECT l.status, l.storage_write_off_id, w.storage_id, w.alternative_storage_ids,
                       w.status
                FROM crew.team_flight_log l
                JOIN storage_item_write_off w ON w.id = l.storage_write_off_id
                WHERE l.entry_id = ?
                """)) {
            ps.setLong(1, sourceLogId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("Fight log produced a write-off").isTrue();
                assertThat(rs.getString(1)).isEqualTo("CREATED");
                pendingWriteOffId = rs.getLong(2);
                assertThat(rs.getLong(3)).as("first spotter CREW id").isEqualTo(first.crew().getId());
                Array alternatives = rs.getArray(4);
                assertThat(alternatives).isNotNull();
                assertThat((Long[]) alternatives.getArray()).containsExactly(second.crew().getId());
                assertThat(rs.getString(5)).isEqualTo("PENDING");
            }
        }
        assertThat(writeOffCount(marker)).as("one write-off for one Fight event").isEqualTo(1);
        Response repeated = apiExecutor.execute(ApiEndpointDefinition.FAITA_DEV_SYNC_TEAMS_POST, UserRole.ADMIN);
        assertThat(repeated.statusCode()).as("repeated syncTeams: %s", repeated.getBody().asString())
                .isEqualTo(200);
        assertThat(writeOffCount(marker)).as("repeated sync must not duplicate write-off").isEqualTo(1);
    }

    private CrewRegionScenario point(String prefix) {
        CrewRegionScenario scenario = crewFixture.prepareAttachedCrewScenario(prefix);
        refreshRoleSessions(UserRole.ADMIN, UserRole.OWNER_1);
        return scenario;
    }

    private void stockFirst(CrewRegionScenario scenario) {
        issue(scenario, 5);
    }

    private void issue(CrewRegionScenario scenario, double amount) {
        relocationFixture.createSendAndFinishBySender(UserRole.OWNER_1,
                scenario.memberStorageId(), scenario.crew().getId(), resource.getId(), amount);
    }

    private StockSnapshot stock(long id) {
        return RelocationStockAssertions.capture(apiExecutor, id, UserRole.ADMIN,
                Set.of(resource.getId()), "spotter stock");
    }

    private long seedPending(long primaryCrewId, Long alternativeCrewId) throws SQLException {
        return seedPending(primaryCrewId, alternativeCrewId, null);
    }

    private long seedPending(long primaryCrewId, Long alternativeCrewId, Long journalPositionId)
            throws SQLException {
        String sql = """
                INSERT INTO storage_item_write_off
                    (date_time, storage_id, alternative_storage_ids, fly_point_storage_id, resources,
                     external_resource_id, external_resource_name, amount, source,
                     operation_comment, status, source_id)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, 'FLIGHT', ?, 'PENDING', ?)
                RETURNING id
                """;
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setLong(2, primaryCrewId);
            if (alternativeCrewId == null) ps.setNull(3, java.sql.Types.ARRAY);
            else ps.setArray(3, getDbHelper().getConnection().createArrayOf("int8", new Long[]{alternativeCrewId}));
            if (journalPositionId == null) ps.setNull(4, java.sql.Types.BIGINT);
            else ps.setLong(4, journalPositionId);
            ps.setString(5, "[{\"id\":" + resource.getId() + ",\"name\":\"" + resource.getName() + "\"}]");
            ps.setString(6, externalId);
            ps.setString(7, resource.getName());
            ps.setBigDecimal(8, java.math.BigDecimal.valueOf(AMOUNT));
            ps.setString(9, "erp-auto-test spotter routing");
            ps.setString(10, "erp-spotter-" + UUID.randomUUID());
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                pendingWriteOffId = rs.getLong(1);
                return pendingWriteOffId;
            }
        }
    }

    private void complete(long id) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.INVENTORY_WRITE_OFF_PUT_COMPLETE,
                UserRole.ADMIN, Map.of("writeOffIdentifiers", List.of(id)));
        assertThat(response.statusCode()).as("complete write-off id=%s: %s", id,
                response.getBody().asString()).isEqualTo(200);
    }

    private String status(long id) throws SQLException {
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(
                "SELECT status FROM storage_item_write_off WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString(1);
            }
        }
    }

    private long writeOffCount(String marker) throws SQLException {
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement("""
                SELECT count(*) FROM storage_item_write_off
                WHERE source = 'FLIGHT' AND operation_comment = ?
                """)) {
            ps.setString(1, marker);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getLong(1);
            }
        }
    }

    private int uniqueTeamId() throws SQLException {
        for (int attempt = 0; attempt < 20; attempt++) {
            int candidate = 100_000_000 + Math.floorMod(UUID.randomUUID().hashCode(), 800_000_000);
            if (firstTeamId != null && candidate == firstTeamId) continue;
            try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(
                    "SELECT 1 FROM crew.team WHERE team_id = ?")) {
                ps.setInt(1, candidate);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return candidate;
                }
            }
        }
        throw new IllegalStateException("Could not reserve a unique spotter team id");
    }

    private void insertTeam(int teamId, CrewRegionScenario scenario) throws SQLException {
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement("""
                INSERT INTO crew.team (team_id, team_name, team_status, unit_id, unit_name,
                                       team_storage_id, unit_storage_id)
                VALUES (?, ?, 'ACTIVE', ?, ?, ?, ?)
                """)) {
            ps.setInt(1, teamId);
            ps.setString(2, scenario.crew().getName());
            ps.setInt(3, teamId);
            ps.setString(4, scenario.unit().getName());
            ps.setLong(5, scenario.crew().getId());
            ps.setLong(6, scenario.unit().getId());
            ps.executeUpdate();
        }
    }

    private MainTeam findMainTeam() throws SQLException {
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement("""
                SELECT t.team_id, t.team_name, t.unit_name
                FROM crew.team t JOIN crew.unit u ON u.unit_id = t.unit_id
                WHERE t.team_status = 'ACTIVE' AND t.team_storage_id IS NOT NULL
                  AND u.sync_enabled IS TRUE
                ORDER BY t.team_id LIMIT 1
                """); ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).as("active synced flight crew on dev").isTrue();
            return new MainTeam(rs.getInt(1), rs.getString(2), rs.getString(3));
        }
    }

    private void insertLog(MainTeam main, String marker, Instant when) throws SQLException {
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement("""
                INSERT INTO crew.team_flight_log
                    (flight_timestamp, unit_name, sub_unit_name, team_id, team_name,
                     spotter_team_id, ammunition_id, ammunition_name, amount, comment, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, 'NEW') RETURNING entry_id
                """)) {
            ps.setTimestamp(1, Timestamp.from(when));
            ps.setString(2, main.unitName());
            ps.setString(3, "QA");
            ps.setInt(4, main.id());
            ps.setString(5, main.name());
            ps.setArray(6, getDbHelper().getConnection().createArrayOf("int4",
                    new Integer[]{firstTeamId, secondTeamId}));
            ps.setInt(7, -100_000_000 - Math.floorMod(marker.hashCode(), 800_000_000));
            ps.setString(8, marker);
            ps.setString(9, marker);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                sourceLogId = rs.getLong(1);
            }
        }
    }

    private record MainTeam(int id, String name, String unitName) {}
}
