package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.defect.DefectDataFactory;
import com.erp.data.factories.project_production.ProjectProductionDataFactory;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.enums.ProjectProductionState;
import com.erp.enums.ProjectProductionType;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProductionFixture;
import com.erp.fixtures.ProjectProductionFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.ShiftFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.models.request.DefectRequest;
import com.erp.models.request.ProjectProductionRequest;
import com.erp.models.request.ProjectProductionStageRequest;
import com.erp.models.request.RelocationInputRequest;
import com.erp.models.request.RelocationItemBatchRequest;
import com.erp.models.request.ResourceBatchSelectionRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.EquipmentCategoryResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.ResourceUsageResponse;
import com.erp.models.response.StorageItemBatchResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.pages.DefectFormPage;
import com.erp.pages.NonSeriesProductionFormPage;
import com.erp.pages.ProductionCreateFormPage;
import com.erp.pages.ProjectProductionFormPage;
import com.erp.pages.RelocationCreateOutputPage;
import com.erp.pages.ResourcesListPage;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.microsoft.playwright.Page;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.assertj.core.api.AbstractBooleanAssert;
import org.assertj.core.api.AbstractIntegerAssert;
import org.assertj.core.api.Assertions;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@Epic("Production")
@Feature("Manual input batch accounting UI")
public class ManualInputBatchAccountingUiTest extends BaseUITest {
   private StorageFixture storages;
   private ProductionFixture production;
   private ResourceFixture resources;
   private long storageId;
   private long inputId;
   private long fifoId;
   private long outputId;
   private TechnologicalMapResponse techMap;
   private long shiftId;
   private String inputName;
   private String fifoName;

   @BeforeClass(
      alwaysRun = true
   )
   public void baseTestClassSetup() {
      super.baseTestClassSetup();
      this.storages = new StorageFixture(this.testContext, this.apiExecutor);
      this.storageId = this.storages.createProductionStorage(ConfigProvider.getOwner1StorageId(), "ui-manual-batch-").getId();
      this.production = new ProductionFixture(this.testContext, this.apiExecutor);
      this.production.prepareContext(this.storageId);
      this.resources = new ResourceFixture(this.testContext, this.apiExecutor);
      List<Long> inputIds = (List)this.testContext.get(ContextKey.PRODUCTION_INPUT_RESOURCE_IDS);
      this.inputId = (Long)inputIds.get(0);
      this.fifoId = (Long)inputIds.get(1);
      this.outputId = (Long)this.testContext.get(ContextKey.PRODUCTION_OUTPUT_RESOURCE_ID);
      this.techMap = (TechnologicalMapResponse)this.testContext.get(ContextKey.PRODUCTION_TECH_MAP);
      ShiftFixture shifts = new ShiftFixture(this.testContext, this.apiExecutor);
      this.shiftId = shifts.create(UserRole.ADMIN, this.storageId, shifts.uniqueRequest("ui-manual-")).getId();
      this.inputName = this.resources.getById(UserRole.ADMIN, this.inputId).getName();
      this.fifoName = this.resources.getById(UserRole.ADMIN, this.fifoId).getName();
      this.injectSessionCookies(this.cachedSessionCookies(UserRole.ADMIN), this.sessionCookieDomain());
      this.injectWorkspaceView(UserRole.ADMIN, this.storageId);
   }

   @AfterClass(
      alwaysRun = true
   )
   public void cleanupManualBatchUiData() {
      try {
         if (this.storageId != 0L && this.inputId != 0L) {
            this.setSelection(this.inputId, false);
         }

         if (this.techMap != null) {
            this.production.getTechMapFixture().deactivateTechMap(UserRole.ADMIN, this.techMap.getId(), this.storageId);
         }

         if (this.shiftId != 0L) {
            (new ShiftFixture(this.testContext, this.apiExecutor)).deleteRaw(UserRole.ADMIN, this.shiftId, this.storageId);
         }

         if (this.storages != null) {
            this.storages.deactivateTrackedStorages(UserRole.ADMIN);
         }

         if (this.resources != null) {
            for(long id : new long[]{this.inputId, this.fifoId, this.outputId}) {
               if (id != 0L) {
                  this.resources.deactivate(UserRole.ADMIN, id);
               }
            }
         }
      } catch (RuntimeException e) {
         LoggerFactory.getLogger(this.getClass()).warn("Manual batch UI cleanup failed", e);
      }

   }

   @Test(
      priority = 1
   )
   @TestCaseId({"TC-MIB-002", "TC-MIB-003"})
   @Story("Switch and filter manual accounting")
   public void manualBatchToggleAndFilterHappyPath() {
      this.injectSessionCookies(this.cachedSessionCookies(UserRole.OWNER_1), this.sessionCookieDomain());
      this.injectWorkspaceView(UserRole.OWNER_1, this.storageId);

      try {
         ResourcesListPage list = (new ResourcesListPage(this.page)).open(this.storageId);
         list.searchByName(this.inputName);
         this.page.waitForCondition(() -> list.isResourceRowVisible(this.inputName));
         Assertions.assertThat(list.isResourceRowVisible(this.inputName)).isTrue();
         Assertions.assertThat(list.isManualBatchAccounting(this.inputName)).isFalse();
         list.setManualBatchAccounting(this.inputName, true);
         list.attachScreenshot("TC-MIB-002 — manual batch switch enabled");
         this.page.reload();
         list.waitForLoaded().searchByName(this.inputName);
         Assertions.assertThat(list.isManualBatchAccounting(this.inputName)).isTrue();
         list.selectBatchAccountingFilter("Обліковувати попартійно");
         Assertions.assertThat(list.isResourceRowVisible(this.inputName)).isTrue();
         list.selectBatchAccountingFilter("Не попартійно (FIFO)");
         Assertions.assertThat(list.isResourceRowVisible(this.inputName)).isFalse();
         list.searchByName(this.fifoName);
         this.page.waitForCondition(() -> list.isResourceRowVisible(this.fifoName));
         Assertions.assertThat(list.isResourceRowVisible(this.fifoName)).isTrue();
         list.selectBatchAccountingFilter("Усі способи обліку");
         list.searchByName(this.inputName);
         this.page.waitForCondition(() -> list.isResourceRowVisible(this.inputName));
         Assertions.assertThat(list.isResourceRowVisible(this.inputName)).isTrue();
         list.setManualBatchAccounting(this.inputName, false);
         Assertions.assertThat(list.isManualBatchAccounting(this.inputName)).isFalse();
      } finally {
         this.injectSessionCookies(this.cachedSessionCookies(UserRole.ADMIN), this.sessionCookieDomain());
         this.injectWorkspaceView(UserRole.ADMIN, this.storageId);
      }

   }

   @Test(
      priority = 2
   )
   @TestCaseId({"TC-MIB-UI-004"})
   @Story("Pick a raw batch in serial production UI")
   public void serialProductionPicksYoungerBatchInUiHappyPath() {
      String batchName = "ui-raw-" + String.valueOf(UUID.randomUUID());
      RelocationInputRequest receipt = RelocationDataFactory.buildReceiveRequest(RelocationStockSeeder.resolveSupplierStorageId(this.apiExecutor, UserRole.ADMIN), this.storageId, this.inputId, (double)20.0F, batchName);
      Assertions.assertThat(this.apiExecutor.executeRelocationReceive(receipt, UserRole.ADMIN).statusCode()).isBetween(200, 299);
      StorageItemBatchResponse batch = (StorageItemBatchResponse)ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.ADMIN, this.inputId, false, batchName).stream().filter((b) -> batchName.equals(b.getBatchNumber())).findFirst().orElseThrow();
      double before = batch.getAmount();
      this.setSelection(this.inputId, true);
      ProductionCreateFormPage form = (new ProductionCreateFormPage(this.page)).open();
      ResourceResponse output = this.resources.getById(UserRole.ADMIN, this.outputId);
      form.selectProduct(output.getName()).selectTechMap(this.techMap.getName()).fillAmount("1").fillBatchNumber("ui-product-" + String.valueOf(UUID.randomUUID()));
      this.page.waitForCondition(() -> form.hasRawInputBatchOption(this.inputName, batch.getBatchUuid()));
      Assertions.assertThat(form.hasRawInputBatchOption(this.inputName, batch.getBatchUuid())).isTrue();
      form.ensureShiftSelected();
      form.attachScreenshot("TC-MIB-UI-004 — production raw batch picker");
      ((AbstractBooleanAssert)Assertions.assertThat(form.isSubmitEnabled()).as("Manual raw batch is required in the UI", new Object[0])).isFalse();
      form.selectRawInputBatch(this.inputName, batch.getBatchUuid()).waitUntilSubmitEnabled().submitExpectSuccess();
      Assertions.assertThat(form.isOnProductionJournal()).isTrue();
      double consumed = ((ResourceUsageResponse)this.techMap.getInput().stream().filter((i) -> this.inputId == i.getResource().getId()).findFirst().orElseThrow()).getAmount();
      double after = ((StorageItemBatchResponse)ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.ADMIN, this.inputId, false, batchName).stream().filter((b) -> batch.getBatchUuid().equals(b.getBatchUuid())).findFirst().orElseThrow()).getAmount();
      Assertions.assertThat(after).isCloseTo(before - consumed, Assertions.within(0.01));
   }

   @Test(
      priority = 3
   )
   @TestCaseId({"TC-MIB-UI-007"})
   @Story("Pick a raw batch in relocation UI")
   public void relocationPicksYoungerBatchInUiHappyPath() {
      String batchName = "ui-transfer-" + String.valueOf(UUID.randomUUID());
      RelocationInputRequest receipt = RelocationDataFactory.buildReceiveRequest(RelocationStockSeeder.resolveSupplierStorageId(this.apiExecutor, UserRole.ADMIN), this.storageId, this.inputId, (double)20.0F, batchName);
      Assertions.assertThat(this.apiExecutor.executeRelocationReceive(receipt, UserRole.ADMIN).statusCode()).isBetween(200, 299);
      StorageItemBatchResponse batch = (StorageItemBatchResponse)ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.ADMIN, this.inputId, false, batchName).stream().filter((b) -> batchName.equals(b.getBatchNumber())).findFirst().orElseThrow();
      double before = batch.getAmount();
      this.setSelection(this.inputId, true);
      String recipientName = this.storages.getById(UserRole.ADMIN, ConfigProvider.getOwner1StorageId()).getName();
      this.page.navigate(ConfigProvider.getBaseUrl() + "/relocation/create-output");
      RelocationCreateOutputPage form = (new RelocationCreateOutputPage(this.page)).waitForLoaded();
      form.selectRecipientByLabel(recipientName).selectOutputResourceByName(this.inputName).fillOutputQuantity("3").waitForAvailableBatchChips(0).selectAvailableBatch(0, batchName).fillSelectedBatchAmount(batchName, "3").fillInvoiceIssuerDefaults().confirmSend();
      form.attachScreenshot("TC-MIB-UI-007 — relocation with selected batch");
      double after = ((StorageItemBatchResponse)ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.ADMIN, this.inputId, false, batchName).stream().filter((b) -> batch.getBatchUuid().equals(b.getBatchUuid())).findFirst().orElseThrow()).getAmount();
      Assertions.assertThat(after).isCloseTo(before - (double)3.0F, Assertions.within(0.01));
   }

   @Test(
      priority = 4
   )
   @TestCaseId({"TC-MIB-UI-008"})
   @Story("Pick a raw batch in warehouse defect UI")
   public void warehouseDefectPicksRawBatchInUiHappyPath() {
      String batchName = "ui-defect-" + String.valueOf(UUID.randomUUID());
      RelocationInputRequest receipt = RelocationDataFactory.buildReceiveRequest(RelocationStockSeeder.resolveSupplierStorageId(this.apiExecutor, UserRole.ADMIN), this.storageId, this.inputId, (double)20.0F, batchName);
      Assertions.assertThat(this.apiExecutor.executeRelocationReceive(receipt, UserRole.ADMIN).statusCode()).isBetween(200, 299);
      StorageItemBatchResponse batch = (StorageItemBatchResponse)ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.ADMIN, this.inputId, false, batchName).stream().filter((b) -> batchName.equals(b.getBatchNumber())).findFirst().orElseThrow();
      double before = batch.getAmount();
      this.setSelection(this.inputId, true);
      DefectFormPage form = (new DefectFormPage(this.page)).open().selectType("Склад").selectResourceByName(this.inputName).fillStorageBatchAmount(batchName, "3");
      form.attachScreenshot("TC-MIB-UI-008 — raw storage defect batch selected");
      form.submitAndWaitForList();
      double after = ((StorageItemBatchResponse)ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.ADMIN, this.inputId, false, batchName).stream().filter((b) -> batch.getBatchUuid().equals(b.getBatchUuid())).findFirst().orElseThrow()).getAmount();
      Assertions.assertThat(after).isCloseTo(before - (double)3.0F, Assertions.within(0.01));
   }

   @Test(
      priority = 4
   )
   @TestCaseId({"TC-MIB-UI-017"})
   @Story("Pick the existing unknown-origin batch in warehouse defect UI")
   public void warehouseDefectPicksUnknownBatchInUiHappyPath() {
      BigDecimal amount = BigDecimal.TEN;
      RelocationInputRequest receipt = ((RelocationInputRequest.RelocationInputRequestBuilder)((RelocationInputRequest.RelocationInputRequestBuilder)((RelocationInputRequest.RelocationInputRequestBuilder)((RelocationInputRequest.RelocationInputRequestBuilder)RelocationInputRequest.builder().senderId(RelocationStockSeeder.resolveSupplierStorageId(this.apiExecutor, UserRole.ADMIN))).recipientId(this.storageId)).date(LocalDate.now())).invoiceNumber("ui-unknown-" + String.valueOf(UUID.randomUUID())).items(List.of(ResourceUsageRequest.builder().resourceId(this.inputId).amount(amount).batches(List.of(RelocationItemBatchRequest.builder().amount(amount).isProduced(false).build())).build()))).build();
      Assertions.assertThat(this.apiExecutor.executeRelocationReceive(receipt, UserRole.ADMIN).statusCode()).isBetween(200, 299);
      StorageItemBatchResponse unknown = (StorageItemBatchResponse)ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.ADMIN, this.inputId, false, (String)null).stream().filter((batch) -> "UNKNOWN".equals(batch.getOrigin()) && batch.getAmount() > (double)0.0F).findFirst().orElseThrow();
      double before = unknown.getAmount();
      this.setSelection(this.inputId, true);
      DefectFormPage form = (new DefectFormPage(this.page)).open().selectType("Склад").selectResourceByName(this.inputName).fillStorageBatchAmount("Невідома партія", "3");
      form.attachScreenshot("TC-MIB-UI-017 — unknown storage batch selected");
      form.submitAndWaitForList();
      double after = ((StorageItemBatchResponse)ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.ADMIN, this.inputId, false, (String)null).stream().filter((batch) -> unknown.getBatchUuid().equals(batch.getBatchUuid())).findFirst().orElseThrow()).getAmount();
      Assertions.assertThat(after).isCloseTo(before - (double)3.0F, Assertions.within(0.01));
   }

   @Test(
      priority = 4
   )
   @TestCaseId({"TC-MIB-UI-017B"})
   @Story("Explain why the unknown batch cannot be selected without stock")
   public void warehouseDefectExplainsUnavailableUnknownBatchInUi() {
      this.setSelection(this.fifoId, true);

      try {
         DefectFormPage form = (new DefectFormPage(this.page)).open().selectType("Склад").selectResourceByName(this.fifoName);
         Page var10000 = this.page;
         Objects.requireNonNull(form);
         var10000.waitForCondition(form::isUnknownBatchUnavailableWarningVisible);
         form.attachScreenshot("TC-MIB-UI-017B — unknown batch unavailable warning");
         Assertions.assertThat(form.isUnknownBatchUnavailableWarningVisible()).isTrue();
      } finally {
         this.setSelection(this.fifoId, false);
      }

   }

   @Test(
      priority = 4
   )
   @TestCaseId({"TC-MIB-UI-015"})
   @Story("Edit a FIFO warehouse defect after switching the resource to MANUAL")
   public void fifoDefectEditRequiresVisibleBatchAfterManualSwitch() {
      this.setSelection(this.inputId, false);
      DefectRequest request = DefectDataFactory.buildStorageFifoDefect(this.storageId, this.inputId, (double)2.0F);
      Response created = this.apiExecutor.execute(ApiEndpointDefinition.DEFECT_POST_CREATE, UserRole.ADMIN, request);
      ((AbstractIntegerAssert)Assertions.assertThat(created.statusCode()).as(created.asString(), new Object[0])).isBetween(200, 299);
      long defectId = created.jsonPath().getLong("id");
      String originalUuid = created.jsonPath().getString("defectBatches[0].batchUuid");
      String originalNumber = created.jsonPath().getString("defectBatches[0].batchNumber");
      String label = "UNKNOWN".equals(created.jsonPath().getString("defectBatches[0].origin")) ? "Невідома партія" : originalNumber;
      Assertions.assertThat(originalUuid).isNotBlank();

      try {
         this.setSelection(this.inputId, true);
         DefectFormPage form = (new DefectFormPage(this.page)).openEdit(defectId).fillStorageBatchAmount(label, "2");
         form.attachScreenshot("TC-MIB-UI-015 — FIFO defect after manual switch");
         form.submitAndWaitForList();
         Response reloaded = this.apiExecutor.execute(ApiEndpointDefinition.DEFECT_GET_BY_ID, UserRole.ADMIN, (Object)null, defectId, this.storageId);
         Assertions.assertThat(reloaded.statusCode()).isEqualTo(200);
         Assertions.assertThat(reloaded.jsonPath().getString("defectBatches[0].batchUuid")).isEqualTo(originalUuid);
      } finally {
         this.apiExecutor.execute(ApiEndpointDefinition.DEFECT_DELETE, UserRole.ADMIN, (Object)null, defectId, this.storageId);
         this.setSelection(this.inputId, false);
      }

   }

   @Test(
      priority = 5
   )
   @TestCaseId({"TC-MIB-UI-005"})
   @Story("Pick a raw batch in non-series production UI")
   public void nonSeriesProductionPicksRawBatchInUiHappyPath() {
      String batchName = "ui-nsp-" + String.valueOf(UUID.randomUUID());
      RelocationInputRequest receipt = RelocationDataFactory.buildReceiveRequest(RelocationStockSeeder.resolveSupplierStorageId(this.apiExecutor, UserRole.ADMIN), this.storageId, this.inputId, (double)20.0F, batchName);
      Assertions.assertThat(this.apiExecutor.executeRelocationReceive(receipt, UserRole.ADMIN).statusCode()).isBetween(200, 299);
      StorageItemBatchResponse batch = (StorageItemBatchResponse)ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.ADMIN, this.inputId, false, batchName).stream().filter((b) -> batchName.equals(b.getBatchNumber())).findFirst().orElseThrow();
      double before = batch.getAmount();
      this.setSelection(this.inputId, true);
      NonSeriesProductionFormPage form = (new NonSeriesProductionFormPage(this.page)).openCreate().fillProductName("ui-nsp-product-" + String.valueOf(UUID.randomUUID())).setProductAmount(1).setWorkerQty(2).selectStatusInProgress().addResourceUsage(this.inputName, (double)2.0F).selectRawBatch(batch.getBatchUuid());
      form.attachScreenshot("TC-MIB-UI-005 — nonseries raw batch selected");
      form.saveProduct();
      double after = ((StorageItemBatchResponse)ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.ADMIN, this.inputId, false, batchName).stream().filter((b) -> batch.getBatchUuid().equals(b.getBatchUuid())).findFirst().orElseThrow()).getAmount();
      Assertions.assertThat(after).isCloseTo(before - (double)2.0F, Assertions.within(0.01));
   }

   @Test(
      priority = 6
   )
   @TestCaseId({"TC-MIB-UI-006"})
   @Story("Pick a raw batch for actual project stage usage in UI")
   public void projectStagePicksRawBatchInUiHappyPath() {
      String batchName = "ui-project-" + String.valueOf(UUID.randomUUID());
      RelocationInputRequest receipt = RelocationDataFactory.buildReceiveRequest(RelocationStockSeeder.resolveSupplierStorageId(this.apiExecutor, UserRole.ADMIN), this.storageId, this.inputId, (double)20.0F, batchName);
      Assertions.assertThat(this.apiExecutor.executeRelocationReceive(receipt, UserRole.ADMIN).statusCode()).isBetween(200, 299);
      StorageItemBatchResponse batch = (StorageItemBatchResponse)ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.ADMIN, this.inputId, false, batchName).stream().filter((b) -> batchName.equals(b.getBatchNumber())).findFirst().orElseThrow();
      double before = batch.getAmount();
      ProjectProductionFixture projects = new ProjectProductionFixture(this.testContext, this.apiExecutor);
      Long categoryId = ((EquipmentCategoryResponse)projects.getEquipmentCategories().getFirst()).getId();
      Long modelId = projects.getEquipmentModels().stream().findFirst().map((model) -> model.getId()).orElse(null);
      ProjectProductionRequest request = ProjectProductionDataFactory.buildCreateRequest(this.storageId, categoryId, modelId, ProjectProductionState.IN_PROGRESS, ProjectProductionType.CREATION, (List)null);
      if (modelId == null) {
         request.setEquipmentModelName(ProjectProductionDataFactory.uniqueModelName());
      }

      Long projectId = null;

      try {
         this.setSelection(this.inputId, true);
         projectId = projects.createAs(UserRole.ADMIN, request).getId();
         ProjectProductionStageRequest planned = ProjectProductionDataFactory.singleResourceStage(this.inputId, (double)3.0F, (double)0.0F);
         Assertions.assertThat(projects.addStageRaw(UserRole.ADMIN, projectId, this.storageId, planned).statusCode()).isBetween(200, 299);
         Assertions.assertThat(((StorageItemBatchResponse)ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.ADMIN, this.inputId, false, batchName).stream().filter((b) -> batch.getBatchUuid().equals(b.getBatchUuid())).findFirst().orElseThrow()).getAmount()).isCloseTo(before, Assertions.within(0.01));
         ProjectProductionFormPage form = (new ProjectProductionFormPage(this.page)).openEdit(projectId);
         form.attachScreenshot("TC-MIB-UI-006 — project stage raw batch picker");
         form.saveStageActualUsageFromBatch(1, "3", batch.getBatchUuid().toString());
         double after = ((StorageItemBatchResponse)ProductionStockAssertions.queryBatches(this.apiExecutor, this.storageId, UserRole.ADMIN, this.inputId, false, batchName).stream().filter((b) -> batch.getBatchUuid().equals(b.getBatchUuid())).findFirst().orElseThrow()).getAmount();
         Assertions.assertThat(after).isCloseTo(before - (double)3.0F, Assertions.within(0.01));
      } finally {
         try {
            if (projectId != null) {
               projects.deleteAs(UserRole.ADMIN, projectId, this.storageId, (List)null);
            }
         } finally {
            this.setSelection(this.inputId, false);
         }

      }

   }

   private void setSelection(long resourceId, boolean enabled) {
      Assertions.assertThat(this.apiExecutor.execute(ApiEndpointDefinition.RESOURCE_BATCH_SELECTION_PUT, UserRole.ADMIN, new ResourceBatchSelectionRequest(enabled), this.storageId, resourceId).statusCode()).isBetween(200, 299);
   }
}
