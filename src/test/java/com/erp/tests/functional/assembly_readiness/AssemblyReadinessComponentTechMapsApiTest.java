package com.erp.tests.functional.assembly_readiness;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.AssemblyReadinessFixture;
import com.erp.fixtures.AssemblyReadinessFixture.TechMapSetup;
import com.erp.models.response.AssemblyComponentResponse;
import com.erp.models.response.AssemblyComponentTechMapResponse;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API coverage for nested {@code components[].technologicalMaps} on
 * {@code GET /api/v1/assembly-readiness/{storageId}} (CPMA-633):
 * active PRODUCTION maps that have the component resource in OUTPUT.
 */
@Slf4j
@Epic("Production")
@Feature("Assembly Readiness — component tech map links")
@Story("components[].technologicalMaps")
public class AssemblyReadinessComponentTechMapsApiTest extends BaseFunctionalTest {

    private AssemblyReadinessFixture fixture;
    private Long owner1StorageId;
    private Long owner2StorageId;

    private final List<CleanupTechMap> techMapsToCleanup = new ArrayList<>();
    private final List<Long> resourcesToCleanup = new ArrayList<>();

    private record CleanupTechMap(Long techMapId, Long storageId) {
    }

    @BeforeClass(alwaysRun = true)
    public void setupFixtures() {
        if (testContext == null) {
            baseTestClassSetup();
        }
        fixture = new AssemblyReadinessFixture(testContext, apiExecutor);
        fixture.prepareContext();
        owner1StorageId = ConfigProvider.getOwner1StorageId();
        owner2StorageId = ConfigProvider.getOwner2StorageId();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupMethodArtifacts() {
        for (CleanupTechMap entry : techMapsToCleanup) {
            fixture.cleanupTechMap(UserRole.ADMIN, entry.techMapId(), entry.storageId());
        }
        techMapsToCleanup.clear();
        for (Long resourceId : resourcesToCleanup) {
            fixture.cleanupResource(UserRole.ADMIN, resourceId);
        }
        resourcesToCleanup.clear();
    }

    @Test(priority = 10)
    @TestCaseId("TC-AR-TM-API-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Компонент з активною PRODUCTION техкартою (ресурс у OUTPUT) →
            components[].technologicalMaps містить id, name, storages цієї карти.""")
    public void testActiveProductionProducerMapLinkedOnComponent() {
        TechMapSetup assembly = arrangeAssemblyWithStock();
        TechnologicalMapResponse producer = trackTechMap(
                fixture.createProducerTechMap(UserRole.ADMIN, owner1StorageId, assembly.getInput1()),
                owner1StorageId);

        AssemblyComponentResponse component = componentOf(assembly, assembly.getInput1().getId());

        assertThat(component.getTechnologicalMaps())
                .as("technologicalMaps must include the producer map")
                .isNotEmpty();
        AssemblyComponentTechMapResponse linked = fixture.findComponentTechMap(component, producer.getId())
                .orElseThrow(() -> new AssertionError("Producer map missing in technologicalMaps"));

        assertThat(linked.getName()).isEqualTo(producer.getName());
        assertThat(linked.getStorages())
                .extracting(s -> s.getId())
                .contains(owner1StorageId);
    }

    @Test(priority = 20)
    @TestCaseId("TC-AR-TM-API-002")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Немає активної техкарти з компонентом у OUTPUT → technologicalMaps порожній.")
    public void testNoProducerMapsWhenComponentHasNone() {
        TechMapSetup assembly = arrangeAssemblyWithStock();

        AssemblyComponentResponse component = componentOf(assembly, assembly.getInput1().getId());

        assertThat(component.getTechnologicalMaps() == null ? List.of() : component.getTechnologicalMaps())
                .filteredOn(m -> !m.getId().equals(assembly.getTechMap().getId()))
                .as("No external producer maps expected for fresh component resource")
                .isEmpty();
        assertThat(fixture.componentHasTechMapLink(component, assembly.getTechMap().getId()))
                .as("Assembly map itself outputs the product, not the component — must not self-link")
                .isFalse();
    }

    @Test(priority = 30)
    @TestCaseId("TC-AR-TM-API-003")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Деактивована техкарта-виробник не потрапляє в technologicalMaps (лише active).")
    public void testInactiveProducerMapExcluded() {
        TechMapSetup assembly = arrangeAssemblyWithStock();
        TechnologicalMapResponse producer = trackTechMap(
                fixture.createProducerTechMap(UserRole.ADMIN, owner1StorageId, assembly.getInput1()),
                owner1StorageId);

        assertThat(fixture.componentHasTechMapLink(
                componentOf(assembly, assembly.getInput1().getId()), producer.getId())).isTrue();

        fixture.cleanupTechMap(UserRole.ADMIN, producer.getId(), owner1StorageId);
        techMapsToCleanup.removeIf(e -> producer.getId().equals(e.techMapId()));

        assertThat(fixture.componentHasTechMapLink(
                componentOf(assembly, assembly.getInput1().getId()), producer.getId()))
                .as("Inactive producer must disappear from technologicalMaps")
                .isFalse();
    }

    @Test(priority = 40)
    @TestCaseId("TC-AR-TM-API-004")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            DISASSEMBLE техкарта з компонентом у OUTPUT не потрапляє в technologicalMaps
            (backend CPMA-633 фільтрує лише PRODUCTION).""")
    public void testDisassembleProducerMapExcluded() {
        TechMapSetup assembly = arrangeAssemblyWithStock();
        TechnologicalMapResponse disProducer = trackTechMap(
                fixture.createDisassembleProducerTechMap(UserRole.ADMIN, owner1StorageId, assembly.getInput1()),
                owner1StorageId);

        assertThat(fixture.componentHasTechMapLink(
                componentOf(assembly, assembly.getInput1().getId()), disProducer.getId()))
                .as("DISASSEMBLE producer maps are not returned by assembly-readiness")
                .isFalse();
    }

    @Test(priority = 50)
    @TestCaseId("TC-AR-TM-API-005")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Кілька активних PRODUCTION виробників одного компонента → усі в technologicalMaps.")
    public void testMultipleProducerMapsReturned() {
        TechMapSetup assembly = arrangeAssemblyWithStock();
        TechnologicalMapResponse producer1 = trackTechMap(
                fixture.createProducerTechMap(UserRole.ADMIN, owner1StorageId, assembly.getInput1()),
                owner1StorageId);
        TechnologicalMapResponse producer2 = trackTechMap(
                fixture.createProducerTechMap(UserRole.ADMIN, owner1StorageId, assembly.getInput1()),
                owner1StorageId);

        AssemblyComponentResponse component = componentOf(assembly, assembly.getInput1().getId());

        assertThat(component.getTechnologicalMaps())
                .extracting(AssemblyComponentTechMapResponse::getId)
                .contains(producer1.getId(), producer2.getId());
        assertThat(component.getTechnologicalMaps())
                .extracting(AssemblyComponentTechMapResponse::getName)
                .contains(producer1.getName(), producer2.getName());
    }

    @Test(priority = 60)
    @TestCaseId("TC-AR-TM-API-006")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            API повертає техкарти всіх стореджів (з полем storages).
            ADMIN і OWNER_1 бачать однаковий список — фільтрація за роллю на UI.""")
    public void testApiReturnsProducerMapsFromAllStorages() {
        TechMapSetup assembly = arrangeAssemblyWithStock();
        TechnologicalMapResponse onOwner1 = trackTechMap(
                fixture.createProducerTechMap(UserRole.ADMIN, owner1StorageId, assembly.getInput1()),
                owner1StorageId);
        TechnologicalMapResponse onOwner2 = trackTechMap(
                fixture.createProducerTechMap(UserRole.ADMIN, owner2StorageId, assembly.getInput1()),
                owner2StorageId);

        AssemblyComponentResponse asOwner = componentOf(assembly, assembly.getInput1().getId(), UserRole.OWNER_1);
        AssemblyComponentResponse asAdmin = componentOf(assembly, assembly.getInput1().getId(), UserRole.ADMIN);

        assertThat(fixture.componentHasTechMapLink(asOwner, onOwner1.getId())).isTrue();
        assertThat(fixture.componentHasTechMapLink(asOwner, onOwner2.getId()))
                .as("API includes foreign-storage producer; UI hides it for owner")
                .isTrue();
        assertThat(fixture.componentHasTechMapLink(asAdmin, onOwner2.getId())).isTrue();

        AssemblyComponentTechMapResponse foreign = fixture.findComponentTechMap(asOwner, onOwner2.getId())
                .orElseThrow();
        assertThat(foreign.getStorages()).extracting(s -> s.getId()).contains(owner2StorageId);
    }

    @Test(priority = 70)
    @TestCaseId("TC-AR-TM-API-007")
    @Severity(SeverityLevel.NORMAL)
    @Description("Лінк прив'язаний до компонента за OUTPUT: виробник input1 не з'являється на input2.")
    public void testProducerLinkedOnlyToMatchingOutputComponent() {
        TechMapSetup assembly = arrangeAssemblyWithStock();
        TechnologicalMapResponse producerForInput1 = trackTechMap(
                fixture.createProducerTechMap(UserRole.ADMIN, owner1StorageId, assembly.getInput1()),
                owner1StorageId);

        AssemblyComponentResponse input1 = componentOf(assembly, assembly.getInput1().getId());
        AssemblyComponentResponse input2 = componentOf(assembly, assembly.getInput2().getId());

        assertThat(fixture.componentHasTechMapLink(input1, producerForInput1.getId())).isTrue();
        assertThat(fixture.componentHasTechMapLink(input2, producerForInput1.getId())).isFalse();
    }

    private TechMapSetup arrangeAssemblyWithStock() {
        TechMapSetup setup = createAndTrack(
                fixture.createProductionTechMap(UserRole.ADMIN, owner1StorageId, 1.0, 1.0));
        fixture.seedComponentStock(owner1StorageId, UserRole.OWNER_1, Map.of(
                setup.getInput1().getId(), 5.0,
                setup.getInput2().getId(), 5.0));
        return setup;
    }

    private AssemblyComponentResponse componentOf(TechMapSetup assembly, Long resourceId) {
        return componentOf(assembly, resourceId, UserRole.OWNER_1);
    }

    private AssemblyComponentResponse componentOf(TechMapSetup assembly, Long resourceId, UserRole role) {
        AssemblyReadinessResponse row = fixture.findByTechMapId(
                fixture.getReadiness(role, owner1StorageId),
                assembly.getTechMap().getId()).orElseThrow();
        return fixture.findComponent(row, resourceId).orElseThrow();
    }

    private TechMapSetup createAndTrack(TechMapSetup setup) {
        trackTechMap(setup.getTechMap(), owner1StorageId);
        trackResource(setup.getProduct().getId());
        trackResource(setup.getInput1().getId());
        trackResource(setup.getInput2().getId());
        return setup;
    }

    private TechnologicalMapResponse trackTechMap(TechnologicalMapResponse techMap, Long storageId) {
        techMapsToCleanup.add(new CleanupTechMap(techMap.getId(), storageId));
        return techMap;
    }

    private void trackResource(Long resourceId) {
        resourcesToCleanup.add(resourceId);
    }
}
