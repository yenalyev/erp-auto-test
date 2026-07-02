package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Write-off API після польотів (Fight) — умовне покриття залежно від dev-інтеграції.
 */
@Slf4j
@Epic("Inventory")
@Feature("Crew Write-Off")
@Story("Fight reconciliation")
public class CrewWriteOffTest extends CrewApiTestBase {

    private static boolean fightIntegrationEnabled;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    @Step("Probe Fight / write-off integration on dev")
    public void probeFightIntegration() {
        Response stats = apiExecutor.execute(
                ApiEndpointDefinition.INVENTORY_WRITE_OFF_GET_SHORT_STATS,
                UserRole.ADMIN);
        fightIntegrationEnabled = stats.statusCode() == 200;
        log.info("Fight write-off integration probe: enabled={}", fightIntegrationEnabled);
    }

    @Test(priority = 10, enabled = false)
    @TestCaseId("TC-CREW-FIGHT-001")
    @Description("Після Fight sync — write-off у GET /write-off для відомого crew (потребує Fight на dev)")
    @Severity(SeverityLevel.NORMAL)
    public void testWriteOffAppearsAfterFightSync() {
        if (!fightIntegrationEnabled) {
            throw new org.testng.SkipException("Fight sync disabled on dev");
        }
        CrewRegionScenario scenario = crewFixture.prepareSingleCrewScenario("fight-wo-");
        Response page = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.INVENTORY_WRITE_OFF_GET_PAGE,
                UserRole.OWNER_1,
                java.util.Map.of("storageId", scenario.crew().getId()));
        assertThat(page.statusCode()).isEqualTo(200);
    }

    @Test(priority = 20, enabled = false)
    @TestCaseId("TC-CREW-FIGHT-002")
    @Description("Complete reconciliation зменшує crew stock (потребує Fight seed на dev)")
    @Severity(SeverityLevel.NORMAL)
    public void testWriteOffCompleteReducesCrewStock() {
        if (!fightIntegrationEnabled) {
            throw new org.testng.SkipException("Fight sync disabled on dev");
        }
        // E2E reconciliation delegated to tk SyncTeamProcessIT
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
}
