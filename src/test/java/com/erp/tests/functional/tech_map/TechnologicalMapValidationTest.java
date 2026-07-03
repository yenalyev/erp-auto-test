package com.erp.tests.functional.tech_map;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API validation: ресурс не може бути одночасно у input і output техкарти (TC-MFG-031…034).
 *
 * <p>Jira: CPMA-603
 */
@Slf4j
@Issue("CPMA-603")
@Epic("Technological Maps")
@Feature("Input/output validation")
public class TechnologicalMapValidationTest extends BaseFunctionalTest {

    private TechnologicalMapFixture techMapFixture;
    private Long storageId;
    private List<ResourceResponse> resources;
    private TechnologicalMapResponse techMapForCleanup;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    @Step("Підготовка середовища для тестів валідації техкарт")
    public void setupTechnologicalMapValidationTest() {
        techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        techMapFixture.prepareContext();
        storageId = techMapFixture.getOwner1StorageId();
        resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        SchemaRegistry.logSchemaCoverage();

        techMapFixture.setMode(storageId, StorageTechnologicalMapMode.EDIT_ALLOWED);
        techMapFixture.assertMode(storageId, UserRole.ADMIN, StorageTechnologicalMapMode.EDIT_ALLOWED);
    }

    @AfterClass(alwaysRun = true)
    @Step("Відновити READ_ONLY для локації Owner1")
    public void restoreReadOnlyMode() {
        if (techMapFixture != null && storageId != null) {
            techMapFixture.setMode(storageId, StorageTechnologicalMapMode.READ_ONLY);
        }
    }

    @AfterMethod(alwaysRun = true)
    @Step("Деактивувати техкарту після update-тесту")
    public void cleanupCreatedTechMap() {
        if (techMapForCleanup != null && techMapFixture != null && storageId != null) {
            techMapFixture.deactivateTechMap(UserRole.OWNER_1, techMapForCleanup.getId(), storageId);
            techMapForCleanup = null;
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-MFG-031")
    @Story("Create tech map — input/output overlap rejected")
    @Description("POST create PRODUCTION: input [A], output [A] — той самий resourceId → 400, техкарта не створюється")
    @Severity(SeverityLevel.CRITICAL)
    public void testCannotCreateProductionTechMapWithInputOutputOverlap() {
        ResourceResponse overlapping = resources.get(0);
        TechnologicalMapRequest request = TechnologicalMapDataFactory.withInputOutputOverlap(
                overlapping, null, storageId, TechnologicalMapDataFactory.TYPE_PRODUCTION);
        assertCreateRejectedWithOverlap(request, overlapping.getName());
    }

    @Test(priority = 11)
    @TestCaseId("TC-MFG-032")
    @Story("Create tech map — input/output overlap rejected")
    @Description("POST create DISASSEMBLE: input [A], output [A] — той самий resourceId → 400, техкарта не створюється")
    @Severity(SeverityLevel.CRITICAL)
    public void testCannotCreateDisassembleTechMapWithInputOutputOverlap() {
        ResourceResponse overlapping = resources.get(0);
        TechnologicalMapRequest request = TechnologicalMapDataFactory.withInputOutputOverlap(
                overlapping, null, storageId, TechnologicalMapDataFactory.TYPE_DISASSEMBLE);
        assertCreateRejectedWithOverlap(request, overlapping.getName());
    }

    @Test(priority = 12)
    @TestCaseId("TC-MFG-033")
    @Story("Create tech map — input/output overlap rejected")
    @Description("POST create PRODUCTION: input [C, B], output [B] — overlap на другому input-ресурсі → 400")
    @Severity(SeverityLevel.CRITICAL)
    public void testCannotCreateTechMapWithMultiInputOutputOverlap() {
        ResourceResponse overlapping = resources.get(1);
        ResourceResponse otherInput = resources.get(2);
        TechnologicalMapRequest request = TechnologicalMapDataFactory.withInputOutputOverlap(
                overlapping, otherInput, storageId, TechnologicalMapDataFactory.TYPE_PRODUCTION);
        assertCreateRejectedWithOverlap(request, overlapping.getName());
    }

    @Test(priority = 20)
    @TestCaseId("TC-MFG-034")
    @Story("Update tech map — input/output overlap rejected")
    @Description("PUT /technological-maps/{id}: валідна техкарта → output[0].resourceId = input[0].resourceId → 400, дані без змін")
    @Severity(SeverityLevel.CRITICAL)
    public void testCannotUpdateTechMapWithInputOutputOverlap() {
        TechnologicalMapResponse source = Allure.step("Arrange: створити валідну техкарту", () ->
                techMapFixture.createTechMapAs(UserRole.OWNER_1, storageId));
        techMapForCleanup = source;

        TechnologicalMapRequest updateRequest = Allure.step(
                "Mutate: output[0].resourceId = input[0].resourceId",
                () -> TechnologicalMapDataFactory.withIntroducedOverlap(source));

        String overlappingResourceName = source.getInput().getFirst().getResource().getName();

        Response response = Allure.step("OWNER_1: PUT update with input/output overlap", () ->
                apiExecutor.execute(
                        ApiEndpointDefinition.TECH_MAP_UPDATE_NAME,
                        UserRole.OWNER_1,
                        updateRequest,
                        String.valueOf(source.getId())));

        Allure.step("Validate rejection", () ->
                techMapFixture.assertInputOutputOverlapRejection(response, overlappingResourceName));

        Allure.step("Assert tech map unchanged via GET by name", () -> {
            List<TechnologicalMapResponse> found = techMapFixture.getTechMapsByName(
                    storageId, UserRole.ADMIN, source.getName());
            TechnologicalMapResponse current = found.stream()
                    .filter(m -> source.getId().equals(m.getId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Tech map not found: " + source.getId()));

            assertThat(current.getName()).isEqualTo(source.getName());
            assertThat(current.getInput()).hasSize(source.getInput().size());
            assertThat(current.getOutput()).hasSize(source.getOutput().size());
            assertThat(current.getInput().getFirst().getResource().getId())
                    .isEqualTo(source.getInput().getFirst().getResource().getId());
            assertThat(current.getOutput().getFirst().getResource().getId())
                    .isEqualTo(source.getOutput().getFirst().getResource().getId());
        });
    }

    private void assertCreateRejectedWithOverlap(TechnologicalMapRequest request, String overlappingResourceName) {
        long countBefore = techMapFixture.countTechMapsByName(storageId, UserRole.ADMIN, request.getName());

        Response response = Allure.step("OWNER_1: POST create with input/output overlap", () ->
                apiExecutor.execute(ApiEndpointDefinition.TECH_MAP_CREATE, UserRole.OWNER_1, request));

        Allure.step("Validate rejection", () ->
                techMapFixture.assertInputOutputOverlapRejection(response, overlappingResourceName));

        long countAfter = techMapFixture.countTechMapsByName(storageId, UserRole.ADMIN, request.getName());
        assertThat(countAfter)
                .as("Кількість техкарт з ім'ям %s не повинна зрости", request.getName())
                .isEqualTo(countBefore);
    }
}
