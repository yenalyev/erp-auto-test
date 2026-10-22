package com.erp.tests.functional.analytics;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.RelocationExportColumnsFixture;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.assertions.RelocationExportColumnsAssertions;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import org.testng.SkipException;
import org.testng.annotations.*;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static com.erp.api.endpoints.ApiEndpointDefinition.EXPORT_RELOCATIONS_GET;
import static org.assertj.core.api.Assertions.assertThat;

@Epic("Експорт даних")
@Feature("Переміщення — порядок колонок, групи та kind")
public class RelocationExportColumnsApiTest extends BaseFunctionalTest {
    private RelocationExportColumnsFixture basic;
    private RelocationExportColumnsFixture groups;
    private final LocalDate today = LocalDate.now(ZoneId.of("Europe/Kyiv"));

    @Test(priority = 1)
    @TestCaseId("TC-REL-COL-API-001")
    public void exactHeadersAndColumnOrder() {
        RelocationExportColumnsAssertions.rows(export(today, today));
    }

    @Test(priority = 2)
    @TestCaseId("TC-REL-COL-API-002")
    public void kindBelongsToRecipientRow() {
        RelocationExportColumnsAssertions.assertKinds(exportWithBasicData(), basic);
    }

    @Test(priority = 3)
    @TestCaseId("TC-REL-COL-API-003")
    public void recipientGroupsAcrossAllKinds() throws Exception {
        arrangeGroups();
        RelocationExportColumnsAssertions.assertGroups(export(today, today), groups, false);
    }

    @Test(priority = 4)
    @TestCaseId("TC-REL-COL-API-004")
    public void senderGroupsAreIndependentOfRecipientGroups() throws Exception {
        arrangeGroups();
        RelocationExportColumnsAssertions.assertGroups(export(today, today), groups, true);
    }

    @Test(priority = 5)
    @TestCaseId("TC-REL-COL-API-005")
    @Severity(SeverityLevel.CRITICAL)
    public void excelMatchesBothJournalTabsWithoutDuplicatingBatchRows() {
        arrangeBasic();
        var journal = basic.journalOracle();
        assertThat(journal).hasSize(4);
        assertThat(journal.stream().mapToInt(r -> r.getItems().size()).sum()).isEqualTo(8);
        RelocationExportColumnsAssertions.assertJournalMatches(export(today, today), basic, journal);
        RelocationExportColumnsAssertions.assertJournalMatches(export(today, today), basic, journal);
    }

    @Test(priority = 6)
    @TestCaseId("TC-REL-COL-API-006")
    public void dateRangeIncludesTodayAndExcludesTomorrow() {
        arrangeBasic();
        assertThat(RelocationExportColumnsAssertions.ownedRows(export(today, today), basic)).hasSize(8);
        assertThat(RelocationExportColumnsAssertions.ownedRows(export(today.plusDays(1), today.plusDays(1)), basic)).isEmpty();
    }

    private void arrangeBasic() {
        if (basic != null) return;
        basic = new RelocationExportColumnsFixture(testContext, apiExecutor);
        basic.arrangeKinds(ConfigProvider.getOwner1StorageId());
    }

    private byte[] exportWithBasicData() { arrangeBasic(); return export(today, today); }

    private void arrangeGroups() throws Exception {
        if (getDbHelper() == null) throw new SkipException("Group oracle requires read-only JDBC: -Duse.database=true -Dssh.enabled=false");
        if (groups != null) return;
        groups = new RelocationExportColumnsFixture(testContext, apiExecutor);
        groups.arrangeGroups(getDbHelper().getConnection());
        assertThat(groups.transfers()).hasSize(12);
    }

    private byte[] export(LocalDate start, LocalDate end) {
        var response = apiExecutor.executeWithQueryParams(EXPORT_RELOCATIONS_GET, UserRole.ADMIN,
                Map.of("start", start.toString(), "end", end.toString()));
        assertThat(response.statusCode()).as("Global relocation Excel").isEqualTo(200);
        byte[] bytes = response.asByteArray();
        Allure.addAttachment("Переміщення " + start + " — " + end, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ByteArrayInputStream(bytes), "xlsx");
        return bytes;
    }

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        try { if (groups != null) groups.cleanup(); }
        finally { if (basic != null) basic.cleanup(); }
    }
}
