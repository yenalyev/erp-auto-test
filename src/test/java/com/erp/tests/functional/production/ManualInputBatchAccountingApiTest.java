package com.erp.tests.functional.production;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.defect.DefectDataFactory;
import com.erp.data.factories.non_series_production.NonSeriesProductionDataFactory;
import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.data.factories.project_production.ProjectProductionDataFactory;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.BusinessRole;
import com.erp.enums.ProjectProductionState;
import com.erp.enums.ProjectProductionType;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProductionFixture;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.ProjectProductionFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.common.DefectBatchItem;
import com.erp.models.request.DefectRequest;
import com.erp.models.request.ManufacturingItemRequest;
import com.erp.models.request.ManufacturingListRequest;
import com.erp.models.request.NonSeriesProductionRequest;
import com.erp.models.request.NonSeriesProductionResourceUsageRequest;
import com.erp.models.request.ProjectProductionRequest;
import com.erp.models.request.ProjectProductionStageRequest;
import com.erp.models.request.ProjectProductionStageResourceUsageRequest;
import com.erp.models.request.RelocationInputRequest;
import com.erp.models.request.RelocationItemBatchRequest;
import com.erp.models.request.RelocationOutputRequest;
import com.erp.models.request.RelocationOutputEditRequest;
import com.erp.models.request.RelocationUpdateRequest;
import com.erp.models.request.ResourceBatchSelectionRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.DefectResponse;
import com.erp.models.response.BatchInspectionResponse;
import com.erp.models.response.EquipmentCategoryResponse;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.NonSeriesProductionResourceUsageResponse;
import com.erp.models.response.NonSeriesProductionResponse;
import com.erp.models.response.ProjectProductionResponse;
import com.erp.models.response.RelocationItemResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceUsageResponse;
import com.erp.models.response.StorageItemBatchResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.AllureHelper;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.utils.helpers.ProductionBatchAssertions;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Step;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Generated;
import org.assertj.core.api.AbstractDoubleAssert;
import org.assertj.core.api.AbstractIntegerAssert;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.DoubleAssert;
import org.assertj.core.api.IntegerAssert;
import org.assertj.core.api.ListAssert;
import org.assertj.core.api.SoftAssertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Epic("Production")
@Feature("Manual input batch accounting API")
public class ManualInputBatchAccountingApiTest extends BaseFunctionalTest {
   @Generated
   private static final Logger log = LoggerFactory.getLogger(ManualInputBatchAccountingApiTest.class);
   private static final double MIN_INPUT_STOCK_PER_TEST = (double)500.0F;
   private ProductionFixture productionFixture;
   private StorageFixture storageFixture;
   private UserFixture userFixture;
   private Long storageId;
   private TechnologicalMapResponse techMap;
   private Long inputResourceId1;
   private Long inputResourceId2;
   private Long outputResourceId;

   @BeforeClass(
      alwaysRun = true,
      dependsOnMethods = {"baseTestClassSetup"}
   )
   @Step("Підготовка середовища для тестів виробництва")
   public void setupProductionTest() {
      this.storageFixture = new StorageFixture(this.testContext, this.apiExecutor);
      StorageResponse storage = this.storageFixture.createProductionStorage(ConfigProvider.getOwner1StorageId(), "prd-isolated-");
      this.storageId = storage.getId();
      this.userFixture = new UserFixture(this.testContext, this.apiExecutor);
      UserFixture.BusinessActor owner = this.userFixture.createBusinessActor(getPlaywrightSessionProvider(), BusinessRole.BUSINESS_UNIT_OWNER, List.of(storage));
      this.apiExecutor.setSessionForRole(UserRole.OWNER_1, owner.username(), owner.password());
      this.productionFixture = new ProductionFixture(this.testContext, this.apiExecutor);
      this.productionFixture.prepareContext(this.storageId);
      this.techMap = (TechnologicalMapResponse)this.testContext.get(ContextKey.PRODUCTION_TECH_MAP);
      if (this.techMap == null) {
         throw new IllegalStateException("PRODUCTION_TECH_MAP missing after fixture setup");
      } else {
         List<Long> inputIds = (List)this.testContext.get(ContextKey.PRODUCTION_INPUT_RESOURCE_IDS);
         if (inputIds != null && inputIds.size() >= 2) {
            this.inputResourceId1 = (Long)inputIds.get(0);
            this.inputResourceId2 = (Long)inputIds.get(1);
            this.outputResourceId = (Long)this.testContext.get(ContextKey.PRODUCTION_OUTPUT_RESOURCE_ID);
            SchemaRegistry.logSchemaCoverage();
         } else {
            throw new IllegalStateException("PRODUCTION_INPUT_RESOURCE_IDS missing after fixture setup");
         }
      }
   }

   @AfterClass(
      alwaysRun = true
   )
   public void cleanupProductionLocation() {
      if (this.techMap != null && this.productionFixture != null && this.storageId != null) {
         try {
            this.productionFixture.getTechMapFixture().deactivateTechMap(UserRole.ADMIN, this.techMap.getId(), this.storageId);
         } catch (RuntimeException e) {
            log.warn("Could not deactivate dynamic production tech map {}", this.techMap.getId(), e);
         }
      }

      if (this.storageFixture != null) {
         this.storageFixture.deactivateTrackedStorages(UserRole.ADMIN);
      }

      if (this.inputResourceId1 != null && this.inputResourceId2 != null && this.outputResourceId != null) {
         ResourceFixture resources = new ResourceFixture(this.testContext, this.apiExecutor);

         for(Long id : List.of(this.inputResourceId1, this.inputResourceId2, this.outputResourceId)) {
            try {
               resources.deactivate(UserRole.ADMIN, id);
            } catch (RuntimeException e) {
               log.warn("Could not deactivate dynamic production resource {}", id, e);
            }
         }
      }

      if (this.apiExecutor != null) {
         this.apiExecutor.evictSessionForRole(UserRole.OWNER_1);
      }

      if (this.userFixture != null) {
         this.userFixture.deactivateTrackedUsers();
      }

   }

   @BeforeMethod(
      alwaysRun = true
   )
   @Step("Поповнити запас сировини перед тестом (ізоляція на staging)")
   public void ensureInputStockBeforeTest() {
      if (this.storageId != null && this.inputResourceId1 != null && this.inputResourceId2 != null) {
         this.productionFixture.ensureInputStockAtLeast(this.storageId, this.inputResourceId1, this.inputResourceId2, (double)500.0F);
      } else {
         throw new SkipException("Production class fixture setup did not complete");
      }
   }

   @Test(
      priority = 11
   )
   @TestCaseId({"TC-MIB-001"})
   @Story("Location-specific batch selection")
   @Description("Перемикач ресурсу зберігається для конкретної локації; фільтр повертає MANUAL/FIFO")
   @Severity(SeverityLevel.CRITICAL)
   public void batchSelectionIsScopedToLocationAndFiltersResources() {
      Long otherStorageId = ConfigProvider.getOwner1StorageId();
      Assertions.assertThat(this.selectedResourceIds(this.storageId)).doesNotContain(new Long[]{this.inputResourceId1});

      try {
         Response ownerEnabled = this.apiExecutor.execute(ApiEndpointDefinition.RESOURCE_BATCH_SELECTION_PUT, UserRole.OWNER_1, new ResourceBatchSelectionRequest(true), this.storageId, this.inputResourceId1);
         ((AbstractIntegerAssert)Assertions.assertThat(ownerEnabled.statusCode()).as("Location manager can enable manual selection", new Object[0])).isBetween(200, 299);
         Assertions.assertThat(this.selectedResourceIds(this.storageId)).contains(new Long[]{this.inputResourceId1});
         Assertions.assertThat(this.selectedResourceIds(otherStorageId)).doesNotContain(new Long[]{this.inputResourceId1});
         Response denied = this.apiExecutor.execute(ApiEndpointDefinition.RESOURCE_BATCH_SELECTION_PUT, UserRole.ANONYMOUS, new ResourceBatchSelectionRequest(false), this.storageId, this.inputResourceId1);
         Assertions.assertThat(denied.statusCode()).isIn(new Object[]{401, 403});
         Response otherOwnerDenied = this.apiExecutor.execute(ApiEndpointDefinition.RESOURCE_BATCH_SELECTION_PUT, UserRole.OWNER_2, new ResourceBatchSelectionRequest(false), this.storageId, this.inputResourceId1);
         Assertions.assertThat(otherOwnerDenied.statusCode()).isEqualTo(403);
         Assertions.assertThat(this.selectedResourceIds(this.storageId)).contains(new Long[]{this.inputResourceId1});
         Assertions.assertThat(this.resourceIdsByBatchSelection(true, this.inputResourceId1)).contains(new Long[]{this.inputResourceId1});
         Assertions.assertThat(this.resourceIdsByBatchSelection(false, this.inputResourceId1)).doesNotContain(new Long[]{this.inputResourceId1});
         Assertions.assertThat(this.resourceIdsByBatchSelection(false, this.inputResourceId2)).contains(new Long[]{this.inputResourceId2});
         Assertions.assertThat(this.resourceIdsByBatchSelection(true, this.inputResourceId2)).doesNotContain(new Long[]{this.inputResourceId2});
         String name = (new ResourceFixture(this.testContext, this.apiExecutor)).getById(UserRole.ADMIN, this.inputResourceId1).getName();
         Response otherLocationFifo = this.apiExecutor.executeWithQueryParams(ApiEndpointDefinition.RESOURCE_GET_ALL, UserRole.ADMIN, Map.of("storageId", otherStorageId, "batchSelection", false, "name", name, "page", 0, "size", 100), new Object[0]);
         Assertions.assertThat(otherLocationFifo.statusCode()).isEqualTo(200);
         Assertions.assertThat(otherLocationFifo.jsonPath().getList("content.id", Number.class).stream().map(Number::longValue).toList()).contains(new Long[]{this.inputResourceId1});
      } finally {
         this.setBatchSelection(this.inputResourceId1, false);
      }

      Assertions.assertThat(this.selectedResourceIds(this.storageId)).doesNotContain(new Long[]{this.inputResourceId1});
   }

   @Test(
      priority = 12
   )
   @TestCaseId({"TC-MIB-004"})
   @Story("Manual raw batch in serial production")
   @Description("Серійне виробництво відхиляє відсутній UUID, а явний UUID списує саме вибрану партію")
   @Severity(SeverityLevel.CRITICAL)
   public void serialProductionRequiresAndConsumesSelectedRawBatch() {
      String var10000 = UUID.randomUUID().toString();
      String namedBatch = "manual-" + var10000.substring(0, 8);
      StorageItemBatchResponse selected = this.seedNamedInputBatch(namedBatch, 20);
      Assertions.assertThat(selected.getBatchUuid()).isNotNull();
      double selectedBefore = selected.getAmount();
      double required = ((ResourceUsageResponse)this.techMap.getInput().stream().filter((input) -> this.inputResourceId1.equals(input.getResource().getId())).findFirst().orElseThrow()).getAmount();
      long journalBefore = this.getProductionListSize();
      ManufacturingListRequest disallowed = ProductionDataFactory.buildCreateRequest(this.techMap, (double)1.0F);
      ((ResourceUsageRequest)((ManufacturingItemRequest)disallowed.getItems().getFirst()).getInputs().stream().filter((input) -> this.inputResourceId1.equals(input.getResourceId())).findFirst().orElseThrow()).setBatchUuid(selected.getBatchUuid());
      Assertions.assertThat(this.apiExecutor.execute(ApiEndpointDefinition.PRODUCTION_POST_CREATE, UserRole.OWNER_1, disallowed, this.storageId).statusCode()).isEqualTo(400);

      try {
         this.setBatchSelection(this.inputResourceId1, true);
         ManufacturingListRequest missing = ProductionDataFactory.buildCreateRequest(this.techMap, (double)1.0F);
         Response rejected = this.apiExecutor.execute(ApiEndpointDefinition.PRODUCTION_POST_CREATE, UserRole.OWNER_1, missing, this.storageId);
         Assertions.assertThat(rejected.statusCode()).isEqualTo(400);
         Assertions.assertThat(rejected.jsonPath().getString("errors[0].field")).contains(new CharSequence[]{"batchUuid"});
         Assertions.assertThat(this.getProductionListSize()).isEqualTo(journalBefore);
         Assertions.assertThat(this.batchAmount(selected.getBatchUuid())).isCloseTo(selectedBefore, Assertions.within(0.01));
         ManufacturingListRequest nonexistent = ProductionDataFactory.buildCreateRequest(this.techMap, (double)1.0F);
         ((ResourceUsageRequest)((ManufacturingItemRequest)nonexistent.getItems().getFirst()).getInputs().stream().filter((input) -> this.inputResourceId1.equals(input.getResourceId())).findFirst().orElseThrow()).setBatchUuid(UUID.randomUUID());
         Assertions.assertThat(this.apiExecutor.execute(ApiEndpointDefinition.PRODUCTION_POST_CREATE, UserRole.OWNER_1, nonexistent, this.storageId).statusCode()).isEqualTo(400);
         Assertions.assertThat(this.batchAmount(selected.getBatchUuid())).isCloseTo(selectedBefore, Assertions.within(0.01));
         ManufacturingListRequest valid = ProductionDataFactory.buildCreateRequest(this.techMap, (double)1.0F);
         ((ResourceUsageRequest)((ManufacturingItemRequest)valid.getItems().getFirst()).getInputs().stream().filter((input) -> this.inputResourceId1.equals(input.getResourceId())).findFirst().orElseThrow()).setBatchUuid(selected.getBatchUuid());
         Response created = this.apiExecutor.execute(ApiEndpointDefinition.PRODUCTION_POST_CREATE, UserRole.OWNER_1, valid, this.storageId);
         Assertions.assertThat(created.statusCode()).isEqualTo(200);
         Assertions.assertThat(this.getProductionListSize()).isEqualTo(journalBefore + 1L);
         StorageItemBatchResponse after = (StorageItemBatchResponse)ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.OWNER_1, this.inputResourceId1, false, namedBatch).stream().filter((b) -> selected.getBatchUuid().equals(b.getBatchUuid())).findFirst().orElseThrow();
         Assertions.assertThat(after.getAmount()).isCloseTo(selectedBefore - required, Assertions.within(0.01));
         String productBatch = valid.getItems().getFirst().getBatchNumber();
         BatchInspectionResponse inspection = new InventoryFixture(this.testContext, this.apiExecutor)
            .inspectBatch(productBatch, UserRole.ADMIN).stream()
            .filter(item -> item.getResource() != null && this.outputResourceId.equals(item.getResource().getId()))
            .findFirst().orElseThrow();
         Assertions.assertThat(inspection.getIngredients())
            .anySatisfy(ingredient -> {
               Assertions.assertThat(ingredient.getResource().getId()).isEqualTo(this.inputResourceId1);
               Assertions.assertThat(ingredient.getBatchName()).isEqualTo(namedBatch);
               Assertions.assertThat(ingredient.getSelection()).isEqualTo("MANUAL");
            });
         Assertions.assertThat(inspection.getIngredients())
            .anySatisfy(ingredient -> {
               Assertions.assertThat(ingredient.getResource().getId()).isEqualTo(this.inputResourceId2);
               Assertions.assertThat(ingredient.getSelection()).isEqualTo("FIFO");
            });
      } finally {
         this.setBatchSelection(this.inputResourceId1, false);
      }

   }

   @Test(
      priority = 13
   )
   @TestCaseId({"TC-MIB-005"})
   @Story("Manual raw batch in non-series production")
   @Description("Несерійне виробництво вимагає batchUuid і списує тільки вибрану партію")
   @Severity(SeverityLevel.CRITICAL)
   public void nonSeriesProductionRequiresAndConsumesSelectedRawBatch() {
      StorageItemBatchResponse selected = this.seedNamedInputBatch("nsp-manual-" + UUID.randomUUID().toString().substring(0, 8), 20);
      double before = selected.getAmount();
      NonSeriesProductionRequest request = NonSeriesProductionDataFactory.buildInProgressRequest(this.storageId, this.inputResourceId1, (double)2.0F, (double)1.0F);
      Long createdId = null;

      try {
         this.setBatchSelection(this.inputResourceId1, true);
         Response rejected = this.apiExecutor.execute(ApiEndpointDefinition.NON_SERIES_PRODUCTION_POST_CREATE, UserRole.ADMIN, request);
         Assertions.assertThat(rejected.statusCode()).isEqualTo(400);
         Assertions.assertThat(rejected.jsonPath().getString("errors[0].field")).contains(new CharSequence[]{"batchUuid"});
         ((NonSeriesProductionResourceUsageRequest)request.getResourceUsageList().getFirst()).setBatchUuid(selected.getBatchUuid());
         Response created = this.apiExecutor.execute(ApiEndpointDefinition.NON_SERIES_PRODUCTION_POST_CREATE, UserRole.ADMIN, request);
         Assertions.assertThat(created.statusCode()).isEqualTo(200);
         NonSeriesProductionResponse item = (NonSeriesProductionResponse)created.as(NonSeriesProductionResponse.class);
         createdId = item.getId();
         Response read = this.apiExecutor.execute(ApiEndpointDefinition.NON_SERIES_PRODUCTION_GET_BY_ID, UserRole.ADMIN, (Object)null, createdId, this.storageId);
         Assertions.assertThat(read.statusCode()).isEqualTo(200);
         Assertions.assertThat(((NonSeriesProductionResourceUsageResponse)((NonSeriesProductionResponse)read.as(NonSeriesProductionResponse.class)).getResourceUsageList().getFirst()).getBatchUuid()).isEqualTo(selected.getBatchUuid());
         Assertions.assertThat(this.batchAmount(selected.getBatchUuid())).isCloseTo(before - (double)2.0F, Assertions.within(0.01));
      } finally {
         try {
            if (createdId != null) {
               Response deleted = this.apiExecutor.execute(ApiEndpointDefinition.NON_SERIES_PRODUCTION_DELETE, UserRole.ADMIN, (Object)null, createdId, this.storageId);
               Assertions.assertThat(deleted.statusCode()).isBetween(200, 299);
            }
         } finally {
            this.setBatchSelection(this.inputResourceId1, false);
         }

      }

   }

   @Test(
      priority = 14
   )
   @TestCaseId({"TC-MIB-006"})
   @Story("Manual raw batch in project production")
   @Description("Етап проєкту не вимагає UUID для плану, але вимагає його для фактичної витрати")
   @Severity(SeverityLevel.CRITICAL)
   public void projectStageRequiresSelectedBatchOnlyForActualUsage() {
      StorageItemBatchResponse selected = this.seedNamedInputBatch("project-manual-" + UUID.randomUUID().toString().substring(0, 8), 20);
      double before = selected.getAmount();
      ProjectProductionFixture projects = new ProjectProductionFixture(this.testContext, this.apiExecutor);
      Long categoryId = ((EquipmentCategoryResponse)projects.getEquipmentCategories().getFirst()).getId();
      Long modelId = projects.getEquipmentModels().stream().findFirst().map((model) -> model.getId()).orElse(null);
      ProjectProductionRequest request = ProjectProductionDataFactory.buildCreateRequest(this.storageId, categoryId, modelId, ProjectProductionState.IN_PROGRESS, ProjectProductionType.CREATION, (List)null);
      if (modelId == null) {
         request.setEquipmentModelName(ProjectProductionDataFactory.uniqueModelName());
      }

      Long productionId = null;
      boolean finished = false;

      try {
         this.setBatchSelection(this.inputResourceId1, true);
         ProjectProductionResponse production = projects.createAs(UserRole.ADMIN, request);
         productionId = production.getId();
         ProjectProductionStageRequest planned = ProjectProductionDataFactory.singleResourceStage(this.inputResourceId1, (double)3.0F, (double)0.0F);
         Response plannedCreated = projects.addStageRaw(UserRole.ADMIN, productionId, this.storageId, planned);
         Assertions.assertThat(plannedCreated.statusCode()).isEqualTo(200);
         Long stageId = plannedCreated.jsonPath().getLong("id");
         Assertions.assertThat(stageId).isNotNull();
         Assertions.assertThat(this.batchAmount(selected.getBatchUuid())).isCloseTo(before, Assertions.within(0.01));
         ProjectProductionStageRequest missing = ProjectProductionDataFactory.singleResourceStage(this.inputResourceId1, (double)3.0F, (double)3.0F);
         Response rejected = this.apiExecutor.execute(ApiEndpointDefinition.PROJECT_PRODUCTION_STAGE_PUT_UPDATE, UserRole.ADMIN, missing, new Object[]{productionId, stageId, this.storageId});
         Assertions.assertThat(rejected.statusCode()).isEqualTo(400);
         Assertions.assertThat(rejected.jsonPath().getString("errors[0].field")).contains(new CharSequence[]{"batchUuid"});
         ((ProjectProductionStageResourceUsageRequest)missing.getProjectProductionStageResourceUsages().getFirst()).setBatchUuid(selected.getBatchUuid());
         Response created = this.apiExecutor.execute(ApiEndpointDefinition.PROJECT_PRODUCTION_STAGE_PUT_UPDATE, UserRole.ADMIN, missing, new Object[]{productionId, stageId, this.storageId});
         Assertions.assertThat(created.statusCode()).isEqualTo(200);
         Assertions.assertThat(created.jsonPath().getString("projectProductionStageResourceUsages[0].batchUuid")).isEqualTo(selected.getBatchUuid().toString());
         Assertions.assertThat(this.batchAmount(selected.getBatchUuid())).isCloseTo(before - (double)3.0F, Assertions.within(0.01));
         ((ProjectProductionStageResourceUsageRequest)missing.getProjectProductionStageResourceUsages().getFirst()).setAmountUsed(BigDecimal.valueOf(4L));
         Response revised = this.apiExecutor.execute(ApiEndpointDefinition.PROJECT_PRODUCTION_STAGE_PUT_UPDATE, UserRole.ADMIN, missing, new Object[]{productionId, stageId, this.storageId});
         Assertions.assertThat(revised.statusCode()).isEqualTo(200);
         Assertions.assertThat(this.batchAmount(selected.getBatchUuid())).isCloseTo(before - (double)4.0F, Assertions.within(0.01));
         ((ProjectProductionStageResourceUsageRequest)missing.getProjectProductionStageResourceUsages().getFirst()).setBatchUuid(UUID.randomUUID());
         Response invalidEdit = this.apiExecutor.execute(ApiEndpointDefinition.PROJECT_PRODUCTION_STAGE_PUT_UPDATE, UserRole.ADMIN, missing, new Object[]{productionId, stageId, this.storageId});
         Assertions.assertThat(invalidEdit.statusCode()).isBetween(400, 499);
         Assertions.assertThat(this.batchAmount(selected.getBatchUuid())).isCloseTo(before - (double)4.0F, Assertions.within(0.01));
         projects.finishAs(UserRole.ADMIN, productionId, this.storageId);
         finished = true;
         Assertions.assertThat(projects.getById(productionId, this.storageId).getState()).isEqualTo(ProjectProductionState.DONE);
         Assertions.assertThat(this.batchAmount(selected.getBatchUuid())).isCloseTo(before - (double)4.0F, Assertions.within(0.01));
      } finally {
         try {
            if (productionId != null && !finished) {
               projects.deleteAs(UserRole.ADMIN, productionId, this.storageId, (List)null);
            }
         } finally {
            this.setBatchSelection(this.inputResourceId1, false);
         }

      }

   }

   @Test(
      priority = 15
   )
   @TestCaseId({"TC-MIB-007"})
   @Story("Manual batch in relocation")
   public void relocationConsumesSelectedBatchAndReceivesItAtDestination() {
      StorageItemBatchResponse older = this.seedNamedInputBatch("rel-old-" + String.valueOf(UUID.randomUUID()), 10);
      StorageItemBatchResponse younger = this.seedNamedInputBatch("rel-new-" + String.valueOf(UUID.randomUUID()), 10);
      Long destinationId = ConfigProvider.getOwner1StorageId();
      double olderBefore = this.batchAmount(older.getBatchUuid());
      double youngerBefore = this.batchAmount(younger.getBatchUuid());

      try {
         this.setBatchSelection(this.inputResourceId1, true);
         Response send = this.apiExecutor.execute(ApiEndpointDefinition.RELOCATION_POST_SEND, UserRole.OWNER_1, RelocationDataFactory.buildSendWithBatch(this.storageId, destinationId, this.inputResourceId1, (double)6.0F, younger.getBatchUuid(), younger.getBatchNumber(), false));
         Assertions.assertThat(send.statusCode()).isBetween(200, 299);
         RelocationResponse relocation = (RelocationResponse)send.as(RelocationResponse.class);
         Assertions.assertThat(((RelocationItemResponse)relocation.getItems().getFirst()).getBatches()).anyMatch((b) -> younger.getBatchUuid().equals(b.getBatchUuid()));
         Assertions.assertThat(this.batchAmount(older.getBatchUuid())).isCloseTo(olderBefore, Assertions.within(0.01));
         Assertions.assertThat(this.batchAmount(younger.getBatchUuid())).isCloseTo(youngerBefore - (double)6.0F, Assertions.within(0.01));
         Response resolved = this.apiExecutor.execute(ApiEndpointDefinition.RELOCATION_PUT_RESOLVE, UserRole.ADMIN, RelocationUpdateRequest.builder().state(RelocationState.FINISHED).receivedDate(LocalDate.now()).build(), relocation.getId(), destinationId);
         Assertions.assertThat(resolved.statusCode()).isBetween(200, 299);
         Assertions.assertThat(ProductionStockAssertions.queryBatches(this.apiExecutor, destinationId, UserRole.ADMIN, this.inputResourceId1, false, younger.getBatchNumber())).anyMatch((b) -> younger.getBatchUuid().equals(b.getBatchUuid()));
         Assertions.assertThat(this.apiExecutor.execute(ApiEndpointDefinition.RESOURCE_BATCH_SELECTION_PUT, UserRole.ADMIN, new ResourceBatchSelectionRequest(true), destinationId, this.inputResourceId1).statusCode()).isBetween(200, 299);
         Response secondSend = this.apiExecutor.execute(ApiEndpointDefinition.RELOCATION_POST_SEND, UserRole.OWNER_1, RelocationDataFactory.buildSendWithBatch(this.storageId, destinationId, this.inputResourceId1, (double)2.0F, younger.getBatchUuid(), younger.getBatchNumber(), false));
         Assertions.assertThat(secondSend.statusCode()).isBetween(200, 299);
         Assertions.assertThat(this.batchAmount(younger.getBatchUuid())).isCloseTo(youngerBefore - (double)8.0F, Assertions.within(0.01));
         Assertions.assertThat(this.apiExecutor.execute(ApiEndpointDefinition.RELOCATION_PUT_RESOLVE, UserRole.ADMIN, RelocationUpdateRequest.builder().state(RelocationState.FINISHED).receivedDate(LocalDate.now()).build(), secondSend.jsonPath().getLong("id"), destinationId).statusCode()).isBetween(200, 299);
      } finally {
         try {
            this.apiExecutor.execute(ApiEndpointDefinition.RESOURCE_BATCH_SELECTION_PUT, UserRole.ADMIN, new ResourceBatchSelectionRequest(false), destinationId, this.inputResourceId1);
         } finally {
            this.setBatchSelection(this.inputResourceId1, false);
         }
      }

   }

   @Test(
      priority = 16
   )
   @TestCaseId({"TC-MIB-008"})
   @Story("Manual batch in warehouse defect")
   public void warehouseDefectConsumesSelectedBatchAndCancelRestoresIt() {
      StorageItemBatchResponse older = this.seedNamedInputBatch("def-old-" + String.valueOf(UUID.randomUUID()), 10);
      StorageItemBatchResponse younger = this.seedNamedInputBatch("def-new-" + String.valueOf(UUID.randomUUID()), 10);
      double olderBefore = this.batchAmount(older.getBatchUuid());
      double youngerBefore = this.batchAmount(younger.getBatchUuid());
      Long defectId = null;

      try {
         this.setBatchSelection(this.inputResourceId1, true);
         DefectRequest request = DefectDataFactory.buildStorageExplicitBatchesDefect(this.storageId, this.inputResourceId1, 3.0, List.of(DefectDataFactory.batch(younger.getBatchUuid(), younger.getBatchNumber(), false, 3.0)));
         Response created = this.apiExecutor.executeDefectCreate(request, UserRole.OWNER_1);
         ((AbstractIntegerAssert)Assertions.assertThat(created.statusCode()).as("Warehouse defect response: %s", new Object[]{created.asString()})).isBetween(200, 299);
         DefectResponse defect = (DefectResponse)created.as(DefectResponse.class);
         defectId = defect.getId();
         Assertions.assertThat(defect.getDefectBatches()).anyMatch((b) -> younger.getBatchUuid().equals(b.getBatchUuid()));
         Assertions.assertThat(this.batchAmount(older.getBatchUuid())).isCloseTo(olderBefore, Assertions.within(0.01));
         Assertions.assertThat(this.batchAmount(younger.getBatchUuid())).isCloseTo(youngerBefore - (double)3.0F, Assertions.within(0.01));
      } finally {
         try {
            if (defectId != null) {
               Response deleted = this.apiExecutor.execute(ApiEndpointDefinition.DEFECT_DELETE, UserRole.OWNER_1, (Object)null, defectId, this.storageId);
               Assertions.assertThat(deleted.statusCode()).isBetween(200, 299);
               Assertions.assertThat(this.batchAmount(older.getBatchUuid())).isCloseTo(olderBefore, Assertions.within(0.01));
               Assertions.assertThat(this.batchAmount(younger.getBatchUuid())).isCloseTo(youngerBefore, Assertions.within(0.01));
            }
         } finally {
            this.setBatchSelection(this.inputResourceId1, false);
         }

      }

   }

   @Test(
      priority = 16
   )
   @TestCaseId({"TC-MIB-017"})
   @Story("Unknown-origin stock is an explicit warehouse defect choice")
   public void warehouseDefectSelectsOnlyExistingUnknownBatch() {
      StorageItemBatchResponse known = this.seedNamedInputBatch("known-for-unknown-" + String.valueOf(UUID.randomUUID()), 10);
      StorageItemBatchResponse unknown = this.seedUnknownInputBatch();
      double knownBefore = this.batchAmount(known.getBatchUuid());
      double unknownBefore = this.batchAmount(unknown.getBatchUuid());
      Long defectId = null;

      try {
         this.setBatchSelection(this.inputResourceId1, true);
         DefectRequest request = DefectDataFactory.buildStorageExplicitBatchesDefect(this.storageId, this.inputResourceId1, 3.0, List.of(DefectDataFactory.batch(unknown.getBatchUuid(), unknown.getBatchNumber(), false, 3.0)));
         Response created = this.apiExecutor.executeDefectCreate(request, UserRole.OWNER_1);
         ((AbstractIntegerAssert)Assertions.assertThat(created.statusCode()).as(created.asString(), new Object[0])).isBetween(200, 299);
         defectId = created.jsonPath().getLong("id");
         Assertions.assertThat(created.jsonPath().getString("defectBatches[0].batchUuid")).isEqualTo(unknown.getBatchUuid().toString());
         Assertions.assertThat(this.batchAmount(known.getBatchUuid())).isCloseTo(knownBefore, Assertions.within(0.01));
         Assertions.assertThat(this.batchAmount(unknown.getBatchUuid())).isCloseTo(unknownBefore - (double)3.0F, Assertions.within(0.01));
      } finally {
         try {
            if (defectId != null) {
               Assertions.assertThat(this.apiExecutor.execute(ApiEndpointDefinition.DEFECT_DELETE, UserRole.OWNER_1, (Object)null, defectId, this.storageId).statusCode()).isBetween(200, 299);
               Assertions.assertThat(this.batchAmount(known.getBatchUuid())).isCloseTo(knownBefore, Assertions.within(0.01));
               Assertions.assertThat(this.batchAmount(unknown.getBatchUuid())).isCloseTo(unknownBefore, Assertions.within(0.01));
            }
         } finally {
            this.setBatchSelection(this.inputResourceId1, false);
         }

      }

   }

   @Test(
      priority = 17
   )
   @TestCaseId({"TC-MIB-009"})
   @Story("Manual mode requires explicit batches for every stock operation")
   public void relocationAndWarehouseDefectRejectMissingBatchWithoutStockChange() {
      StorageItemBatchResponse batch = this.seedNamedInputBatch("missing-" + String.valueOf(UUID.randomUUID()), 10);
      double before = this.batchAmount(batch.getBatchUuid());

      try {
         this.setBatchSelection(this.inputResourceId1, true);
         Response relocation = this.apiExecutor.execute(ApiEndpointDefinition.RELOCATION_POST_SEND, UserRole.OWNER_1, RelocationDataFactory.buildSendRequest(this.storageId, ConfigProvider.getOwner1StorageId(), this.inputResourceId1, (double)1.0F));
         Response defect = this.apiExecutor.executeDefectCreate(DefectDataFactory.buildStorageFifoDefect(this.storageId, this.inputResourceId1, (double)1.0F), UserRole.OWNER_1);
         if (relocation.statusCode() >= 200 && relocation.statusCode() < 300) {
            this.apiExecutor.execute(ApiEndpointDefinition.RELOCATION_PUT_RESOLVE, UserRole.ADMIN, RelocationUpdateRequest.builder().state(RelocationState.CANCELLED).build(), relocation.jsonPath().getLong("id"), ConfigProvider.getOwner1StorageId());
         }

         if (defect.statusCode() >= 200 && defect.statusCode() < 300) {
            this.apiExecutor.execute(ApiEndpointDefinition.DEFECT_DELETE, UserRole.OWNER_1, (Object)null, defect.jsonPath().getLong("id"), this.storageId);
         }

         SoftAssertions checks = new SoftAssertions();
         ((IntegerAssert)checks.assertThat(relocation.statusCode()).as("Relocation without selected batch", new Object[0])).isBetween(400, 499);
         ((IntegerAssert)checks.assertThat(defect.statusCode()).as("Warehouse defect without selected batch", new Object[0])).isBetween(400, 499);
         ((DoubleAssert)checks.assertThat(this.batchAmount(batch.getBatchUuid())).as("Stock after rejected missing-batch requests", new Object[0])).isCloseTo(before, Assertions.within(0.01));
         checks.assertAll();
      } finally {
         this.setBatchSelection(this.inputResourceId1, false);
      }

   }

   @Test(
      priority = 18
   )
   @TestCaseId({"TC-MIB-010"})
   @Story("Invalid raw batch selections")
   public void serialProductionRejectsForeignMissingAndInsufficientBatches() {
      StorageItemBatchResponse selected = this.seedNamedInputBatch("invalid-" + String.valueOf(UUID.randomUUID()), 10);
      StorageItemBatchResponse foreign = (StorageItemBatchResponse)ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.ADMIN, this.inputResourceId2, false, (String)null).getFirst();
      double selectedBefore = this.batchAmount(selected.getBatchUuid());
      long countBefore = this.getProductionListSize();

      try {
         this.setBatchSelection(this.inputResourceId1, true);

         for(UUID invalid : List.of(UUID.randomUUID(), foreign.getBatchUuid())) {
            Response rejected = this.apiExecutor.execute(ApiEndpointDefinition.PRODUCTION_POST_CREATE, UserRole.OWNER_1, this.serialRequest((double)1.0F, invalid, (UUID)null), this.storageId);
            ((AbstractIntegerAssert)Assertions.assertThat(rejected.statusCode()).as("Invalid raw batch " + String.valueOf(invalid), new Object[0])).isBetween(400, 499);
         }

         Response overdraw = this.apiExecutor.execute(ApiEndpointDefinition.PRODUCTION_POST_CREATE, UserRole.OWNER_1, this.serialRequest((double)2.0F, selected.getBatchUuid(), (UUID)null), this.storageId);
         Assertions.assertThat(overdraw.statusCode()).isBetween(400, 499);
         Assertions.assertThat(this.batchAmount(selected.getBatchUuid())).isCloseTo(selectedBefore, Assertions.within(0.01));
         Assertions.assertThat(this.getProductionListSize()).isEqualTo(countBefore);
      } finally {
         this.setBatchSelection(this.inputResourceId1, false);
      }

   }

   @Test(
      priority = 18
   )
   @TestCaseId({"TC-MIB-024"})
   @Story("Invalid split of relocation and warehouse defect")
   public void relocationAndDefectRejectInvalidBatchBreakdowns() {
      StorageItemBatchResponse selected = this.seedNamedInputBatch("invalid-split-" + String.valueOf(UUID.randomUUID()), 20);
      double before = this.batchAmount(selected.getBatchUuid());
      Long destinationId = ConfigProvider.getOwner1StorageId();
      String foreignName = "foreign-location-" + String.valueOf(UUID.randomUUID());
      RelocationInputRequest destinationReceipt = RelocationDataFactory.buildReceiveRequest(RelocationStockSeeder.resolveSupplierStorageId(this.apiExecutor, UserRole.ADMIN), destinationId, this.inputResourceId1, (double)10.0F, foreignName);
      Assertions.assertThat(this.apiExecutor.executeRelocationReceive(destinationReceipt, UserRole.ADMIN).statusCode()).isBetween(200, 299);
      UUID foreignLocationUuid = ((StorageItemBatchResponse)ProductionStockAssertions.queryBatches(this.apiExecutor, destinationId, UserRole.ADMIN, this.inputResourceId1, false, foreignName).getFirst()).getBatchUuid();
      List<RelocationItemBatchRequest> invalidBatches = List.of(RelocationItemBatchRequest.builder().batchUuid(selected.getBatchUuid()).batchNumber(selected.getBatchNumber()).isProduced(false).amount(BigDecimal.valueOf(6L)).build(), RelocationItemBatchRequest.builder().batchUuid(selected.getBatchUuid()).batchNumber(selected.getBatchNumber()).isProduced(false).amount(BigDecimal.valueOf(8L)).build(), RelocationItemBatchRequest.builder().batchUuid(selected.getBatchUuid()).batchNumber(selected.getBatchNumber()).isProduced(false).amount(BigDecimal.ZERO).build(), RelocationItemBatchRequest.builder().batchUuid(selected.getBatchUuid()).batchNumber(selected.getBatchNumber()).isProduced(false).amount(BigDecimal.valueOf(-1L)).build(), RelocationItemBatchRequest.builder().batchUuid(UUID.randomUUID()).batchNumber(selected.getBatchNumber()).isProduced(false).amount(BigDecimal.valueOf(7L)).build(), RelocationItemBatchRequest.builder().batchUuid(foreignLocationUuid).batchNumber(foreignName).isProduced(false).amount(BigDecimal.valueOf(7L)).build());
      SoftAssertions checks = new SoftAssertions();

      try {
         this.setBatchSelection(this.inputResourceId1, true);

         for(RelocationItemBatchRequest invalid : invalidBatches) {
            RelocationOutputRequest transfer = RelocationDataFactory.buildSendWithBatches(this.storageId, destinationId, this.inputResourceId1, (double)7.0F, List.of(invalid));
            Response response = this.apiExecutor.execute(ApiEndpointDefinition.RELOCATION_POST_SEND, UserRole.OWNER_1, transfer);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
               this.apiExecutor.execute(ApiEndpointDefinition.RELOCATION_PUT_RESOLVE, UserRole.ADMIN, RelocationUpdateRequest.builder().state(RelocationState.CANCELLED).build(), response.jsonPath().getLong("id"), destinationId);
            }

            ((IntegerAssert)checks.assertThat(response.statusCode()).as("Transfer batch amount=" + String.valueOf(invalid.getAmount()), new Object[0])).isBetween(400, 499);
         }

         for(RelocationItemBatchRequest invalid : invalidBatches) {
            Response response = this.apiExecutor.executeDefectCreate(DefectDataFactory.buildStorageExplicitBatchesDefect(this.storageId, this.inputResourceId1, 7.0, List.of(DefectBatchItem.builder().batchUuid(invalid.getBatchUuid()).batchNumber(invalid.getBatchNumber()).isProduced(false).amount(invalid.getAmount()).build())), UserRole.OWNER_1);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
               this.apiExecutor.execute(ApiEndpointDefinition.DEFECT_DELETE, UserRole.OWNER_1, (Object)null, response.jsonPath().getLong("id"), this.storageId);
            }

            ((IntegerAssert)checks.assertThat(response.statusCode()).as("Defect batch amount=" + String.valueOf(invalid.getAmount()), new Object[0])).isBetween(400, 499);
         }

         ((DoubleAssert)checks.assertThat(this.batchAmount(selected.getBatchUuid())).as("Invalid transfers and defects leave the selected batch intact", new Object[0])).isCloseTo(before, Assertions.within(0.01));
         checks.assertAll();
      } finally {
         this.setBatchSelection(this.inputResourceId1, false);
      }

   }

   @Test(
      priority = 19
   )
   @TestCaseId({"TC-MIB-011"})
   @Story("Manual and FIFO resources in one production")
   public void mixedManualAndFifoInputsStayAtomic() {
      StorageItemBatchResponse selected = this.seedNamedInputBatch("mixed-" + String.valueOf(UUID.randomUUID()), 20);
      double before = this.batchAmount(selected.getBatchUuid());
      long countBefore = this.getProductionListSize();

      try {
         this.setBatchSelection(this.inputResourceId1, true);
         Response missingManual = this.apiExecutor.execute(ApiEndpointDefinition.PRODUCTION_POST_CREATE, UserRole.OWNER_1, this.serialRequest((double)1.0F, (UUID)null, (UUID)null), this.storageId);
         Assertions.assertThat(missingManual.statusCode()).isBetween(400, 499);
         Response explicitFifo = this.apiExecutor.execute(ApiEndpointDefinition.PRODUCTION_POST_CREATE, UserRole.OWNER_1, this.serialRequest((double)1.0F, selected.getBatchUuid(), ((StorageItemBatchResponse)ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.ADMIN, this.inputResourceId2, false, (String)null).getFirst()).getBatchUuid()), this.storageId);
         Assertions.assertThat(explicitFifo.statusCode()).isBetween(400, 499);
         Assertions.assertThat(this.batchAmount(selected.getBatchUuid())).isCloseTo(before, Assertions.within(0.01));
         Assertions.assertThat(this.getProductionListSize()).isEqualTo(countBefore);
         Response valid = this.apiExecutor.execute(ApiEndpointDefinition.PRODUCTION_POST_CREATE, UserRole.OWNER_1, this.serialRequest((double)1.0F, selected.getBatchUuid(), (UUID)null), this.storageId);
         Assertions.assertThat(valid.statusCode()).isEqualTo(200);
         Assertions.assertThat(this.batchAmount(selected.getBatchUuid())).isCloseTo(before - (double)10.0F, Assertions.within(0.01));
         Assertions.assertThat(this.getProductionListSize()).isEqualTo(countBefore + 1L);
      } finally {
         this.setBatchSelection(this.inputResourceId1, false);
      }

   }

   @Test(
      priority = 19
   )
   @TestCaseId({"TC-MIB-018"})
   @Story("A finished-product batch uses one raw batch per MANUAL resource")
   public void separateManualResourcesCanEachUseTheirOwnBatch() {
      StorageItemBatchResponse first = this.seedNamedInputBatch("two-manual-" + String.valueOf(UUID.randomUUID()), 20);
      StorageItemBatchResponse second = (StorageItemBatchResponse)ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.ADMIN, this.inputResourceId2, false, (String)null).stream().filter((batch) -> batch.getAmount() > (double)10.0F).findFirst().orElseThrow();
      double firstBefore = this.batchAmount(first.getBatchUuid());
      double secondBefore = ((StorageItemBatchResponse)ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.ADMIN, this.inputResourceId2, false, (String)null).stream().filter((batch) -> second.getBatchUuid().equals(batch.getBatchUuid())).findFirst().orElseThrow()).getAmount();

      try {
         this.setBatchSelection(this.inputResourceId1, true);
         this.setBatchSelection(this.inputResourceId2, true);
         Response created = this.apiExecutor.execute(ApiEndpointDefinition.PRODUCTION_POST_CREATE, UserRole.OWNER_1, this.serialRequest((double)1.0F, first.getBatchUuid(), second.getBatchUuid()), this.storageId);
         ((AbstractIntegerAssert)Assertions.assertThat(created.statusCode()).as(created.asString(), new Object[0])).isEqualTo(200);
         Assertions.assertThat(this.batchAmount(first.getBatchUuid())).isLessThan(firstBefore);
         double secondAfter = ((StorageItemBatchResponse)ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.ADMIN, this.inputResourceId2, false, (String)null).stream().filter((batch) -> second.getBatchUuid().equals(batch.getBatchUuid())).findFirst().orElseThrow()).getAmount();
         Assertions.assertThat(secondAfter).isLessThan(secondBefore);
      } finally {
         this.setBatchSelection(this.inputResourceId1, false);
         this.setBatchSelection(this.inputResourceId2, false);
      }

   }

   @Test(
      priority = 21
   )
   @TestCaseId({"TC-MIB-013"})
   @Story("Stale manual selection")
   public void staleBatchSelectionCannotOverdraw() {
      StorageItemBatchResponse selected = this.seedNamedInputBatch("stale-" + String.valueOf(UUID.randomUUID()), 20);
      long countBefore = this.getProductionListSize();

      try {
         this.setBatchSelection(this.inputResourceId1, true);
         ManufacturingListRequest stale = this.serialRequest((double)2.0F, selected.getBatchUuid(), (UUID)null);
         Response first = this.apiExecutor.execute(ApiEndpointDefinition.PRODUCTION_POST_CREATE, UserRole.OWNER_1, this.serialRequest((double)1.0F, selected.getBatchUuid(), (UUID)null), this.storageId);
         Assertions.assertThat(first.statusCode()).isEqualTo(200);
         Assertions.assertThat(this.batchAmount(selected.getBatchUuid())).isCloseTo((double)10.0F, Assertions.within(0.01));
         Response rejected = this.apiExecutor.execute(ApiEndpointDefinition.PRODUCTION_POST_CREATE, UserRole.OWNER_1, stale, this.storageId);
         Assertions.assertThat(rejected.statusCode()).isBetween(400, 499);
         Assertions.assertThat(this.batchAmount(selected.getBatchUuid())).isCloseTo((double)10.0F, Assertions.within(0.01));
         Assertions.assertThat(this.getProductionListSize()).isEqualTo(countBefore + 1L);
      } finally {
         this.setBatchSelection(this.inputResourceId1, false);
      }

   }

   @Test(
      priority = 22
   )
   @TestCaseId({"TC-MIB-014"})
   @Story("Mode changes affect only new operations")
   public void fifoManualFifoSwitchPreservesEarlierBatchDebits() {
      StorageItemBatchResponse selected = this.seedNamedInputBatch("mode-" + String.valueOf(UUID.randomUUID()), 30);
      double before = this.batchAmount(selected.getBatchUuid());
      Assertions.assertThat(this.apiExecutor.execute(ApiEndpointDefinition.PRODUCTION_POST_CREATE, UserRole.OWNER_1, this.serialRequest((double)1.0F, (UUID)null, (UUID)null), this.storageId).statusCode()).isEqualTo(200);
      Assertions.assertThat(this.batchAmount(selected.getBatchUuid())).isCloseTo(before, Assertions.within(0.01));

      try {
         this.setBatchSelection(this.inputResourceId1, true);
         Assertions.assertThat(this.apiExecutor.execute(ApiEndpointDefinition.PRODUCTION_POST_CREATE, UserRole.OWNER_1, this.serialRequest((double)1.0F, selected.getBatchUuid(), (UUID)null), this.storageId).statusCode()).isEqualTo(200);
         Assertions.assertThat(this.batchAmount(selected.getBatchUuid())).isCloseTo(before - (double)10.0F, Assertions.within(0.01));
      } finally {
         this.setBatchSelection(this.inputResourceId1, false);
      }

      Assertions.assertThat(this.apiExecutor.execute(ApiEndpointDefinition.PRODUCTION_POST_CREATE, UserRole.OWNER_1, this.serialRequest((double)1.0F, (UUID)null, (UUID)null), this.storageId).statusCode()).isEqualTo(200);
      Assertions.assertThat(this.batchAmount(selected.getBatchUuid())).isCloseTo(before - (double)10.0F, Assertions.within(0.01));
      Assertions.assertThat(this.selectedResourceIds(this.storageId)).doesNotContain(new Long[]{this.inputResourceId1});
   }

   @Test(
      priority = 23
   )
   @TestCaseId({"TC-MIB-012"})
   @Story("Repeated receipt with the same batch number keeps its UUID")
   public void repeatedBatchNumberKeepsStableUuidAndDebitsIt() {
      String sharedName = "same-name-" + String.valueOf(UUID.randomUUID());
      StorageItemBatchResponse first = this.seedNamedInputBatch(sharedName, 20);
      RelocationInputRequest repeat = RelocationDataFactory.buildReceiveRequest(RelocationStockSeeder.resolveSupplierStorageId(this.apiExecutor, UserRole.ADMIN), this.storageId, this.inputResourceId1, (double)20.0F, sharedName);
      Assertions.assertThat(this.apiExecutor.executeRelocationReceive(repeat, UserRole.ADMIN).statusCode()).isBetween(200, 299);
      List<StorageItemBatchResponse> matches = ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.OWNER_1, this.inputResourceId1, false, sharedName);
      ((ListAssert)Assertions.assertThat(matches).filteredOn((b) -> sharedName.equals(b.getBatchNumber()))).hasSize(1);
      Assertions.assertThat(((StorageItemBatchResponse)matches.getFirst()).getBatchUuid()).isEqualTo(first.getBatchUuid());
      double before = this.batchAmount(first.getBatchUuid());

      try {
         this.setBatchSelection(this.inputResourceId1, true);
         Response created = this.apiExecutor.execute(ApiEndpointDefinition.PRODUCTION_POST_CREATE, UserRole.OWNER_1, this.serialRequest((double)1.0F, first.getBatchUuid(), (UUID)null), this.storageId);
         Assertions.assertThat(created.statusCode()).isEqualTo(200);
         Assertions.assertThat(this.batchAmount(first.getBatchUuid())).isCloseTo(before - (double)10.0F, Assertions.within(0.01));
      } finally {
         this.setBatchSelection(this.inputResourceId1, false);
      }

   }

   @Test(
      priority = 24
   )
   @TestCaseId({"TC-MIB-015"})
   @Story("Editing a manual batch in non-series production")
   public void nonSeriesEditMovesDebitToNewBatchOnceAndRejectsInvalidEdit() {
      StorageItemBatchResponse first = this.seedNamedInputBatch("edit-a-" + String.valueOf(UUID.randomUUID()), 20);
      StorageItemBatchResponse second = this.seedNamedInputBatch("edit-b-" + String.valueOf(UUID.randomUUID()), 20);
      double firstBefore = this.batchAmount(first.getBatchUuid());
      double secondBefore = this.batchAmount(second.getBatchUuid());
      NonSeriesProductionRequest request = NonSeriesProductionDataFactory.buildInProgressRequest(this.storageId, this.inputResourceId1, (double)2.0F, (double)1.0F);
      ((NonSeriesProductionResourceUsageRequest)request.getResourceUsageList().getFirst()).setBatchUuid(first.getBatchUuid());
      Long createdId = null;

      try {
         this.setBatchSelection(this.inputResourceId1, true);
         Response created = this.apiExecutor.execute(ApiEndpointDefinition.NON_SERIES_PRODUCTION_POST_CREATE, UserRole.ADMIN, request);
         Assertions.assertThat(created.statusCode()).isEqualTo(200);
         createdId = created.jsonPath().getLong("id");
         Assertions.assertThat(this.batchAmount(first.getBatchUuid())).isCloseTo(firstBefore - (double)2.0F, Assertions.within(0.01));
         ((NonSeriesProductionResourceUsageRequest)request.getResourceUsageList().getFirst()).setBatchUuid(second.getBatchUuid());
         ((NonSeriesProductionResourceUsageRequest)request.getResourceUsageList().getFirst()).setAmount(BigDecimal.valueOf(3L));
         Response updated = this.apiExecutor.execute(ApiEndpointDefinition.NON_SERIES_PRODUCTION_PUT_UPDATE, UserRole.ADMIN, request, createdId);
         Assertions.assertThat(updated.statusCode()).isEqualTo(200);
         Assertions.assertThat(updated.jsonPath().getString("resourceUsageList[0].batchUuid")).isEqualTo(second.getBatchUuid().toString());
         Assertions.assertThat(this.batchAmount(first.getBatchUuid())).isCloseTo(firstBefore, Assertions.within(0.01));
         Assertions.assertThat(this.batchAmount(second.getBatchUuid())).isCloseTo(secondBefore - (double)3.0F, Assertions.within(0.01));
         ((NonSeriesProductionResourceUsageRequest)request.getResourceUsageList().getFirst()).setBatchUuid(UUID.randomUUID());
         Response rejected = this.apiExecutor.execute(ApiEndpointDefinition.NON_SERIES_PRODUCTION_PUT_UPDATE, UserRole.ADMIN, request, createdId);
         Assertions.assertThat(rejected.statusCode()).isBetween(400, 499);
         Assertions.assertThat(this.batchAmount(first.getBatchUuid())).isCloseTo(firstBefore, Assertions.within(0.01));
         Assertions.assertThat(this.batchAmount(second.getBatchUuid())).isCloseTo(secondBefore - (double)3.0F, Assertions.within(0.01));
         this.setBatchSelection(this.inputResourceId1, false);
         ((NonSeriesProductionResourceUsageRequest)request.getResourceUsageList().getFirst()).setBatchUuid((UUID)null);
         ((NonSeriesProductionResourceUsageRequest)request.getResourceUsageList().getFirst()).setAmount(BigDecimal.valueOf(4L));
         Response fifoEdit = this.apiExecutor.execute(ApiEndpointDefinition.NON_SERIES_PRODUCTION_PUT_UPDATE, UserRole.ADMIN, request, createdId);
         Assertions.assertThat(fifoEdit.statusCode()).isEqualTo(200);
         Assertions.assertThat(this.batchAmount(second.getBatchUuid())).isCloseTo(secondBefore, Assertions.within(0.01));
      } finally {
         try {
            if (createdId != null) {
               this.apiExecutor.execute(ApiEndpointDefinition.NON_SERIES_PRODUCTION_DELETE, UserRole.ADMIN, (Object)null, createdId, this.storageId);
            }
         } finally {
            this.setBatchSelection(this.inputResourceId1, false);
         }

      }

   }

   @Test(
      priority = 24
   )
   @TestCaseId({"TC-MIB-025"})
   @Story("Editing a warehouse defect with manual batches")
   public void warehouseDefectEditMovesDebitAndRejectsInvalidBatch() {
      StorageItemBatchResponse first = this.seedNamedInputBatch("def-edit-a-" + String.valueOf(UUID.randomUUID()), 20);
      StorageItemBatchResponse second = this.seedNamedInputBatch("def-edit-b-" + String.valueOf(UUID.randomUUID()), 20);
      double firstBefore = this.batchAmount(first.getBatchUuid());
      double secondBefore = this.batchAmount(second.getBatchUuid());
      Long defectId = null;

      try {
         this.setBatchSelection(this.inputResourceId1, true);
         DefectRequest request = DefectDataFactory.buildStorageExplicitBatchesDefect(this.storageId, this.inputResourceId1, 3.0, List.of(DefectDataFactory.batch(first.getBatchUuid(), first.getBatchNumber(), false, 3.0)));
         Response created = this.apiExecutor.executeDefectCreate(request, UserRole.OWNER_1);
         ((AbstractIntegerAssert)Assertions.assertThat(created.statusCode()).as("Defect create: %s", new Object[]{created.asString()})).isBetween(200, 299);
         defectId = created.jsonPath().getLong("id");
         Assertions.assertThat(this.batchAmount(first.getBatchUuid())).isCloseTo(firstBefore - (double)3.0F, Assertions.within(0.01));
         request.setAmount(BigDecimal.valueOf(4L));
         request.setDefectBatches(List.of(DefectDataFactory.batch(second.getBatchUuid(), second.getBatchNumber(), false, (double)4.0F)));
         Response edited = this.apiExecutor.executeDefectUpdate(defectId, request, UserRole.OWNER_1);
         Assertions.assertThat(edited.statusCode()).isBetween(200, 299);
         Assertions.assertThat(this.batchAmount(first.getBatchUuid())).isCloseTo(firstBefore, Assertions.within(0.01));
         Assertions.assertThat(this.batchAmount(second.getBatchUuid())).isCloseTo(secondBefore - (double)4.0F, Assertions.within(0.01));
         request.setDefectBatches(List.of(DefectDataFactory.batch(UUID.randomUUID(), "missing", false, (double)4.0F)));
         Response rejected = this.apiExecutor.executeDefectUpdate(defectId, request, UserRole.OWNER_1);
         Assertions.assertThat(rejected.statusCode()).isBetween(400, 499);
         Assertions.assertThat(this.batchAmount(second.getBatchUuid())).isCloseTo(secondBefore - (double)4.0F, Assertions.within(0.01));
      } finally {
         try {
            if (defectId != null) {
               this.apiExecutor.execute(ApiEndpointDefinition.DEFECT_DELETE, UserRole.OWNER_1, (Object)null, defectId, this.storageId);
            }
         } finally {
            this.setBatchSelection(this.inputResourceId1, false);
         }

      }

   }

   @Test(
      priority = 25
   )
   @TestCaseId({"TC-MIB-016"})
   @Story("Fractional quantity and exhausted batch")
   public void manualBatchKeepsFractionAndRejectsExhaustedSelection() {
      StorageItemBatchResponse batch = this.seedNamedInputBatch("fraction-" + String.valueOf(UUID.randomUUID()), 20);
      double before = this.batchAmount(batch.getBatchUuid());
      NonSeriesProductionRequest fractional = NonSeriesProductionDataFactory.buildInProgressRequest(this.storageId, this.inputResourceId1, (double)0.125F, (double)1.0F);
      ((NonSeriesProductionResourceUsageRequest)fractional.getResourceUsageList().getFirst()).setBatchUuid(batch.getBatchUuid());
      Long createdId = null;

      try {
         this.setBatchSelection(this.inputResourceId1, true);
         Response created = this.apiExecutor.execute(ApiEndpointDefinition.NON_SERIES_PRODUCTION_POST_CREATE, UserRole.ADMIN, fractional);
         Assertions.assertThat(created.statusCode()).isEqualTo(200);
         createdId = created.jsonPath().getLong("id");
         Assertions.assertThat(this.batchAmount(batch.getBatchUuid())).isCloseTo(before - (double)0.125F, Assertions.within(1.0E-5));
         NonSeriesProductionRequest overdraw = NonSeriesProductionDataFactory.buildInProgressRequest(this.storageId, this.inputResourceId1, before, (double)1.0F);
         ((NonSeriesProductionResourceUsageRequest)overdraw.getResourceUsageList().getFirst()).setBatchUuid(batch.getBatchUuid());
         Response rejected = this.apiExecutor.execute(ApiEndpointDefinition.NON_SERIES_PRODUCTION_POST_CREATE, UserRole.ADMIN, overdraw);
         Assertions.assertThat(rejected.statusCode()).isBetween(400, 499);
         Assertions.assertThat(this.batchAmount(batch.getBatchUuid())).isCloseTo(before - (double)0.125F, Assertions.within(1.0E-5));
      } finally {
         try {
            if (createdId != null) {
               this.apiExecutor.execute(ApiEndpointDefinition.NON_SERIES_PRODUCTION_DELETE, UserRole.ADMIN, (Object)null, createdId, this.storageId);
            }
         } finally {
            this.setBatchSelection(this.inputResourceId1, false);
         }

      }

   }

   @Test(priority = 30)
   @TestCaseId("TC-MIB-019")
   @Story("Direct relocation edit after FIFO to MANUAL switch")
   @Description("PUT send without batches is rejected before stock changes; an explicit original UUID edits the debit")
   public void relocationEditRequiresExplicitSenderBatchAfterModeSwitch() {
      seedNamedInputBatch("edit-send-" + UUID.randomUUID(), 20);
      Long destinationId = ConfigProvider.getOwner1StorageId();
      Response sendResponse = apiExecutor.execute(ApiEndpointDefinition.RELOCATION_POST_SEND,
              UserRole.OWNER_1,
              RelocationDataFactory.buildSendRequest(storageId, destinationId, inputResourceId1, 2.0));
      Assertions.assertThat(sendResponse.statusCode()).as(sendResponse.asString()).isBetween(200, 299);
      RelocationResponse sent = sendResponse.as(RelocationResponse.class);
      var original = sent.getItems().getFirst().getBatches().getFirst();
      UUID originalUuid = original.getBatchUuid();
      Assertions.assertThat(originalUuid).isNotNull();
      double afterSend = batchAmount(originalUuid);
      try {
         setBatchSelection(inputResourceId1, true);
         RelocationOutputEditRequest missing = RelocationDataFactory.buildSendEditRequest(
                 inputResourceId1, 3.0, "TC-MIB-019 missing batch").toBuilder()
                 .version(sent.getVersion()).build();
         Response rejected = apiExecutor.execute(ApiEndpointDefinition.RELOCATION_PUT_UPDATE_SEND,
                 UserRole.ADMIN, missing, sent.getId(), storageId);
         Assertions.assertThat(rejected.statusCode()).as(rejected.asString()).isBetween(400, 499);
         Assertions.assertThat(batchAmount(originalUuid)).isCloseTo(afterSend, Assertions.within(0.01));

         RelocationOutputEditRequest exact = missing.toBuilder()
                 .items(List.of(RelocationDataFactory.usageWithBatch(inputResourceId1, 3.0,
                         originalUuid, original.getBatchNumber(), Boolean.TRUE.equals(original.getIsProduced()))))
                 .build();
         Response accepted = apiExecutor.execute(ApiEndpointDefinition.RELOCATION_PUT_UPDATE_SEND,
                 UserRole.ADMIN, exact, sent.getId(), storageId);
         Assertions.assertThat(accepted.statusCode()).as(accepted.asString()).isBetween(200, 299);
         Assertions.assertThat(batchAmount(originalUuid)).isCloseTo(afterSend - 1.0, Assertions.within(0.01));
      } finally {
         setBatchSelection(inputResourceId1, false);
      }
   }

   @Test(priority = 31)
   @TestCaseId("TC-MIB-020")
   @Story("Direct STORAGE defect edit after FIFO to MANUAL switch")
   @Description("PUT defect requires the previously consumed batch UUID and leaves stock unchanged on rejection")
   public void fifoStorageDefectEditRequiresOriginalBatchUuidAfterModeSwitch() {
      DefectRequest fifo = DefectDataFactory.buildStorageFifoDefect(storageId, inputResourceId1, 2.0);
      Response created = apiExecutor.executeDefectCreate(fifo, UserRole.OWNER_1);
      Assertions.assertThat(created.statusCode()).as(created.asString()).isBetween(200, 299);
      long defectId = created.jsonPath().getLong("id");
      UUID originalUuid = UUID.fromString(created.jsonPath().getString("defectBatches[0].batchUuid"));
      String originalNumber = created.jsonPath().getString("defectBatches[0].batchNumber");
      double afterCreate = batchAmount(originalUuid);
      try {
         setBatchSelection(inputResourceId1, true);
         Response rejected = apiExecutor.executeDefectUpdate(defectId, fifo, UserRole.OWNER_1);
         Assertions.assertThat(rejected.statusCode()).as(rejected.asString()).isBetween(400, 499);
         Assertions.assertThat(batchAmount(originalUuid)).isCloseTo(afterCreate, Assertions.within(0.01));

         DefectRequest exact = fifo.toBuilder().isProduced(true).defectBatches(List.of(
                 DefectDataFactory.batch(originalUuid, originalNumber, false, 2.0))).build();
         Response accepted = apiExecutor.executeDefectUpdate(defectId, exact, UserRole.OWNER_1);
         Assertions.assertThat(accepted.statusCode()).as(accepted.asString()).isBetween(200, 299);
         Assertions.assertThat(accepted.jsonPath().getString("defectBatches[0].batchUuid"))
                 .isEqualTo(originalUuid.toString());
         Assertions.assertThat(batchAmount(originalUuid)).isCloseTo(afterCreate, Assertions.within(0.01));
      } finally {
         apiExecutor.execute(ApiEndpointDefinition.DEFECT_DELETE,
                 UserRole.ADMIN, null, defectId, storageId);
         setBatchSelection(inputResourceId1, false);
      }
   }

   @Test(priority = 32)
   @TestCaseId("TC-MIB-022")
   @Story("Manual relocation with two raw batches and return")
   @Description("A 2+3 split debits only the selected UUIDs; RETURNED restores each batch after cancellation")
   public void relocationSplitAcrossTwoBatchesRestoresOnReturn() {
      StorageItemBatchResponse first = seedNamedInputBatch("split-send-a-" + UUID.randomUUID(), 20);
      StorageItemBatchResponse second = seedNamedInputBatch("split-send-b-" + UUID.randomUUID(), 20);
      double firstBefore = batchAmount(first.getBatchUuid());
      double secondBefore = batchAmount(second.getBatchUuid());
      Long destinationId = ConfigProvider.getOwner1StorageId();
      List<RelocationItemBatchRequest> batches = List.of(
              RelocationItemBatchRequest.builder().batchUuid(first.getBatchUuid())
                      .batchNumber(first.getBatchNumber()).amount(BigDecimal.valueOf(2))
                      .isProduced(false).build(),
              RelocationItemBatchRequest.builder().batchUuid(second.getBatchUuid())
                      .batchNumber(second.getBatchNumber()).amount(BigDecimal.valueOf(3))
                      .isProduced(false).build());
      try {
         setBatchSelection(inputResourceId1, true);
         Response sent = apiExecutor.execute(ApiEndpointDefinition.RELOCATION_POST_SEND,
                 UserRole.OWNER_1, RelocationDataFactory.buildSendWithBatches(
                         storageId, destinationId, inputResourceId1, 5.0, batches));
         Assertions.assertThat(sent.statusCode()).as(sent.asString()).isBetween(200, 299);
         Assertions.assertThat(batchAmount(first.getBatchUuid())).isCloseTo(firstBefore - 2.0, Assertions.within(0.01));
         Assertions.assertThat(batchAmount(second.getBatchUuid())).isCloseTo(secondBefore - 3.0, Assertions.within(0.01));
         Response cancelled = apiExecutor.execute(ApiEndpointDefinition.RELOCATION_PUT_RESOLVE,
                 UserRole.ADMIN, RelocationUpdateRequest.builder().state(RelocationState.CANCELLED).build(),
                 sent.jsonPath().getLong("id"), destinationId);
         Assertions.assertThat(cancelled.statusCode()).as(cancelled.asString()).isBetween(200, 299);
         Assertions.assertThat(batchAmount(first.getBatchUuid())).isCloseTo(firstBefore - 2.0, Assertions.within(0.01));
         Assertions.assertThat(batchAmount(second.getBatchUuid())).isCloseTo(secondBefore - 3.0, Assertions.within(0.01));
         Response returned = apiExecutor.execute(ApiEndpointDefinition.RELOCATION_PUT_RESOLVE,
                 UserRole.ADMIN, RelocationUpdateRequest.builder().state(RelocationState.RETURNED).build(),
                 sent.jsonPath().getLong("id"), storageId);
         Assertions.assertThat(returned.statusCode()).as(returned.asString()).isBetween(200, 299);
         Assertions.assertThat(batchAmount(first.getBatchUuid())).isCloseTo(firstBefore, Assertions.within(0.01));
         Assertions.assertThat(batchAmount(second.getBatchUuid())).isCloseTo(secondBefore, Assertions.within(0.01));
      } finally {
         setBatchSelection(inputResourceId1, false);
      }
   }

   @Test(priority = 33)
   @TestCaseId("TC-MIB-023")
   @Story("Manual STORAGE defect with two raw batches and cancellation")
   @Description("A 2+3 defect split debits the named UUIDs and deletion restores each batch")
   public void storageDefectSplitAcrossTwoBatchesRestoresOnDelete() {
      StorageItemBatchResponse first = seedNamedInputBatch("split-def-a-" + UUID.randomUUID(), 20);
      StorageItemBatchResponse second = seedNamedInputBatch("split-def-b-" + UUID.randomUUID(), 20);
      double firstBefore = batchAmount(first.getBatchUuid());
      double secondBefore = batchAmount(second.getBatchUuid());
      Long defectId = null;
      try {
         setBatchSelection(inputResourceId1, true);
         DefectRequest request = DefectDataFactory.buildStorageExplicitBatchesDefect(
                 storageId, inputResourceId1, 5.0, List.of(
                 DefectDataFactory.batch(first.getBatchUuid(), first.getBatchNumber(), false, 2.0),
                 DefectDataFactory.batch(second.getBatchUuid(), second.getBatchNumber(), false, 3.0)));
         Response created = apiExecutor.executeDefectCreate(request, UserRole.OWNER_1);
         Assertions.assertThat(created.statusCode()).as(created.asString()).isBetween(200, 299);
         defectId = created.jsonPath().getLong("id");
         Assertions.assertThat(batchAmount(first.getBatchUuid())).isCloseTo(firstBefore - 2.0, Assertions.within(0.01));
         Assertions.assertThat(batchAmount(second.getBatchUuid())).isCloseTo(secondBefore - 3.0, Assertions.within(0.01));
         Response deleted = apiExecutor.execute(ApiEndpointDefinition.DEFECT_DELETE,
                 UserRole.OWNER_1, null, defectId, storageId);
         Assertions.assertThat(deleted.statusCode()).as(deleted.asString()).isBetween(200, 299);
         defectId = null;
         Assertions.assertThat(batchAmount(first.getBatchUuid())).isCloseTo(firstBefore, Assertions.within(0.01));
         Assertions.assertThat(batchAmount(second.getBatchUuid())).isCloseTo(secondBefore, Assertions.within(0.01));
      } finally {
         if (defectId != null) {
            apiExecutor.execute(ApiEndpointDefinition.DEFECT_DELETE,
                    UserRole.ADMIN, null, defectId, storageId);
         }
         setBatchSelection(inputResourceId1, false);
      }
   }

   private ManufacturingListRequest serialRequest(double amount, UUID manualBatch, UUID fifoBatch) {
      ManufacturingListRequest request = ProductionDataFactory.buildCreateRequest(this.techMap, amount);
      ((ResourceUsageRequest)((ManufacturingItemRequest)request.getItems().getFirst()).getInputs().stream().filter((i) -> this.inputResourceId1.equals(i.getResourceId())).findFirst().orElseThrow()).setBatchUuid(manualBatch);
      ((ResourceUsageRequest)((ManufacturingItemRequest)request.getItems().getFirst()).getInputs().stream().filter((i) -> this.inputResourceId2.equals(i.getResourceId())).findFirst().orElseThrow()).setBatchUuid(fifoBatch);
      return request;
   }

   private StorageItemBatchResponse seedNamedInputBatch(String name, int quantity) {
      Set<UUID> existing = (Set)ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.OWNER_1, this.inputResourceId1, false, name).stream().map(StorageItemBatchResponse::getBatchUuid).collect(Collectors.toSet());
      BigDecimal amount = BigDecimal.valueOf((long)quantity);
      RelocationInputRequest receipt = ((RelocationInputRequest.RelocationInputRequestBuilder)((RelocationInputRequest.RelocationInputRequestBuilder)((RelocationInputRequest.RelocationInputRequestBuilder)((RelocationInputRequest.RelocationInputRequestBuilder)((RelocationInputRequest.RelocationInputRequestBuilder)RelocationInputRequest.builder().senderId(RelocationStockSeeder.resolveSupplierStorageId(this.apiExecutor, UserRole.ADMIN))).recipientId(this.storageId)).date(LocalDate.now())).invoiceNumber(name).description("erp-auto-test manual batch selection")).items(List.of(ResourceUsageRequest.builder().resourceId(this.inputResourceId1).amount(amount).batches(List.of(RelocationItemBatchRequest.builder().batchNumber(name).amount(amount).isProduced(false).build())).build()))).build();
      Assertions.assertThat(this.apiExecutor.executeRelocationReceive(receipt, UserRole.ADMIN).statusCode()).isBetween(200, 299);
      StorageItemBatchResponse selected = (StorageItemBatchResponse)ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.OWNER_1, this.inputResourceId1, false, name).stream().filter((b) -> name.equals(b.getBatchNumber()) && !existing.contains(b.getBatchUuid())).findFirst().orElseThrow();
      Assertions.assertThat(selected.getBatchUuid()).isNotNull();
      return selected;
   }

   private StorageItemBatchResponse seedUnknownInputBatch() {
      BigDecimal amount = BigDecimal.TEN;
      RelocationInputRequest receipt = ((RelocationInputRequest.RelocationInputRequestBuilder)((RelocationInputRequest.RelocationInputRequestBuilder)((RelocationInputRequest.RelocationInputRequestBuilder)((RelocationInputRequest.RelocationInputRequestBuilder)((RelocationInputRequest.RelocationInputRequestBuilder)RelocationInputRequest.builder().senderId(RelocationStockSeeder.resolveSupplierStorageId(this.apiExecutor, UserRole.ADMIN))).recipientId(this.storageId)).date(LocalDate.now())).invoiceNumber("unknown-" + String.valueOf(UUID.randomUUID())).description("erp-auto-test unknown-origin stock")).items(List.of(ResourceUsageRequest.builder().resourceId(this.inputResourceId1).amount(amount).batches(List.of(RelocationItemBatchRequest.builder().amount(amount).isProduced(false).build())).build()))).build();
      Assertions.assertThat(this.apiExecutor.executeRelocationReceive(receipt, UserRole.ADMIN).statusCode()).isBetween(200, 299);
      return (StorageItemBatchResponse)ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.OWNER_1, this.inputResourceId1, false, (String)null).stream().filter((batch) -> "Партія невідомого походження".equals(batch.getBatchNumber()) && batch.getAmount() > (double)0.0F).findFirst().orElseThrow();
   }

   private double batchAmount(UUID batchUuid) {
      return ((StorageItemBatchResponse)ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.OWNER_1, this.inputResourceId1, false, (String)null).stream().filter((b) -> batchUuid.equals(b.getBatchUuid())).findFirst().orElseThrow()).getAmount();
   }

   private List<Long> selectedResourceIds(Long locationId) {
      Response response = this.apiExecutor.execute(ApiEndpointDefinition.RESOURCE_BATCH_SELECTION_GET, UserRole.ADMIN, (Object)null, locationId);
      Assertions.assertThat(response.statusCode()).isEqualTo(200);
      return response.jsonPath().getList("$", Long.class);
   }

   private void setBatchSelection(Long resourceId, boolean enabled) {
      Response response = this.apiExecutor.execute(ApiEndpointDefinition.RESOURCE_BATCH_SELECTION_PUT, UserRole.OWNER_1, new ResourceBatchSelectionRequest(enabled), this.storageId, resourceId);
      Assertions.assertThat(response.statusCode()).isBetween(200, 299);
   }

   private List<Long> resourceIdsByBatchSelection(boolean enabled, Long resourceId) {
      String exactName = (new ResourceFixture(this.testContext, this.apiExecutor)).getById(UserRole.ADMIN, resourceId).getName();
      Response response = this.apiExecutor.executeWithQueryParams(ApiEndpointDefinition.RESOURCE_GET_ALL, UserRole.OWNER_1, Map.of("storageId", this.storageId, "batchSelection", enabled, "name", exactName, "page", 0, "size", 100), new Object[0]);
      Assertions.assertThat(response.statusCode()).isEqualTo(200);
      List<Number> ids = response.jsonPath().getList("content.id", Number.class);
      return ids.stream().map(Number::longValue).toList();
   }

   private long getProductionListSize() {
      Response listResponse = this.apiExecutor.execute(ApiEndpointDefinition.PRODUCTION_GET_PAGE_BY_STORE_ID, UserRole.OWNER_1, String.valueOf(this.storageId));
      return DatabaseIntegrityValidator.extractPageTotalElements(listResponse);
   }

}
