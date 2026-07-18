package com.erp.tests.functional.assembly_readiness;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.AssemblyReadinessFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.response.AssemblyComponentResponse;
import com.erp.models.response.AssemblyReadinessResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Assembly readiness uses {@code TechnologicalMapInputs.effectiveInputs()} — default alt only.
 */
@Slf4j
@Epic("Production")
@Feature("Assembly Readiness — alternative groups")
public class AssemblyReadinessAlternativeGroupsApiTest extends BaseFunctionalTest {

    private AssemblyReadinessFixture fixture;
    private TechnologicalMapFixture techMapFixture;
    private Long storageId;
    private final List<Long> techMapsToCleanup = new ArrayList<>();
    private final List<Long> resourcesToCleanup = new ArrayList<>();

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setup() {
        fixture = new AssemblyReadinessFixture(testContext, apiExecutor);
        techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        fixture.prepareContext();
        storageId = ConfigProvider.getOwner1StorageId();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        for (Long techMapId : techMapsToCleanup) {
            fixture.cleanupTechMap(UserRole.ADMIN, techMapId, storageId);
        }
        techMapsToCleanup.clear();
        for (Long resourceId : resourcesToCleanup) {
            fixture.cleanupResource(UserRole.ADMIN, resourceId);
        }
        resourcesToCleanup.clear();
    }

    @Test(priority = 10)
    @TestCaseId("TC-AR-ALT-001")
    @Story("Assembly readiness counts default alternative only")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Техкарта F + {D default@2, E@3} → P.
            assembly-readiness components містять F і D (default), не E.
            """)
    public void testAssemblyReadinessUsesDefaultAlternativeOnly() {
        TechnologicalMapResponse techMap = techMapFixture.createTechMapWithAlternativeGroup(UserRole.ADMIN, storageId);
        techMapsToCleanup.add(techMap.getId());

        Long fixedId = techMap.getInput().getFirst().getResource().getId();
        Long defaultAltId = techMap.getGroups().getFirst().getAlternativeResources().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                .map(r -> r.getResource().getId())
                .findFirst()
                .orElseThrow();
        Long otherAltId = techMap.getGroups().getFirst().getAlternativeResources().stream()
                .filter(r -> !Boolean.TRUE.equals(r.getIsDefault()))
                .map(r -> r.getResource().getId())
                .findFirst()
                .orElseThrow();
        double defaultAltAmount = techMap.getGroups().getFirst().getAlternativeResources().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                .mapToDouble(r -> r.getAmount())
                .findFirst()
                .orElseThrow();

        fixture.seedComponentStock(storageId, UserRole.OWNER_1, Map.of(
                fixedId, 50.0,
                defaultAltId, 50.0,
                otherAltId, 50.0));

        List<AssemblyReadinessResponse> rows = fixture.getReadiness(UserRole.OWNER_1, storageId);
        AssemblyReadinessResponse row = fixture.findByTechMapId(rows, techMap.getId())
                .orElseThrow(() -> new AssertionError("Tech map not in assembly-readiness"));

        Optional<AssemblyComponentResponse> fixedComp = fixture.findComponent(row, fixedId);
        Optional<AssemblyComponentResponse> defaultComp = fixture.findComponent(row, defaultAltId);
        Optional<AssemblyComponentResponse> otherComp = fixture.findComponent(row, otherAltId);

        assertThat(fixedComp).isPresent();
        assertThat(defaultComp).isPresent();
        assertThat(defaultComp.get().getRequiredPerUnit())
                .isEqualByComparingTo(BigDecimal.valueOf(defaultAltAmount));
        assertThat(otherComp).isEmpty();
    }
}
