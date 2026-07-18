package com.erp.tests.functional.assembly_readiness;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.AssemblyReadinessFixture;
import com.erp.fixtures.AssemblyReadinessFixture.TechMapSetup;
import com.erp.models.response.AssemblyComponentResponse;
import com.erp.models.response.AssemblyReadinessResponse;
import com.erp.models.response.ResourceResponse;
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

import static com.erp.fixtures.AssemblyReadinessFixture.computeReadyQty;
import static com.erp.fixtures.AssemblyReadinessFixture.possibleUnits;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * API coverage for {@code GET /api/v1/assembly-readiness/{storageId}} — готовність до комплектації
 * готової продукції з наявних залишків компонентів (bottleneck = min по компонентах, UI-side).
 */
@Slf4j
@Epic("Production")
@Feature("Assembly Readiness (Готово до комплектації)")
@Story("GET /assembly-readiness/{storageId}")
public class AssemblyReadinessApiTest extends BaseFunctionalTest {

    private AssemblyReadinessFixture fixture;
    private com.erp.fixtures.ResourceFixture resourceFixture;
    private Long owner1StorageId;
    private Long owner2StorageId;

    private final List<Long> techMapsToCleanup = new ArrayList<>();
    private final List<Long> resourcesToCleanup = new ArrayList<>();

    @BeforeClass(alwaysRun = true)
    public void setupAssemblyReadinessFixtures() {
        if (testContext == null) {
            baseTestClassSetup();
        }
        fixture = new AssemblyReadinessFixture(testContext, apiExecutor);
        resourceFixture = new com.erp.fixtures.ResourceFixture(testContext, apiExecutor);
        fixture.prepareContext();
        owner1StorageId = ConfigProvider.getOwner1StorageId();
        owner2StorageId = ConfigProvider.getOwner2StorageId();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupMethodArtifacts() {
        for (Long techMapId : techMapsToCleanup) {
            fixture.cleanupTechMap(UserRole.ADMIN, techMapId, owner1StorageId);
        }
        techMapsToCleanup.clear();
        for (Long resourceId : resourcesToCleanup) {
            fixture.cleanupResource(UserRole.ADMIN, resourceId);
        }
        resourcesToCleanup.clear();
    }

    @Test(priority = 10)
    @TestCaseId("TC-AR-API-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Happy path: активна PRODUCTION техкарта з двома input-компонентами та stock > 0.
            Assert: контракт відповіді, коректні stock/required, readyQty = min bottleneck.""")
    public void testResponseContractHappyPath() {
        TechMapSetup setup = createAndTrack(
                fixture.createProductionTechMap(UserRole.ADMIN, owner1StorageId, 2.0, 1.0));
        fixture.seedComponentStock(owner1StorageId, UserRole.OWNER_1, Map.of(
                setup.getInput1().getId(), 10.0,
                setup.getInput2().getId(), 5.0));

        List<AssemblyReadinessResponse> rows = fixture.getReadiness(UserRole.OWNER_1, owner1StorageId);
        AssemblyReadinessResponse row = fixture.findByTechMapId(rows, setup.getTechMap().getId())
                .orElseThrow(() -> new AssertionError("Tech map not found in assembly-readiness response"));

        assertThat(row.getTechnologicalMapName()).isEqualTo(setup.getTechMap().getName());
        assertThat(row.getProductName()).isEqualTo(setup.getProduct().getName());
        assertThat(row.getUnit()).isNotBlank();
        assertThat(row.getComponents()).hasSize(2);

        AssemblyComponentResponse comp1 = fixture.findComponent(row, setup.getInput1().getId()).orElseThrow();
        assertThat(comp1.getRequiredPerUnit()).isEqualByComparingTo(BigDecimal.valueOf(2.0));
        assertThat(comp1.getAvailableStock()).isGreaterThanOrEqualTo(BigDecimal.valueOf(10.0));

        assertThat(computeReadyQty(row)).isEqualTo(5);
    }

    @Test(priority = 20)
    @TestCaseId("TC-AR-API-002")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Лише активні PRODUCTION техкарти; деактивована та DISASSEMBLE не повертаються.")
    public void testOnlyActiveProductionTechMapsIncluded() {
        TechMapSetup active = createAndTrack(
                fixture.createProductionTechMap(UserRole.ADMIN, owner1StorageId, 1.0, 1.0));
        fixture.seedComponentStock(owner1StorageId, UserRole.OWNER_1, Map.of(
                active.getInput1().getId(), 5.0,
                active.getInput2().getId(), 5.0));

        TechMapSetup toDeactivate = createAndTrack(
                fixture.createProductionTechMap(UserRole.ADMIN, owner1StorageId, 1.0, 1.0));
        fixture.seedComponentStock(owner1StorageId, UserRole.OWNER_1, Map.of(
                toDeactivate.getInput1().getId(), 5.0,
                toDeactivate.getInput2().getId(), 5.0));
        fixture.cleanupTechMap(UserRole.ADMIN, toDeactivate.getTechMap().getId(), owner1StorageId);
        techMapsToCleanup.remove(toDeactivate.getTechMap().getId());

        TechnologicalMapResponse disassemble = fixture.createDisassembleTechMap(UserRole.ADMIN, owner1StorageId);
        trackTechMap(disassemble);
        trackResource(disassemble.getInput().getFirst().getResource().getId());
        trackResource(disassemble.getOutput().getFirst().getResource().getId());
        fixture.seedComponentStock(owner1StorageId, UserRole.OWNER_1, Map.of(
                disassemble.getInput().getFirst().getResource().getId(), 10.0));

        List<AssemblyReadinessResponse> rows = fixture.getReadiness(UserRole.OWNER_1, owner1StorageId);

        assertThat(fixture.findByTechMapId(rows, active.getTechMap().getId())).isPresent();
        assertThat(fixture.findByTechMapId(rows, toDeactivate.getTechMap().getId())).isEmpty();
        assertThat(fixture.findByTechMapId(rows, disassemble.getId())).isEmpty();
    }

    @Test(priority = 30)
    @TestCaseId("TC-AR-API-003")
    @Severity(SeverityLevel.NORMAL)
    @Description("Техкарта з нульовим stock на всіх компонентах виключається (withNonZeroStocks).")
    public void testExcludedWhenAllComponentStocksZero() {
        TechMapSetup setup = createAndTrack(
                fixture.createProductionTechMap(UserRole.ADMIN, owner1StorageId, 1.0, 1.0));

        List<AssemblyReadinessResponse> rows = fixture.getReadiness(UserRole.OWNER_1, owner1StorageId);
        assertThat(fixture.findByTechMapId(rows, setup.getTechMap().getId())).isEmpty();
    }

    @Test(priority = 40)
    @TestCaseId("TC-AR-API-004")
    @Severity(SeverityLevel.NORMAL)
    @Description("Частковий stock: рядок присутній (хоч один компонент > 0), readyQty = 0.")
    public void testPartialStockRowPresentWithZeroReadyQty() {
        TechMapSetup setup = createAndTrack(
                fixture.createProductionTechMap(UserRole.ADMIN, owner1StorageId, 1.0, 1.0));
        fixture.seedComponentStock(owner1StorageId, UserRole.OWNER_1, Map.of(
                setup.getInput1().getId(), 5.0));

        List<AssemblyReadinessResponse> rows = fixture.getReadiness(UserRole.OWNER_1, owner1StorageId);
        AssemblyReadinessResponse row = fixture.findByTechMapId(rows, setup.getTechMap().getId()).orElseThrow();

        assertThat(fixture.findComponent(row, setup.getInput2().getId()).orElseThrow().getAvailableStock())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(computeReadyQty(row)).isZero();
    }

    @Test(priority = 50)
    @TestCaseId("TC-AR-API-005")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Bottleneck math: compA 10/2=5, compB 7/3=2 → readyQty=2.")
    public void testBottleneckMath() {
        TechMapSetup setup = createAndTrack(
                fixture.createProductionTechMap(UserRole.ADMIN, owner1StorageId, 2.0, 3.0));
        fixture.seedComponentStock(owner1StorageId, UserRole.OWNER_1, Map.of(
                setup.getInput1().getId(), 10.0,
                setup.getInput2().getId(), 7.0));

        AssemblyReadinessResponse row = fixture.findByTechMapId(
                fixture.getReadiness(UserRole.OWNER_1, owner1StorageId),
                setup.getTechMap().getId()).orElseThrow();

        assertThat(computeReadyQty(row)).isEqualTo(2);
    }

    @Test(priority = 60)
    @TestCaseId("TC-AR-API-006")
    @Severity(SeverityLevel.NORMAL)
    @Description("Дробові залишки: stock=9, required=4 → floor=2.")
    public void testFractionalStockFloorsCorrectly() {
        TechMapSetup setup = createAndTrack(
                fixture.createProductionTechMap(UserRole.ADMIN, owner1StorageId, 4.0, 100.0));
        fixture.seedComponentStock(owner1StorageId, UserRole.OWNER_1, Map.of(
                setup.getInput1().getId(), 9.0,
                setup.getInput2().getId(), 1.0));

        AssemblyReadinessResponse row = fixture.findByTechMapId(
                fixture.getReadiness(UserRole.OWNER_1, owner1StorageId),
                setup.getTechMap().getId()).orElseThrow();

        AssemblyComponentResponse limiting = fixture.findComponent(row, setup.getInput1().getId()).orElseThrow();
        assertThat(possibleUnits(limiting.getAvailableStock(), limiting.getRequiredPerUnit())).isEqualTo(2);
        assertThat(computeReadyQty(row)).isZero();
    }

    @Test(priority = 70)
    @TestCaseId("TC-AR-API-007")
    @Severity(SeverityLevel.NORMAL)
    @Description("Після деактивації техкарти рядок зникає з відповіді.")
    public void testRowRemovedAfterTechMapDeactivation() {
        TechMapSetup setup = createAndTrack(
                fixture.createProductionTechMap(UserRole.ADMIN, owner1StorageId, 1.0, 1.0));
        fixture.seedComponentStock(owner1StorageId, UserRole.OWNER_1, Map.of(
                setup.getInput1().getId(), 3.0,
                setup.getInput2().getId(), 3.0));

        assertThat(fixture.findByTechMapId(
                fixture.getReadiness(UserRole.OWNER_1, owner1StorageId),
                setup.getTechMap().getId())).isPresent();

        fixture.cleanupTechMap(UserRole.ADMIN, setup.getTechMap().getId(), owner1StorageId);
        techMapsToCleanup.remove(setup.getTechMap().getId());

        assertThat(fixture.findByTechMapId(
                fixture.getReadiness(UserRole.OWNER_1, owner1StorageId),
                setup.getTechMap().getId())).isEmpty();
    }

    @Test(priority = 80)
    @TestCaseId("TC-AR-API-008")
    @Severity(SeverityLevel.NORMAL)
    @Description("Scope по storageId: техкарта OWNER_1 не з'являється у відповіді для OWNER_2 storage.")
    public void testScopedByStorageId() {
        TechMapSetup setup = createAndTrack(
                fixture.createProductionTechMap(UserRole.ADMIN, owner1StorageId, 1.0, 1.0));
        fixture.seedComponentStock(owner1StorageId, UserRole.OWNER_1, Map.of(
                setup.getInput1().getId(), 5.0,
                setup.getInput2().getId(), 5.0));

        Optional<AssemblyReadinessResponse> onOwner2 = fixture.findByTechMapId(
                fixture.getReadiness(UserRole.ADMIN, owner2StorageId),
                setup.getTechMap().getId());

        assertThat(onOwner2).isEmpty();
    }

    @Test(priority = 90)
    @TestCaseId("TC-AR-API-009")
    @Severity(SeverityLevel.NORMAL)
    @Description("Додатковий stock через relocation → availableStock і readyQty зростають.")
    public void testStockChangeUpdatesReadiness() {
        TechMapSetup setup = createAndTrack(
                fixture.createProductionTechMap(UserRole.ADMIN, owner1StorageId, 2.0, 1.0));
        fixture.seedComponentStock(owner1StorageId, UserRole.OWNER_1, Map.of(
                setup.getInput1().getId(), 4.0,
                setup.getInput2().getId(), 1.0));

        AssemblyReadinessResponse before = fixture.findByTechMapId(
                fixture.getReadiness(UserRole.OWNER_1, owner1StorageId),
                setup.getTechMap().getId()).orElseThrow();
        int readyBefore = computeReadyQty(before);

        fixture.seedComponentStock(owner1StorageId, UserRole.OWNER_1, Map.of(
                setup.getInput2().getId(), 10.0));

        AssemblyReadinessResponse after = fixture.findByTechMapId(
                fixture.getReadiness(UserRole.OWNER_1, owner1StorageId),
                setup.getTechMap().getId()).orElseThrow();
        int readyAfter = computeReadyQty(after);

        assertThat(readyAfter).isGreaterThan(readyBefore);
        assertThat(fixture.findComponent(after, setup.getInput2().getId()).orElseThrow().getAvailableStock())
                .isGreaterThan(fixture.findComponent(before, setup.getInput2().getId()).orElseThrow().getAvailableStock());
    }

    @Test(priority = 100)
    @TestCaseId("TC-AR-API-010")
    @Severity(SeverityLevel.NORMAL)
    @Description("Спільний компонент у двох техкартах — однаковий availableStock у обох рядках.")
    public void testSharedComponentSameStockAcrossTechMaps() {
        TechMapSetup first = createAndTrack(
                fixture.createProductionTechMap(UserRole.ADMIN, owner1StorageId, 1.0, 1.0));
        ResourceResponse sharedInput = first.getInput1();
        ResourceResponse secondOnlyInput = resourceFixture.createUniqueResource("AR-SHARED-IN2-" + System.nanoTime());
        resourcesToCleanup.add(secondOnlyInput.getId());
        ResourceResponse secondProduct = resourceFixture.createUniqueResource("AR-SHARED-OUT-" + System.nanoTime());
        resourcesToCleanup.add(secondProduct.getId());

        TechnologicalMapResponse second = fixture.createSecondTechMapWithSharedInput(
                UserRole.ADMIN, owner1StorageId, sharedInput, secondOnlyInput, secondProduct);
        trackTechMap(second);
        trackResource(secondProduct.getId());

        fixture.seedComponentStock(owner1StorageId, UserRole.OWNER_1, Map.of(
                sharedInput.getId(), 8.0,
                first.getInput2().getId(), 4.0,
                secondOnlyInput.getId(), 3.0));

        List<AssemblyReadinessResponse> rows = fixture.getReadiness(UserRole.OWNER_1, owner1StorageId);
        AssemblyReadinessResponse row1 = fixture.findByTechMapId(rows, first.getTechMap().getId()).orElseThrow();
        AssemblyReadinessResponse row2 = fixture.findByTechMapId(rows, second.getId()).orElseThrow();

        BigDecimal stock1 = fixture.findComponent(row1, sharedInput.getId()).orElseThrow().getAvailableStock();
        BigDecimal stock2 = fixture.findComponent(row2, sharedInput.getId()).orElseThrow().getAvailableStock();
        assertThat(stock1).isEqualByComparingTo(stock2);
        assertThat(stock1).isGreaterThanOrEqualTo(BigDecimal.valueOf(8.0));
    }

    private TechMapSetup createAndTrack(TechMapSetup setup) {
        trackTechMap(setup.getTechMap());
        trackResource(setup.getProduct().getId());
        trackResource(setup.getInput1().getId());
        trackResource(setup.getInput2().getId());
        return setup;
    }

    private void trackTechMap(TechnologicalMapResponse techMap) {
        techMapsToCleanup.add(techMap.getId());
    }

    private void trackResource(Long resourceId) {
        resourcesToCleanup.add(resourceId);
    }
}
