package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/** Read-only check for artifacts left by a controlled FAITA dev run. */
@Epic("Integration")
@Feature("FAITA crew sync and write-off")
public class FaitaCrewSyncCleanupAuditTest extends BaseFunctionalTest {

    @TestCaseId("TC-FAITA-SYNC-CLEANUP-001")
    @Description("The uniquely tagged dev flight log and write-offs were removed")
    @Test
    public void controlledFlightArtifactsAreAbsent() throws SQLException {
        String marker = System.getProperty("faita.audit.comment");
        assertThat(marker).as("provide -Dfaita.audit.comment=erp-auto-test-faita-pos-...")
                .startsWith("erp-auto-test-faita-pos-");
        assertThat(getDbHelper()).as("requires use.database=true").isNotNull();

        long logs = count("SELECT count(*) FROM crew.team_flight_log WHERE comment = ?", marker);
        long writeOffs = count("SELECT count(*) FROM storage_item_write_off WHERE operation_comment = ?", marker);
        String status = flightLogStatus(marker);
        assertThat(logs)
                .as("test-owned flight log remains: status=%s, writeOffCount=%s", status, writeOffs)
                .isZero();
        assertThat(writeOffs).as("test-owned write-off remains").isZero();
    }

    @TestCaseId("TC-FAITA-SYNC-CLEANUP-002")
    @Description("Remove one known NEW flight log after a failed dev sync, with no write-off")
    @Test
    public void removeKnownUnprocessedFlightLog() throws SQLException {
        if (!Boolean.getBoolean("faita.audit.cleanup.enabled")) {
            throw new SkipException("Explicit cleanup opt-in required");
        }
        String marker = System.getProperty("faita.audit.comment");
        assertThat(marker).startsWith("erp-auto-test-faita-pos-");
        assertThat(getDbHelper()).isNotNull();
        assertThat(count("SELECT count(*) FROM crew.team_flight_log WHERE comment = ?", marker))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM storage_item_write_off WHERE operation_comment = ?", marker))
                .isZero();
        assertThat(flightLogStatus(marker)).isEqualTo("NEW");
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement("""
                DELETE FROM crew.team_flight_log
                WHERE comment = ? AND ammunition_name = ? AND status = 'NEW'
                """)) {
            ps.setString(1, marker);
            ps.setString(2, marker);
            assertThat(ps.executeUpdate()).as("exactly one test-owned NEW flight log deleted")
                    .isEqualTo(1);
        }
        assertThat(count("SELECT count(*) FROM crew.team_flight_log WHERE comment = ?", marker))
                .isZero();
    }

    private long count(String sql, String marker) throws SQLException {
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(sql)) {
            ps.setString(1, marker);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getLong(1);
            }
        }
    }

    private String flightLogStatus(String marker) throws SQLException {
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(
                "SELECT status FROM crew.team_flight_log WHERE comment = ?")) {
            ps.setString(1, marker);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "absent";
            }
        }
    }
}
