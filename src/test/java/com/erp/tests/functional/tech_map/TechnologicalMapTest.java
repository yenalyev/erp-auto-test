package com.erp.tests.functional.tech_map;



import com.erp.annotations.TestCaseId;

import com.erp.api.endpoints.ApiEndpointDefinition;

import com.erp.enums.StorageTechnologicalMapMode;

import com.erp.enums.UserRole;

import com.erp.fixtures.TechnologicalMapFixture;

import com.erp.models.request.TechnologicalMapRequest;

import com.erp.models.response.StorageTechnologicalMapModeResponse;

import com.erp.models.response.TechnologicalMapResponse;

import com.erp.tests.functional.BaseFunctionalTest;

import com.erp.utils.helpers.AllureHelper;

import com.erp.validators.SchemaRegistry;

import io.qameta.allure.*;

import io.restassured.response.Response;

import lombok.extern.slf4j.Slf4j;

import org.testng.annotations.AfterClass;

import org.testng.annotations.BeforeClass;

import org.testng.annotations.Test;



import static org.assertj.core.api.Assertions.assertThat;



@Slf4j

@Epic("Technological Maps")

@Feature("Edit mode access")

public class TechnologicalMapTest extends BaseFunctionalTest {



    private TechnologicalMapFixture techMapFixture;

    private Long storageId;



    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")

    @Step("Підготовка середовища для тестів техкарт")

    public void setupTechnologicalMapTest() {

        techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);

        techMapFixture.prepareContext();

        storageId = techMapFixture.getOwner1StorageId();

        SchemaRegistry.logSchemaCoverage();

    }



    @AfterClass(alwaysRun = true)

    @Step("Відновити READ_ONLY для локації Owner1")

    public void restoreReadOnlyMode() {

        if (techMapFixture != null && storageId != null) {

            techMapFixture.setMode(storageId, StorageTechnologicalMapMode.READ_ONLY);

        }

    }



    @Test(priority = 10)

    @TestCaseId("TC-MFG-018")

    @Story("Create tech map — edit allowed")

    @Description("ADMIN відкриває редагування техкарт для локації Owner1, Owner1 успішно створює техкарту")

    @Severity(SeverityLevel.CRITICAL)

    public void testOwner1CreatesTechMapWhenEditAllowed() {

        Allure.step("ADMIN: відкрити доступ до редагування (EDIT_ALLOWED)", () -> {

            StorageTechnologicalMapModeResponse modeResponse = techMapFixture.setMode(

                    storageId, StorageTechnologicalMapMode.EDIT_ALLOWED);

            assertThat(modeResponse.getMode()).isEqualTo(StorageTechnologicalMapMode.EDIT_ALLOWED);

            techMapFixture.assertMode(storageId, UserRole.ADMIN, StorageTechnologicalMapMode.EDIT_ALLOWED);

        });

        TechnologicalMapRequest request = techMapFixture.buildOwner1CreateRequest();

        Response response = Allure.step("OWNER_1: POST create technological map", () ->

                apiExecutor.execute(ApiEndpointDefinition.TECH_MAP_CREATE, UserRole.OWNER_1, request));


        Allure.step("Validate status and schema", () -> {

            assertThat(response.statusCode()).isEqualTo(200);

            AllureHelper.attachSchemaValidationInfo(ApiEndpointDefinition.TECH_MAP_CREATE, response);

            SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.TECH_MAP_CREATE);

        });



        TechnologicalMapResponse created = response.as(TechnologicalMapResponse.class);

        Allure.step("Assert created tech map fields", () -> {

            assertThat(created.getId()).isNotNull();

            assertThat(created.getName()).isEqualTo(request.getName());

            assertThat(created.getType()).isEqualTo(request.getType());

            assertThat(created.getVersion()).isNotNull().isGreaterThanOrEqualTo(1L);

            assertThat(created.getGroupId()).isNotBlank();

            assertThat(created.getDateTime()).isNotNull();

            assertThat(created.getInput()).isNotNull().hasSize(2);

            assertThat(created.getOutput()).isNotNull().hasSize(1);

            assertThat(created.getStorages())

                    .isNotNull()

                    .anyMatch(s -> storageId.equals(s.getId()));

        });



        Allure.step("Verify tech map appears in storage-scoped list", () -> {

            assertThat(techMapFixture.getTechMapsByName(storageId, UserRole.OWNER_1, created.getName()))

                    .anyMatch(m -> created.getId().equals(m.getId()));

        });

    }



    @Test(priority = 20)

    @TestCaseId("TC-MFG-019")

    @Story("Create tech map — edit forbidden")

    @Description("ADMIN закриває редагування техкарт для локації Owner1, Owner1 не може створити техкарту")

    @Severity(SeverityLevel.CRITICAL)

    public void testOwner1CannotCreateTechMapWhenReadOnly() {

        Allure.step("ADMIN: закрити доступ до редагування (READ_ONLY)", () -> {

            StorageTechnologicalMapModeResponse modeResponse = techMapFixture.setMode(

                    storageId, StorageTechnologicalMapMode.READ_ONLY);

            assertThat(modeResponse.getMode()).isEqualTo(StorageTechnologicalMapMode.READ_ONLY);

            techMapFixture.assertMode(storageId, UserRole.ADMIN, StorageTechnologicalMapMode.READ_ONLY);

        });



        TechnologicalMapRequest request = techMapFixture.buildOwner1CreateRequest();

        String uniqueName = request.getName();



        long countBefore = techMapFixture.countTechMapsByName(storageId, UserRole.ADMIN, uniqueName);



        Response response = Allure.step("OWNER_1: POST create technological map (expected failure)", () ->

                apiExecutor.execute(ApiEndpointDefinition.TECH_MAP_CREATE, UserRole.OWNER_1, request));



        Allure.step("Validate rejection", () -> {

            assertThat(response.statusCode()).isEqualTo(400);

        });



        long countAfter = techMapFixture.countTechMapsByName(storageId, UserRole.ADMIN, uniqueName);

        assertThat(countAfter).isEqualTo(countBefore);

    }

}


