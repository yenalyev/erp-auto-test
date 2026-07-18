package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.enums.UserRole;
import com.erp.fixtures.EquipmentFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.models.response.*;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import io.qameta.allure.Step;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RbacFixture extends BaseFixture {

    public RbacFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    /**
     * Публічний API для тестів. Створює все необхідне одним викликом.
     */
    @Step("Setup complete ERP test data context")
    public void prepareFullRbacContext() {
        log.info("Starting ERP test data generation...");
        fetchSharedUnit(5);
        fetchSharedResourceCategory();
        setupSharedResource();
        setupSharedResourceList(4);
        prepareTechMapForUpdate();
        setupDynamicProductionList(3, UserRole.OWNER_1);
        setupDynamicBusinessUnit();
        setupDynamicPlan(2);
        prepareRelocationRbacContext();
        prepareCrewRbacContext();
        prepareDefectRbacContext();
        prepareGlobalPlanRbacContext();
        new NonSeriesProductionFixture(testContext, apiExecutor).prepareContext();
        prepareUserRbacContext();
    }

    @Step("Setup Keycloak user entity for RBAC matrix")
    public void prepareUserRbacContext() {
        if (testContext.get(ContextKey.SHARED_USER_ID) != null) {
            return;
        }
        new UserFixture(testContext, apiExecutor).prepareRbacUserContext();
    }

    @Step("Setup crew storage in CREWS region for RBAC matrix")
    public void prepareCrewRbacContext() {
        if (testContext.get(ContextKey.CREW_STORAGE_ID) != null) {
            return;
        }
        StorageFixture storageFixture = new StorageFixture(testContext, apiExecutor);
        StorageRegionFixture regionFixture = new StorageRegionFixture(testContext, apiExecutor);
        CrewRegionFixture crewFixture = new CrewRegionFixture(
                testContext, apiExecutor, storageFixture, regionFixture);
        storageFixture.prepareContext();
        crewFixture.prepareSingleCrewScenario("rbac-crew-");
    }

    @Step("Setup global plan entity for RBAC matrix")
    public void prepareGlobalPlanRbacContext() {
        if (testContext.get(ContextKey.GLOBAL_PLAN_ID) != null) {
            return;
        }
        GlobalPlanFixture globalPlanFixture = new GlobalPlanFixture(testContext, apiExecutor);
        globalPlanFixture.prepareDecompositionChain();
        globalPlanFixture.createGlobalPlan(10.0);
    }

    @Step("Setup defect entity for RBAC matrix")
    public void prepareDefectRbacContext() {
        if (testContext.get(ContextKey.DEFECT_ID) != null) {
            return;
        }
        Long resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);
        if (resourceId == null) {
            prepareRelocationRbacContext();
            resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);
        }
        testContext.set(ContextKey.DEFECT_RESOURCE_ID, resourceId);

        DefectFixture defectFixture = new DefectFixture(testContext, apiExecutor);
        defectFixture.seedDefectForRbac();
    }

    @Step("Setup relocation entities for RBAC matrix")
    public void prepareRelocationRbacContext() {
        if (testContext.get(ContextKey.RELOCATION_ID) == null) {
            RelocationFixture relocationFixture = new RelocationFixture(testContext, apiExecutor);
            relocationFixture.prepareContext();

            Long owner1 = com.erp.utils.config.ConfigProvider.getOwner1StorageId();
            Long owner2 = com.erp.utils.config.ConfigProvider.getOwner2StorageId();
            Long resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);
            Long unitId = testContext.get(ContextKey.RELOCATION_UNIT_STORAGE_ID);

            com.erp.models.response.RelocationResponse receive = relocationFixture.createExternalReceive(
                    UserRole.OWNER_1, owner1, resourceId, 5.0,
                    com.erp.data.factories.relocation.RelocationDataFactory.uniqueBatchNumber());
            testContext.set(ContextKey.RELOCATION_ID, receive.getId());

            com.erp.models.response.RelocationResponse created = relocationFixture.createSend(
                    UserRole.OWNER_1, owner1, owner2, resourceId, 2.0);
            testContext.set(ContextKey.RELOCATION_CREATED_ID, created.getId());

            com.erp.models.response.RelocationResponse unitSend = relocationFixture.createSend(
                    UserRole.OWNER_1, owner1, unitId, resourceId, 2.0);
            testContext.set(ContextKey.RELOCATION_AUTO_FINISHED_SEND_ID, unitSend.getId());

            EquipmentFixture equipmentFixture = new EquipmentFixture(testContext, apiExecutor);
            equipmentFixture.prepareContext();
        }

        IncidentFixture incidentFixture = new IncidentFixture(testContext, apiExecutor);
        incidentFixture.seedRbacIncidentContext();
    }
}