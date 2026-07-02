package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.models.query.RelocationJournalQuery;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Фільтрація журналу переміщень для ролі accountant (бізнес-контракт).
 */
@Slf4j
@Epic("Relocation")
@Feature("Accountant Visibility")
@Story("Relocation Journal Filter")
public class AccountantRelocationFilterTest extends CrewApiTestBase {

    private static final String RESOURCE_PREFIX = "acc-rel-";
    private static final double SEND_AMOUNT = 5.0;

    private CrewRegionScenario scenario;
    private Long resourceId;
    private StorageResponse secondUnit;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    @Step("Підготовка fixtures для accountant relocation filter")
    public void setupAccountantRelocationFilterTests() {
        storageFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        relocationFixture.prepareContext();

        scenario = crewFixture.prepareSingleCrewScenario("acc-rel-");
        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        resourceId = resource.getId();

        Long parentId = scenario.unit().getParent() != null
                ? scenario.unit().getParent().getId()
                : storageFixture.resolveParentUnit().getId();
        secondUnit = storageFixture.createUnitStorage(parentId, "acc-rel-u2-");
    }

    @BeforeMethod(alwaysRun = true)
    public void ensureStock() {
        relocationFixture.ensureStock(scenario.memberStorageId(), resourceId, 100.0);
        relocationFixture.ensureStock(scenario.unit().getId(), resourceId, 50.0);
        relocationFixture.ensureStock(owner1StorageId, resourceId, 100.0);
    }

    @Test(priority = 10)
    @TestCaseId("TC-ACC-API-002")
    @Description("""
            Fixture: UNIT→CREW, UNIT→UNIT, STORAGE→UNIT.
            GET /relocations як ACCOUNTANT — лише STORAGE→UNIT у результатах.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testAccountantSeesOnlyStorageToUnitRelocations() {
        RelocationResponse unitToCrew = relocationFixture.createSend(
                UserRole.OWNER_1,
                scenario.unit().getId(),
                scenario.crew().getId(),
                resourceId,
                SEND_AMOUNT);

        RelocationResponse unitToUnit = relocationFixture.createSend(
                UserRole.OWNER_1,
                scenario.unit().getId(),
                secondUnit.getId(),
                resourceId,
                SEND_AMOUNT);

        RelocationResponse storageToUnit = relocationFixture.createSend(
                UserRole.OWNER_1,
                owner1StorageId,
                owner2StorageId,
                resourceId,
                SEND_AMOUNT);

        RelocationJournalQuery query = RelocationJournalQuery.sentHistoryUi(owner1StorageId)
                .toBuilder()
                .pageSize(100)
                .build();

        List<RelocationResponse> page = relocationFixture.getJournalPage(query, UserRole.ACCOUNTANT);
        Set<Long> ids = page.stream().map(RelocationResponse::getId).collect(Collectors.toSet());

        assertThat(ids)
                .as("STORAGE→UNIT має бути видимий для accountant")
                .contains(storageToUnit.getId());
        assertThat(ids)
                .as("UNIT→CREW не повинен бути видимий для accountant")
                .doesNotContain(unitToCrew.getId());
        assertThat(ids)
                .as("UNIT→UNIT не повинен бути видимий для accountant")
                .doesNotContain(unitToUnit.getId());
    }
}
