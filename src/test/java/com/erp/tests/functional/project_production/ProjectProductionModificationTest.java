package com.erp.tests.functional.project_production;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.project_production.ProjectProductionDataFactory;
import com.erp.enums.EquipmentStatus;
import com.erp.enums.ProjectProductionState;
import com.erp.enums.ProjectProductionType;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.EmployeeFixture;
import com.erp.fixtures.EquipmentFixture;
import com.erp.fixtures.ProjectProductionFixture;
import com.erp.models.request.EquipmentRelocationSendRequest;
import com.erp.models.request.EquipmentCategoryRequest;
import com.erp.models.request.ProjectProductionParameterRequest;
import com.erp.models.request.ProjectProductionRequest;
import com.erp.models.response.EmployeeResponse;
import com.erp.models.response.EquipmentCategoryResponse;
import com.erp.models.response.EquipmentResponse;
import com.erp.models.response.ProjectProductionParameterResponse;
import com.erp.models.response.ProjectProductionResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.assertj.core.api.SoftAssertions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Project Production")
@Feature("Equipment modification and reservation")
public class ProjectProductionModificationTest extends BaseFunctionalTest {

    private ProjectProductionFixture productionFixture;
    private EquipmentFixture equipmentFixture;
    private EmployeeFixture employeeFixture;
    private Long storageId;
    private Long targetStorageId;
    private Long categoryId;
    private final List<Long> productionIds = new ArrayList<>();
    private Long relocationId;
    private Long assignedEquipmentId;
    private Long statusChangedEquipmentId;
    private Long employeeId;
    private Long createdCategoryId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupModificationTests() {
        productionFixture = new ProjectProductionFixture(testContext, apiExecutor);
        productionFixture.prepareContext();
        equipmentFixture = new EquipmentFixture(testContext, apiExecutor);
        employeeFixture = new EmployeeFixture(testContext, apiExecutor);
        storageId = ConfigProvider.getOwner1StorageId();
        targetStorageId = ConfigProvider.getOwner2StorageId();
        categoryId = testContext.get(ContextKey.PROJECT_EQUIPMENT_CATEGORY_ID);
    }

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        if (relocationId != null) {
            try {
                equipmentFixture.resolveEquipmentRaw(
                        UserRole.ADMIN, relocationId, storageId, RelocationState.RETURNED);
            } catch (Exception e) {
                log.warn("Could not return relocation {}: {}", relocationId, e.getMessage());
            }
            relocationId = null;
        }
        if (assignedEquipmentId != null) {
            try {
                equipmentFixture.returnAssignmentRaw(UserRole.ADMIN, assignedEquipmentId);
            } catch (Exception e) {
                log.warn("Could not return assignment for equipment {}: {}", assignedEquipmentId, e.getMessage());
            }
            assignedEquipmentId = null;
        }
        if (statusChangedEquipmentId != null) {
            try {
                equipmentFixture.changeEquipmentStatusRaw(
                        UserRole.ADMIN, statusChangedEquipmentId, EquipmentStatus.AVAILABLE);
            } catch (Exception e) {
                log.warn("Could not restore equipment status {}: {}", statusChangedEquipmentId, e.getMessage());
            }
            statusChangedEquipmentId = null;
        }

        List<Long> ids = new ArrayList<>(productionIds);
        Collections.reverse(ids);
        for (Long id : ids) {
            try {
                ProjectProductionResponse production = productionFixture.getById(id, storageId);
                if (production.getState() == ProjectProductionState.DONE) {
                    productionFixture.cancelFinishedAs(UserRole.ADMIN, id, storageId);
                }
                productionFixture.deleteAs(UserRole.ADMIN, id, storageId, null);
            } catch (Exception e) {
                log.warn("Could not clean modification {}: {}", id, e.getMessage());
            }
        }
        productionIds.clear();

        if (employeeId != null) {
            try {
                employeeFixture.deleteRaw(UserRole.ADMIN, employeeId);
            } catch (Exception e) {
                log.warn("Could not delete employee {}: {}", employeeId, e.getMessage());
            }
            employeeId = null;
        }

        if (createdCategoryId != null) {
            try {
                apiExecutor.execute(
                        ApiEndpointDefinition.EQUIPMENT_CATEGORY_DELETE,
                        UserRole.ADMIN, null, createdCategoryId);
            } catch (Exception e) {
                log.warn("Could not delete equipment category {}: {}", createdCategoryId, e.getMessage());
            }
            createdCategoryId = null;
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-PROJ-MOD-001")
    @Story("MODIFICATION changes the selected equipment")
    @Severity(SeverityLevel.CRITICAL)
    public void modificationUpdatesAndFinishesTheSameEquipment() {
        EquipmentResponse equipment = createEquipment();
        ProjectProductionResponse modification = createModification(equipment);

        assertThat(modification.getEquipment().getId()).isEqualTo(equipment.getId());
        assertThat(modification.getSerialNumber()).isEqualTo(equipment.getSerialNumber());
        assertThat(productionFixture.getEquipmentParameters(storageId, equipment.getId()))
                .anySatisfy(parameter -> {
                    assertThat(parameter.getName()).isEqualTo("Firmware");
                    assertThat(parameter.getValue()).isEqualTo("2.0");
                });

        productionFixture.finishAs(UserRole.ADMIN, modification.getId(), storageId);
        ProjectProductionResponse finished = productionFixture.getById(modification.getId(), storageId);
        assertThat(finished.getState()).isEqualTo(ProjectProductionState.DONE);
        assertThat(finished.getEquipment().getId()).isEqualTo(equipment.getId());
    }

    @Test(priority = 20)
    @TestCaseId("TC-PROJ-MOD-002")
    @Story("One active modification per equipment")
    public void secondActiveModificationIsRejected() {
        EquipmentResponse equipment = createEquipment();
        createModification(equipment);

        Response second = productionFixture.createRaw(
                UserRole.ADMIN, modificationRequest(equipment));
        assertThat(second.statusCode()).isBetween(400, 499);
    }

    @Test(priority = 25)
    @TestCaseId("TC-PROJ-MOD-006")
    @Story("Equipment identity is immutable during MODIFICATION")
    @Description("Після створення MODIFICATION не можна змінити equipmentId з A на B")
    @Severity(SeverityLevel.BLOCKER)
    public void equipmentCannotBeChangedInExistingModification() {
        EquipmentResponse equipmentA = createEquipment();
        EquipmentResponse equipmentB = createEquipment();
        ProjectProductionResponse modification = createModification(equipmentA);

        Map<String, String> parametersAAfterCreate = parameterSnapshot(equipmentA.getId());
        Map<String, String> parametersBBeforeUpdate = parameterSnapshot(equipmentB.getId());
        ProjectProductionRequest switchToB = modificationRequest(equipmentB).toBuilder()
                .parameters(List.of(ProjectProductionParameterRequest.builder()
                        .name("Firmware")
                        .value("3.0")
                        .build()))
                .build();

        Response response = productionFixture.updateRaw(
                UserRole.ADMIN, modification.getId(), switchToB);
        ProjectProductionResponse persisted = productionFixture.getById(modification.getId(), storageId);
        Map<String, String> parametersAAfterUpdate = parameterSnapshot(equipmentA.getId());
        Map<String, String> parametersBAfterUpdate = parameterSnapshot(equipmentB.getId());

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(response.statusCode())
                    .as("Changing equipmentId A→B must be rejected; body=%s", response.asString())
                    .isBetween(400, 499);
            softly.assertThat(persisted.getEquipment().getId())
                    .as("Project must remain linked to equipment A")
                    .isEqualTo(equipmentA.getId());
            softly.assertThat(parametersAAfterUpdate)
                    .as("Rejected switch must not add side effects to equipment A")
                    .isEqualTo(parametersAAfterCreate);
            softly.assertThat(parametersBAfterUpdate)
                    .as("Rejected switch must not change equipment B")
                    .isEqualTo(parametersBBeforeUpdate);
        });
    }

    @Test(priority = 27)
    @TestCaseId("TC-PROJ-MOD-007")
    @Story("Equipment category and model are immutable during MODIFICATION")
    @Description("Category/model проєкту не можна змінити після вибору обладнання")
    @Severity(SeverityLevel.BLOCKER)
    public void categoryAndModelCannotBeChangedInExistingModification() {
        EquipmentResponse equipmentA = createEquipment();
        EquipmentResponse equipmentB = createEquipment();
        ProjectProductionResponse modification = createModification(equipmentA);
        Long originalCategoryId = modification.getEquipmentCategory().getId();
        Long originalModelId = modification.getEquipmentModel().getId();
        Long differentCategoryId = resolveDifferentCategoryId(originalCategoryId);
        Long differentModelId = productionFixture.findEquipmentModelId(equipmentB.getName());

        ProjectProductionRequest changedClassification = modificationRequest(equipmentA).toBuilder()
                .equipmentCategoryId(differentCategoryId)
                .equipmentModelId(differentModelId)
                .equipmentModelName(null)
                .parameters(null)
                .build();

        Response response = productionFixture.updateRaw(
                UserRole.ADMIN, modification.getId(), changedClassification);
        ProjectProductionResponse persisted = productionFixture.getById(modification.getId(), storageId);
        Response equipmentResponse = apiExecutor.execute(
                ApiEndpointDefinition.EQUIPMENT_GET_BY_ID,
                UserRole.ADMIN, null, equipmentA.getId());
        EquipmentResponse persistedEquipment = equipmentResponse.as(EquipmentResponse.class);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(response.statusCode())
                    .as("Changing category/model of an existing MODIFICATION must be rejected; body=%s",
                            response.asString())
                    .isBetween(400, 499);
            softly.assertThat(persisted.getEquipment().getId()).isEqualTo(equipmentA.getId());
            softly.assertThat(persisted.getEquipmentCategory().getId()).isEqualTo(originalCategoryId);
            softly.assertThat(persisted.getEquipmentModel().getId()).isEqualTo(originalModelId);
            softly.assertThat(persistedEquipment.getCategory().getId()).isEqualTo(originalCategoryId);
            softly.assertThat(persistedEquipment.getName()).isEqualTo(equipmentA.getName());
        });
    }

    @Test(priority = 30)
    @TestCaseId("TC-PROJ-MOD-003")
    @Story("Active modification reserves equipment from relocation")
    @Description("Обладнання в активній MODIFICATION не можна перемістити")
    @Severity(SeverityLevel.BLOCKER)
    public void equipmentInActiveModificationCannotBeRelocated() {
        EquipmentResponse equipment = createEquipment();
        createModification(equipment);

        EquipmentRelocationSendRequest request = EquipmentRelocationSendRequest.builder()
                .fromStorageId(storageId)
                .toStorageId(targetStorageId)
                .equipmentIds(List.of(equipment.getId()))
                .date(LocalDate.now())
                .description("must be blocked by active modification")
                .build();
        Response response = equipmentFixture.sendEquipmentRaw(UserRole.ADMIN, request);
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            relocationId = response.as(RelocationResponse.class).getId();
        }

        assertThat(response.statusCode())
                .as("Relocation of equipment reserved by MODIFICATION must be rejected; body=%s", response.asString())
                .isBetween(400, 499);
    }

    @Test(priority = 40)
    @TestCaseId("TC-PROJ-MOD-004")
    @Story("Active modification reserves equipment from assignment")
    @Description("Обладнання в активній MODIFICATION не можна закріпити за співробітником")
    @Severity(SeverityLevel.BLOCKER)
    public void equipmentInActiveModificationCannotBeAssigned() {
        EquipmentResponse equipment = createEquipment();
        createModification(equipment);
        EmployeeResponse employee = employeeFixture.createEmployee(
                UserRole.ADMIN, storageId, "pp-mod-" + System.currentTimeMillis() % 1_000_000);
        employeeId = employee.getId();

        Response response = equipmentFixture.assignEquipmentRaw(
                UserRole.ADMIN, equipment.getId(), employee.getId());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            assignedEquipmentId = equipment.getId();
        }

        assertThat(response.statusCode())
                .as("Assignment of equipment reserved by MODIFICATION must be rejected; body=%s", response.asString())
                .isBetween(400, 499);
    }

    @Test(priority = 50)
    @TestCaseId("TC-PROJ-MOD-005")
    @Story("Active modification reserves equipment from repair")
    @Description("Обладнання в активній MODIFICATION не можна перевести в ремонт")
    @Severity(SeverityLevel.BLOCKER)
    public void equipmentInActiveModificationCannotBeSentToRepair() {
        EquipmentResponse equipment = createEquipment();
        createModification(equipment);

        Response response = equipmentFixture.changeEquipmentStatusRaw(
                UserRole.ADMIN, equipment.getId(), EquipmentStatus.IN_REPAIR);
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            statusChangedEquipmentId = equipment.getId();
        }

        assertThat(response.statusCode())
                .as("Repair status for equipment reserved by MODIFICATION must be rejected; body=%s", response.asString())
                .isBetween(400, 499);
    }

    private EquipmentResponse createEquipment() {
        return equipmentFixture.createEquipmentOnStorage(UserRole.ADMIN, storageId, categoryId);
    }

    private ProjectProductionResponse createModification(EquipmentResponse equipment) {
        ProjectProductionResponse created = productionFixture.createAs(
                UserRole.ADMIN, modificationRequest(equipment));
        productionIds.add(created.getId());
        return created;
    }

    private ProjectProductionRequest modificationRequest(EquipmentResponse equipment) {
        Long modelId = productionFixture.findEquipmentModelId(equipment.getName());
        return ProjectProductionDataFactory.buildCreateRequest(
                        storageId,
                        categoryId,
                        modelId,
                        ProjectProductionState.IN_PROGRESS,
                        ProjectProductionType.MODIFICATION,
                        null)
                .toBuilder()
                .serialNumber(null)
                .equipmentId(equipment.getId())
                .parameters(List.of(ProjectProductionParameterRequest.builder()
                        .name("Firmware")
                        .value("2.0")
                        .build()))
                .build();
    }

    private Map<String, String> parameterSnapshot(Long equipmentId) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (ProjectProductionParameterResponse parameter
                : productionFixture.getEquipmentParameters(storageId, equipmentId)) {
            snapshot.put(parameter.getName(), parameter.getValue());
        }
        return snapshot;
    }

    private Long resolveDifferentCategoryId(Long originalCategoryId) {
        Long existing = productionFixture.getEquipmentCategories().stream()
                .map(EquipmentCategoryResponse::getId)
                .filter(id -> !id.equals(originalCategoryId))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.EQUIPMENT_CATEGORY_POST_CREATE,
                UserRole.ADMIN,
                EquipmentCategoryRequest.builder()
                        .name("pp-mod-category-" + System.currentTimeMillis())
                        .build());
        assertThat(response.statusCode()).isBetween(200, 299);
        createdCategoryId = response.as(EquipmentCategoryResponse.class).getId();
        return createdCategoryId;
    }
}
