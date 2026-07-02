package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.models.response.ResourceResponse;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Slf4j
@Epic("Relocation")
@Feature("Crew Issuance")
@Story("Operation History")
public class CrewOperationHistoryTest extends CrewApiTestBase {

    private static final String RESOURCE_PREFIX = "crew-hist-";
    private static final double ISSUE_AMOUNT = 12.0;

    private CrewRegionScenario scenario;
    private Long resourceId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    @Step("Підготовка: CREWS region + ресурс")
    public void setupCrewOperationHistoryTests() {
        storageFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        relocationFixture.prepareContext();

        scenario = crewFixture.prepareSingleCrewScenario("crew-hist-");
        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        resourceId = resource.getId();
    }

    @BeforeMethod(alwaysRun = true)
    public void ensureSenderStock() {
        relocationFixture.ensureStock(scenario.memberStorageId(), resourceId, 100.0);
    }

    @Test(priority = 10)
    @TestCaseId("TC-CREW-HIST-001")
    @Description(StorageRegionsAllureDescriptions.TC_CREW_HIST_001)
    @Severity(SeverityLevel.CRITICAL)
    public void testCrewSendIncreasesRemovedSummaryOnSender() {
        double beforeRemoved = extractRemovedAmount(scenario.memberStorageId(), resourceId);

        relocationFixture.createSend(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                scenario.crew().getId(),
                resourceId,
                ISSUE_AMOUNT);

        double afterRemoved = extractRemovedAmount(scenario.memberStorageId(), resourceId);
        assertThat(afterRemoved - beforeRemoved).isCloseTo(ISSUE_AMOUNT, within(0.01));
    }

    private double extractRemovedAmount(long storageId, long resourceId) {
        Response history = inventoryFixture.getOperationHistoryToday(storageId, UserRole.OWNER_1);
        assertThat(history.statusCode()).isEqualTo(200);
        return extractAmountFromSummaryList(history, "totalRemovedResources", resourceId);
    }

    private static double extractAmountFromSummaryList(Response history, String listKey, long resourceId) {
        var entries = history.jsonPath().getList(listKey);
        if (entries == null || entries.isEmpty()) {
            return 0.0;
        }
        for (int i = 0; i < entries.size(); i++) {
            Long id = history.jsonPath().getLong(listKey + "[" + i + "].resource.id");
            if (id != null && id == resourceId) {
                Number amount = history.jsonPath().get(listKey + "[" + i + "].amount");
                return amount != null ? amount.doubleValue() : 0.0;
            }
        }
        return 0.0;
    }
}
