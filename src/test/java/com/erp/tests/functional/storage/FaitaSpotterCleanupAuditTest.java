package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.Description;
import org.testng.annotations.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/** Read-only audit for test-owned source rows after the dev spotter suite. */
public class FaitaSpotterCleanupAuditTest extends BaseFunctionalTest {
    @TestCaseId("TC-FAITA-SPOT-CLEANUP-001")
    @Description("No spotter suite flight logs or spotter team fixtures remain on dev")
    @Test
    public void sourceFixturesWereRemoved() throws SQLException {
        assertThat(getDbHelper()).as("requires use.database=true").isNotNull();
        assertThat(count("""
                SELECT count(*) FROM crew.team_flight_log
                WHERE comment LIKE 'erp-spotter-%' AND ammunition_name = comment
                """)).as("test-owned Fight logs").isZero();
        assertThat(count("""
                SELECT count(*) FROM crew.team
                WHERE team_name LIKE 'spot-src-a-%' OR team_name LIKE 'spot-src-b-%'
                """)).as("test-owned spotter team mappings").isZero();
        assertThat(count("""
                SELECT count(*) FROM storage_item_write_off
                WHERE source = 'FLIGHT' AND operation_comment LIKE 'erp-spotter-%'
                  AND external_resource_name = operation_comment
                """)).as("test-owned import write-offs").isZero();
    }

    private long count(String sql) throws SQLException {
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        }
    }
}
