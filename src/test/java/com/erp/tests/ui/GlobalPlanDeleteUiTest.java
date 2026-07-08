package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.GlobalPlanFixture;
import com.erp.models.common.GlobalPlanChainContext;
import com.erp.models.request.DecompositionRequest;
import com.erp.models.response.GenerationResponse;
import com.erp.models.response.GlobalPlanResponse;
import com.erp.pages.GlobalPlansPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Production Planning")
@Feature("Global Plans UI")
public class GlobalPlanDeleteUiTest extends BaseUITest {

    private GlobalPlanFixture globalPlanFixture;
    private GlobalPlanChainContext chain;
    private final List<Long> generatedPlanIdsToCleanup = new ArrayList<>();

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();

        globalPlanFixture = new GlobalPlanFixture(testContext, apiExecutor);
        chain = globalPlanFixture.prepareDecompositionChain();

        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.ADMIN.getUsername(), UserRole.ADMIN.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        injectAllLocationsView();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupGeneratedPlans() {
        if (globalPlanFixture != null) {
            globalPlanFixture.cleanupGeneratedPlans(generatedPlanIdsToCleanup);
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-GP-UI-007")
    @Story("Delete global plan from list")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            **Мета:** видалення глобального плану через UI (кнопка «Видалити» + confirm)
            прибирає план зі списку; location-плани залишаються.
            
            **Підготовка (API):** create + generate з повною декомпозицією.
            **Кроки:** відкрити список → видалити → підтвердити dialog.
            
            **Перевірки:** план відсутній у списку; API GET location plans L1 містить згенеровані id.
            """)
    public void deleteGlobalPlanFromListKeepsLocationPlans() {
        final String testCaseId = "TC-GP-UI-007";

        GlobalPlanResponse created = globalPlanFixture.createGlobalPlan(10.0);
        String description = created.getDescription();
        Allure.parameter("globalPlanId", created.getId());
        Allure.parameter("description", description);

        DecompositionRequest decomposition = globalPlanFixture.buildCompleteDecomposition();
        globalPlanFixture.decompose(created.getId(), decomposition);
        GenerationResponse generation = globalPlanFixture.generate(created.getId(), decomposition);
        List<Long> locationPlanIds = generation.getPlans().stream()
                .map(gp -> gp.getPlan().getId())
                .toList();
        generatedPlanIdsToCleanup.addAll(locationPlanIds);

        GlobalPlansPage listPage = new GlobalPlansPage(page).open().waitForPlanVisible(description);
        assertThat(listPage.isPlanVisibleInList(description))
                .as("План має бути у списку перед видаленням")
                .isTrue();
        listPage.attachScreenshot(testCaseId + " — before delete");

        listPage.deletePlanAndConfirm(description);
        page.waitForTimeout(1_000);
        listPage.attachScreenshot(testCaseId + " — after delete");

        assertThat(listPage.isPlanVisibleInList(description))
                .as("План має зникнути зі списку після видалення")
                .isFalse();

        var l1Plans = globalPlanFixture.getLocationPlans(chain.getL1StorageId());
        assertThat(l1Plans).anyMatch(p -> locationPlanIds.contains(p.getId()));

        log.info("{}: global plan deleted via UI, location plans preserved", testCaseId);
    }
}
