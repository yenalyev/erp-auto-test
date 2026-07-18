package com.erp.tests.functional.resource;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API contract for Plan Execution «Керувати обраними ресурсами» catalog:
 * {@code GET /api/v1/resources/with-technological-map}.
 *
 * <p>Requirement: list = output resources of PRODUCTION tech maps on the given storage;
 * Active / Archived selector maps to {@code isActive=true|false}.
 */
@Slf4j
@Issue("CPMA-587")
@Epic("Plans")
@Feature("Plan Execution favourites catalog")
@Story("GET /resources/with-technological-map")
public class ResourceWithTechnologicalMapApiTest extends BaseFunctionalTest {

    private ResourceFixture resourceFixture;
    private TechnologicalMapFixture techMapFixture;
    private Long storageId;

    private TechnologicalMapFixture.IsolatedTechMapContext activeContext;
    private TechnologicalMapFixture.IsolatedTechMapContext archivedContext;
    private TechnologicalMapResponse archivedOutputReboundTechMap;
    private final List<TechnologicalMapResponse> techMapsToCleanup = new ArrayList<>();
    private final List<Long> resourcesToCleanup = new ArrayList<>();

    @BeforeClass(alwaysRun = true)
    public void setupCatalogFixtures() {
        if (testContext == null) {
            baseTestClassSetup();
        }
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        techMapFixture.prepareContext();
        storageId = ConfigProvider.getOwner1StorageId();

        activeContext = techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, storageId);
        techMapsToCleanup.add(activeContext.getTechMap());
        trackResources(activeContext);

        archivedContext = techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, storageId);
        techMapsToCleanup.add(archivedContext.getTechMap());
        trackResources(archivedContext);
        archivedOutputReboundTechMap = bindArchivedOutputToNewActiveTechMap(archivedContext);
        techMapsToCleanup.add(archivedOutputReboundTechMap);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupCatalogFixtures() {
        for (TechnologicalMapResponse techMap : techMapsToCleanup) {
            try {
                techMapFixture.deactivateTechMap(UserRole.ADMIN, techMap.getId(), storageId);
            } catch (RuntimeException e) {
                log.warn("Cleanup tech map {} failed: {}", techMap.getId(), e.getMessage());
            }
        }
        for (Long resourceId : resourcesToCleanup) {
            try {
                resourceFixture.deactivate(UserRole.ADMIN, resourceId);
            } catch (RuntimeException e) {
                log.warn("Cleanup resource {} failed: {}", resourceId, e.getMessage());
            }
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-RES-WTMAP-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Вимога (модалка «Керувати обраними»): перелік виробів = output-ресурси PRODUCTION
            техкарт обраного стору; селектор «Активні» → isActive=true.

            Arrange: на OWNER_1 storage створити дві ізольовані PRODUCTION техкарти; output
            другої архівувати (DELETE /resources/{id}) після деактивації першої техкарти,
            потім прив'язати архівний output до нової активної PRODUCTION техкарти стору.
            Act: GET /resources/with-technological-map?storageId=&isActive=true&name=<output>.
            Assert: активний output присутній; архівний output і input-ресурси відсутні.""")
    public void testActiveSelectorReturnsActiveProductionOutputsOnly() {
        String activeName = activeContext.getProduct().getName();
        String archivedName = archivedContext.getProduct().getName();
        Long inputId = activeContext.getTechMap().getInput().getFirst().getResource().getId();

        List<ResourceResponse> activeCatalog = resourceFixture.getWithTechnologicalMap(
                UserRole.OWNER_1, storageId, true, activeName);

        assertThat(activeCatalog)
                .as("Активний output техкарти виробництва має бути в каталозі (селектор «Активні»)")
                .extracting(ResourceResponse::getId)
                .contains(activeContext.getProduct().getId());

        List<ResourceResponse> archivedInActiveFilter = resourceFixture.getWithTechnologicalMap(
                UserRole.OWNER_1, storageId, true, archivedName);
        assertThat(archivedInActiveFilter)
                .as("Архівний output не має з'являтись при isActive=true")
                .extracting(ResourceResponse::getId)
                .doesNotContain(archivedContext.getProduct().getId());

        List<ResourceResponse> inputLookup = resourceFixture.getWithTechnologicalMap(
                UserRole.OWNER_1, storageId, true,
                activeContext.getTechMap().getInput().getFirst().getResource().getName());
        assertThat(inputLookup)
                .as("Input техкарти не є виробом (output) — не має бути в каталозі")
                .extracting(ResourceResponse::getId)
                .doesNotContain(inputId);
    }

    @Test(priority = 20)
    @TestCaseId("TC-RES-WTMAP-002")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Вимога: селектор «Архівні» у модалці → isActive=false — лише деактивовані output
            вироби PRODUCTION техкарт стору.

            Act: GET .../with-technological-map?storageId=&isActive=false&name=<archived-output>.
            Assert: архівний output присутній; активний output відсутній.

            Arrange для архівного: деактивувати першу техкарту → архівувати ресурс → нова
            активна PRODUCTION техкарта з тим самим output (допустимий стан словника).""")
    public void testArchivedSelectorReturnsInactiveProductionOutputsOnly() {
        String activeName = activeContext.getProduct().getName();
        String archivedName = archivedContext.getProduct().getName();

        List<ResourceResponse> archivedCatalog = resourceFixture.getWithTechnologicalMap(
                UserRole.OWNER_1, storageId, false, archivedName);

        assertThat(archivedCatalog)
                .as("Архівний output техкарти має бути в каталозі (селектор «Архівні»)")
                .extracting(ResourceResponse::getId)
                .contains(archivedContext.getProduct().getId());

        List<ResourceResponse> activeInArchivedFilter = resourceFixture.getWithTechnologicalMap(
                UserRole.OWNER_1, storageId, false, activeName);
        assertThat(activeInArchivedFilter)
                .as("Активний output не має з'являтись при isActive=false")
                .extracting(ResourceResponse::getId)
                .doesNotContain(activeContext.getProduct().getId());
    }

    private void trackResources(TechnologicalMapFixture.IsolatedTechMapContext ctx) {
        resourcesToCleanup.add(ctx.getProduct().getId());
        ctx.getTechMap().getInput().forEach(usage ->
                resourcesToCleanup.add(usage.getResource().getId()));
    }

    /**
     * Archives a tech-map output and binds it to a new active PRODUCTION map on the same storage.
     * Backend forbids {@code DELETE /resources/{id}} while the resource is still referenced by an
     * <em>active</em> tech map ({@code ResourceValidator.validateDeactivate}), so we deactivate the
     * first map first, archive the output, then create a rebound map — the state the «Архівні»
     * selector in the favourites modal is meant to surface.
     */
    private TechnologicalMapResponse bindArchivedOutputToNewActiveTechMap(
            TechnologicalMapFixture.IsolatedTechMapContext ctx) {
        Response deactivateMap = techMapFixture.deactivateTechMap(
                UserRole.ADMIN, ctx.getTechMap().getId(), storageId);
        assertThat(deactivateMap.statusCode())
                .as("Deactivate first tech map before archiving its output")
                .isBetween(200, 299);

        Response deactivateResource = resourceFixture.deactivate(UserRole.ADMIN, ctx.getProduct().getId());
        assertThat(deactivateResource.statusCode())
                .as("Archive output resource in dictionary")
                .isBetween(200, 299);
        assertThat(resourceFixture.getById(UserRole.ADMIN, ctx.getProduct().getId()).getActive())
                .as("Archived output must have active=false in dictionary")
                .isFalse();

        String suffix = String.valueOf(System.currentTimeMillis());
        ResourceResponse in1 = resourceFixture.createUniqueResource("TM-ARCH-IN1-" + suffix);
        ResourceResponse in2 = resourceFixture.createUniqueResource("TM-ARCH-IN2-" + suffix);
        resourcesToCleanup.add(in1.getId());
        resourcesToCleanup.add(in2.getId());

        TechnologicalMapRequest reboundRequest = TechnologicalMapDataFactory.createProductionMapWithStorages(
                "TM-ArchivedOutput-" + suffix,
                List.of(
                        new ResourceUsageRequest(in1.getId(), 2.0),
                        new ResourceUsageRequest(in2.getId(), 1.0)),
                List.of(new ResourceUsageRequest(ctx.getProduct().getId(), 1.0)),
                Set.of(storageId)).build();

        return techMapFixture.createTechMapWithRequest(UserRole.ADMIN, reboundRequest);
    }
}
