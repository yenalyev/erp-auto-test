package com.erp.fixtures;



import com.erp.api.clients.ApiExecutor;

import com.erp.api.endpoints.ApiEndpointDefinition;

import com.erp.data.factories.relocation.RelocationStockSeeder;

import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;

import com.erp.enums.UserRole;

import com.erp.models.request.TechnologicalMapRequest;

import com.erp.models.response.ResourceResponse;

import com.erp.models.response.TechnologicalMapResponse;

import com.erp.test_context.ContextKey;

import com.erp.test_context.TestContext;

import com.erp.utils.config.ConfigProvider;

import io.qameta.allure.Step;

import io.restassured.response.Response;

import lombok.extern.slf4j.Slf4j;



import java.util.List;

import java.util.Map;



@Slf4j

public class ProductionFixture extends BaseFixture {



    private static final double INPUT_STOCK = 100.0;



    private final ResourceFixture resourceFixture;



    public ProductionFixture(TestContext testContext, ApiExecutor apiExecutor) {

        super(testContext, apiExecutor);

        this.resourceFixture = new ResourceFixture(testContext, apiExecutor);

    }



    @Step("FIXTURE: Підготовка середовища для тестів виробництва")

    public void prepareContext() {

        if (testContext.get(ContextKey.PRODUCTION_TECH_MAP) != null) {

            return;

        }



        resourceFixture.prepareContext();

        List<ResourceResponse> resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);

        Long storageId = ConfigProvider.getOwner1StorageId();



        TechnologicalMapRequest techMapRequest = TechnologicalMapDataFactory

                .createProductionTechMap(resources, storageId)

                .build();



        Response techMapResponse = apiExecutor.execute(

                ApiEndpointDefinition.TECH_MAP_CREATE, UserRole.ADMIN, techMapRequest);

        validateSuccess(techMapResponse, "Create production tech map");



        TechnologicalMapResponse techMap;

        try {

            techMap = techMapResponse.as(TechnologicalMapResponse.class);

        } catch (Exception e) {

            throw new IllegalStateException(

                    "Failed to parse tech map response: " + techMapResponse.getBody().asString(), e);

        }

        testContext.set(ContextKey.PRODUCTION_TECH_MAP, techMap);

        testContext.set(ContextKey.DYNAMIC_TECH_MAP, techMap);

        testContext.set(ContextKey.DYNAMIC_TECH_MAP_ID, techMap.getId());



        Long input1 = resources.get(0).getId();

        Long input2 = resources.get(1).getId();

        Long output = resources.get(2).getId();

        testContext.set(ContextKey.PRODUCTION_INPUT_RESOURCE_IDS, List.of(input1, input2));

        testContext.set(ContextKey.PRODUCTION_OUTPUT_RESOURCE_ID, output);



        seedStockViaRelocation(storageId, input1, input2);

        log.info("Production fixture ready: techMap={}, storage={}, inputs=[{}, {}], outputResource={}",
                techMap.getId(), storageId, input1, input2, output);

    }



    @Step("FIXTURE: Seed input stock via relocation receive (SUPPLIER → storage {storageId})")

    private void seedStockViaRelocation(Long storageId, Long input1, Long input2) {

        RelocationStockSeeder.receiveFromSupplier(

                apiExecutor,

                UserRole.OWNER_1,

                storageId,

                Map.of(input1, INPUT_STOCK, input2, INPUT_STOCK));

        log.info("Seeded stock via relocation receive: storage={}, resources=[{}, {}], amount={}",

                storageId, input1, input2, INPUT_STOCK);

    }

}


