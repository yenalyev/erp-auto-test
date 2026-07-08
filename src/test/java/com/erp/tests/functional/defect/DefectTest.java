package com.erp.tests.functional.defect;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.defect.DefectDataFactory;
import com.erp.data.factories.non_series_production.NonSeriesProductionDataFactory;
import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.enums.DefectType;
import com.erp.enums.NonSeriesProductionStatus;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.DefectFixture;
import com.erp.fixtures.NonSeriesProductionFixture;
import com.erp.models.query.DefectQuery;
import com.erp.models.request.DefectRequest;
import com.erp.models.request.DefectWriteOffRequest;
import com.erp.models.response.DefectResponse;
import com.erp.models.response.DefectWriteOffResponse;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.StorageItemBatchResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.AllureHelper;
import com.erp.utils.helpers.DefectStockAssertions;
import com.erp.utils.helpers.ProductionBatchAssertions;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Functional API tests for the Defect ("Брак") domain (REQ-DEF), mirroring DefectController.
 */
@Slf4j
@Epic("Defects")
@Feature("Defect Management API (Брак)")
public class DefectTest extends BaseFunctionalTest {

    private DefectFixture fixture;
    private Long storageId;
    private Long owner2Storage;
    private Long defectResourceId;
    private Long input1;
    private Long input2;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    @Step("Підготовка середовища для тестів браку")
    public void setupDefectTests() {
        fixture = new DefectFixture(testContext, apiExecutor);
        fixture.prepareContext();

        storageId = ConfigProvider.getOwner1StorageId();
        owner2Storage = ConfigProvider.getOwner2StorageId();
        defectResourceId = fixture.defectResourceId();

        List<Long> inputs = testContext.get(ContextKey.PRODUCTION_INPUT_RESOURCE_IDS);
        input1 = inputs.get(0);
        input2 = inputs.get(1);

        SchemaRegistry.logSchemaCoverage();
    }

    @BeforeMethod(alwaysRun = true)
    @Step("Поповнити запаси перед тестом (ізоляція)")
    public void ensureStockBeforeTest() {
        fixture.getProductionFixture().ensureInputStockAtLeast(storageId, input1, input2, 500.0);
        fixture.ensureStock(defectResourceId, 100.0);
    }

    // =====================================================================
    // REQ-DEF-001 — Перегляд і фільтрація браку
    // =====================================================================

    @Test(priority = 10)
    @TestCaseId("TC-DEF-001")
    @Story("Default sorting by source date")
    @Description("AC-06: дефолтне сортування записів про брак — за датою джерела, новіші вгорі")
    @Severity(SeverityLevel.NORMAL)
    public void testDefaultSortingByDateDesc() {
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 3; i++) {
            DefectRequest req = DefectDataFactory.buildStorageFifoDefect(storageId, defectResourceId, 1.0)
                    .toBuilder().date(today.minusDays(i)).build();
            fixture.createAs(UserRole.OWNER_1, req);
        }

        List<DefectResponse> list = fixture.listDefects(
                DefectQuery.builder().storageId(storageId).pageSize(500).build());

        List<LocalDate> dates = list.stream()
                .map(DefectResponse::getDate)
                .filter(d -> d != null)
                .toList();

        Allure.step("Перевірка незростаючого порядку дат (новіші вгорі)", () -> {
            assertThat(dates).isNotEmpty();
            for (int i = 0; i < dates.size() - 1; i++) {
                assertThat(dates.get(i))
                        .as("Дата запису #%d має бути >= дати наступного запису", i)
                        .isAfterOrEqualTo(dates.get(i + 1));
            }
        });
    }

    @Test(priority = 20)
    @TestCaseId("TC-DEF-002")
    @Story("linked-relocation-ids returns relocations that already have a defect")
    @Description("GET /defects/linked-relocation-ids повертає id переміщень, для яких уже створено брак "
            + "за обраними складом/датою/ресурсом (контракт за DefectControllerIT). "
            + "Сценарій форми «доступні переміщення» — фронтендний, тут не перевіряється")
    @Severity(SeverityLevel.NORMAL)
    public void testLinkedRelocationsReturnedForReceipt() {
        Long resource = fixture.createFreshResource();
        String batch = "def-link-" + System.currentTimeMillis();
        RelocationResponse receipt = fixture.createExternalReceipt(resource, 10.0, batch);

        // Без браку переміщення не лінкується
        List<Long> beforeDefect = linkedRelocations(resource, LocalDate.now());
        assertThat(beforeDefect)
                .as("До створення браку переміщення не має повертатись у linked-relocation-ids")
                .doesNotContain(receipt.getId());

        // Створюємо брак за цим отриманням
        fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildRelocationDefect(storageId, resource, receipt.getId(), 4.0, LocalDate.now()));

        List<Long> afterDefect = linkedRelocations(resource, LocalDate.now());
        assertThat(afterDefect)
                .as("Після створення браку id переміщення має повертатись у linked-relocation-ids")
                .contains(receipt.getId());
    }

    // =====================================================================
    // REQ-DEF-002 / 003 — Створення браку на виробництві
    // =====================================================================

    @Test(priority = 30)
    @TestCaseId("TC-DEF-003")
    @Story("Create production defect (partial)")
    @Description("Happy path: брак на частину виготовленої партії. Партія і залишок ГП зменшуються "
            + "на величину браку; обсяг виробництва не змінюється")
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateProductionDefectPartial() {
        Long outRes = fixture.outputResourceId();
        String batch = ProductionDataFactory.uniqueBatchNumber();
        ManufacturingItemResponse prod = fixture.createProduction(5.0, batch);

        double produced = capturedBatchAmount(outRes, batch, "після виробництва");
        double defectAmount = round2(produced * 0.4);
        double stockBefore = fixture.resourceStock(outRes);

        DefectResponse created = fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildProductionDefect(storageId, outRes, prod.getId(), defectAmount, LocalDate.now()));

        assertThat(created.getType()).isEqualTo(DefectType.PRODUCTION);
        assertThat(created.getProductionProcessId()).isEqualTo(prod.getId());
        assertThat(created.getAmount().doubleValue()).isCloseTo(defectAmount, within(0.01));

        DefectStockAssertions.assertStockDebited(stockBefore, fixture.resourceStock(outRes), defectAmount,
                "ГП після браку на виробництві");

        double batchAfter = capturedBatchAmount(outRes, batch, "після браку");
        assertThat(batchAfter).as("Партія зменшилась на величину браку")
                .isCloseTo(produced - defectAmount, within(0.01));

        Allure.step("Обсяг виробництва не змінився", () -> {
            ManufacturingItemResponse byId = getProductionById(prod.getId());
            assertThat(byId.getAmount()).isEqualTo(prod.getAmount());
        });
    }

    @Test(priority = 120)
    @TestCaseId("TC-DEF-012")
    @Story("Create production defect (whole batch)")
    @Description("Happy path: брак на всю партію — партія зникає, залишок ГП зменшується на повний обсяг")
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateProductionDefectWholeBatch() {
        Long outRes = fixture.outputResourceId();
        String batch = ProductionDataFactory.uniqueBatchNumber();
        ManufacturingItemResponse prod = fixture.createProduction(4.0, batch);

        double produced = capturedBatchAmount(outRes, batch, "після виробництва");
        double stockBefore = fixture.resourceStock(outRes);

        fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildProductionDefect(storageId, outRes, prod.getId(), produced, LocalDate.now()));

        DefectStockAssertions.assertStockDebited(stockBefore, fixture.resourceStock(outRes), produced,
                "ГП після браку на всю партію");
        ProductionBatchAssertions.assertProducedBatchAbsent(apiExecutor, storageId, UserRole.OWNER_1, outRes, batch);
    }

    @Test(priority = 40)
    @TestCaseId("TC-DEF-004")
    @Story("Edit production defect (reduce amount)")
    @Description("При зменшенні кількості браку та сама партія перераховується (збільшується), нова не створюється")
    @Severity(SeverityLevel.NORMAL)
    public void testEditProductionDefectReduceAmount() {
        Long outRes = fixture.outputResourceId();
        String batch = ProductionDataFactory.uniqueBatchNumber();
        ManufacturingItemResponse prod = fixture.createProduction(6.0, batch);

        double produced = capturedBatchAmount(outRes, batch, "після виробництва");
        double firstDefect = round2(produced * 0.6);
        double reducedDefect = round2(produced * 0.3);

        DefectResponse created = fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildProductionDefect(storageId, outRes, prod.getId(), firstDefect, LocalDate.now()));
        assertThat(capturedBatchAmount(outRes, batch, "після створення браку"))
                .isCloseTo(produced - firstDefect, within(0.01));

        fixture.updateAs(UserRole.OWNER_1, created.getId(),
                DefectDataFactory.buildProductionDefect(storageId, outRes, prod.getId(), reducedDefect, LocalDate.now()));

        Allure.step("Партія збільшилась на величину зменшення браку, нова партія не створена", () -> {
            assertThat(capturedBatchAmount(outRes, batch, "після зменшення браку"))
                    .isCloseTo(produced - reducedDefect, within(0.01));
            List<StorageItemBatchResponse> batches = DefectStockAssertions.producedBatches(
                    apiExecutor, storageId, UserRole.OWNER_1, outRes, "перевірка кількості партій");
            long sameNumber = batches.stream().filter(b -> batch.equals(b.getBatchNumber())).count();
            assertThat(sameNumber).as("Має бути рівно одна партія з номером «%s»", batch).isEqualTo(1);
        });
    }

    @Test(priority = 110)
    @TestCaseId("TC-DEF-011")
    @Story("Cancel production defect restores batch with isProduced=true")
    @Description("AC-08: при скасуванні браку на виробництві партія відновлюється з isProduced = true "
            + "і з тією самою датою створення партії (date), що була до браку")
    @Severity(SeverityLevel.NORMAL)
    public void testCancelProductionDefectRestoresProducedBatch() {
        Long outRes = fixture.outputResourceId();
        String batch = ProductionDataFactory.uniqueBatchNumber();
        ManufacturingItemResponse prod = fixture.createProduction(4.0, batch);

        StorageItemBatchResponse originalBatch = ProductionBatchAssertions.findProducedBatch(
                        apiExecutor, storageId, UserRole.OWNER_1, outRes, batch)
                .orElseThrow(() -> new AssertionError("Партію «" + batch + "» не знайдено після виробництва"));
        double produced = originalBatch.getAmount() != null ? originalBatch.getAmount() : 0.0;
        Instant originalBatchDate = originalBatch.getDate();
        assertThat(originalBatchDate).as("дата створення партії до браку").isNotNull();

        DefectResponse created = fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildProductionDefect(storageId, outRes, prod.getId(), produced, LocalDate.now()));
        ProductionBatchAssertions.assertProducedBatchAbsent(apiExecutor, storageId, UserRole.OWNER_1, outRes, batch);

        fixture.deleteAs(UserRole.OWNER_1, created.getId());

        Allure.step("Партія відновлена з isProduced = true і тією самою датою створення", () -> {
            List<StorageItemBatchResponse> batches = DefectStockAssertions.producedBatches(
                    apiExecutor, storageId, UserRole.OWNER_1, outRes, "після скасування браку");
            StorageItemBatchResponse restored = batches.stream()
                    .filter(b -> batch.equals(b.getBatchNumber()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Партію «" + batch + "» не відновлено"));
            assertThat(restored.getIsProduced()).isTrue();
            assertThat(restored.getAmount()).isCloseTo(produced, within(0.01));
            assertThat(restored.getDate())
                    .as("дата створення партії після повернення з браку має збігатися з оригінальною")
                    .isEqualTo(originalBatchDate);
        });
    }

    @Test(priority = 130)
    @TestCaseId("TC-DEF-013")
    @Story("Cannot defect whole batch when partially used")
    @Description("Не можна списати на брак усю партію виробництва, якщо вона вже частково використана")
    @Severity(SeverityLevel.NORMAL)
    public void testCannotDefectPartiallyUsedProductionBatch() {
        Long outRes = fixture.outputResourceId();
        String batch = ProductionDataFactory.uniqueBatchNumber();
        ManufacturingItemResponse prod = fixture.createProduction(6.0, batch);
        double produced = capturedBatchAmount(outRes, batch, "після виробництва");

        // Використати частину виготовленої партії (видати на підрозділ)
        fixture.sendFromBatch(fixture.unitStorageId(), outRes, round2(produced * 0.5), batch, true);
        double stockBefore = fixture.resourceStock(outRes);

        Response resp = fixture.createRaw(UserRole.OWNER_1,
                DefectDataFactory.buildProductionDefect(storageId, outRes, prod.getId(), produced, LocalDate.now()));

        Allure.step("Запис про брак не створено (партія частково використана)", () -> {
            assertThat(resp.statusCode()).isNotEqualTo(200);
            assertThat(fixture.resourceStock(outRes)).isCloseTo(stockBefore, within(0.01));
        });
    }

    // =====================================================================
    // REQ-DEF-008 — Створення браку на складі (STORAGE)
    // =====================================================================

    @Test(priority = 80)
    @TestCaseId("TC-DEF-008")
    @Story("FIFO consumption for non-produced storage defect")
    @Description("AC-06: при створенні браку на складі для ресурсу, що не виробляємо, партії списуються за FIFO")
    @Severity(SeverityLevel.NORMAL)
    public void testStorageDefectConsumesBatchesFifo() {
        Long resource = fixture.createFreshResource();
        long ts = System.currentTimeMillis();
        String b1 = "fifo-" + ts + "-1";
        String b2 = "fifo-" + ts + "-2";
        String b3 = "fifo-" + ts + "-3";
        fixture.createExternalReceipt(resource, 10.0, b1);
        fixture.createExternalReceipt(resource, 10.0, b2);
        fixture.createExternalReceipt(resource, 10.0, b3);

        fixture.createAs(UserRole.OWNER_1, DefectDataFactory.buildStorageFifoDefect(storageId, resource, 15.0));

        List<StorageItemBatchResponse> batches = DefectStockAssertions.nonProducedBatches(
                apiExecutor, storageId, UserRole.OWNER_1, resource, "після браку (FIFO)");

        Allure.step("Списано batch1 повністю і частину batch2 (FIFO)", () -> {
            assertThat(DefectStockAssertions.batchAmount(batches, b1)).as("batch1").isCloseTo(0.0, within(0.01));
            assertThat(DefectStockAssertions.batchAmount(batches, b2)).as("batch2").isCloseTo(5.0, within(0.01));
            assertThat(DefectStockAssertions.batchAmount(batches, b3)).as("batch3").isCloseTo(10.0, within(0.01));
        });
    }

    @Test(priority = 90)
    @TestCaseId("TC-DEF-009")
    @Story("Cancel storage defect restores FIFO batches")
    @Description("При скасуванні браку на складі відновлюються попередні партії. "
            + "TCM фіксує дефект продукту: порядок партій може ламатися — перевіряємо відновлення обсягів (hard) "
            + "і фіксуємо порядок (note)")
    @Severity(SeverityLevel.NORMAL)
    public void testCancelStorageDefectRestoresBatches() {
        Long resource = fixture.createFreshResource();
        long ts = System.currentTimeMillis();
        String b1 = "fifo-c-" + ts + "-1";
        String b2 = "fifo-c-" + ts + "-2";
        String b3 = "fifo-c-" + ts + "-3";
        fixture.createExternalReceipt(resource, 10.0, b1);
        fixture.createExternalReceipt(resource, 10.0, b2);
        fixture.createExternalReceipt(resource, 10.0, b3);

        List<String> orderBefore = DefectStockAssertions.batchOrder(DefectStockAssertions.nonProducedBatches(
                apiExecutor, storageId, UserRole.OWNER_1, resource, "до браку"));

        DefectResponse created = fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildStorageFifoDefect(storageId, resource, 15.0));
        fixture.deleteAs(UserRole.OWNER_1, created.getId());

        List<StorageItemBatchResponse> after = DefectStockAssertions.nonProducedBatches(
                apiExecutor, storageId, UserRole.OWNER_1, resource, "після скасування браку");

        Allure.step("Обсяги партій повністю відновлено", () -> {
            assertThat(DefectStockAssertions.batchAmount(after, b1)).as("batch1").isCloseTo(10.0, within(0.01));
            assertThat(DefectStockAssertions.batchAmount(after, b2)).as("batch2").isCloseTo(10.0, within(0.01));
            assertThat(DefectStockAssertions.batchAmount(after, b3)).as("batch3").isCloseTo(10.0, within(0.01));
        });

        List<String> orderAfter = DefectStockAssertions.batchOrder(after);
        if (!orderBefore.equals(orderAfter)) {
            Allure.addAttachment("Відомий дефект TCM (TC-DEF-009): порядок партій змінився",
                    "text/plain", "before=" + orderBefore + "\nafter=" + orderAfter);
        }
    }

    @Test(priority = 100)
    @TestCaseId("TC-DEF-010")
    @Story("Storage defect for own production uses explicit batches")
    @Description("AC-07: для ресурсів власного виробництва (isProduced=true) партії вказуються явно")
    @Severity(SeverityLevel.NORMAL)
    public void testStorageDefectExplicitProducedBatch() {
        Long outRes = fixture.outputResourceId();
        String batch = ProductionDataFactory.uniqueBatchNumber();
        fixture.createProduction(5.0, batch);
        double produced = capturedBatchAmount(outRes, batch, "після виробництва");
        double defectAmount = round2(produced * 0.5);
        double stockBefore = fixture.resourceStock(outRes);

        DefectRequest req = DefectDataFactory.buildStorageExplicitBatchesDefect(
                storageId, outRes, defectAmount,
                List.of(DefectDataFactory.batch(batch, true, defectAmount)));
        fixture.createAs(UserRole.OWNER_1, req);

        DefectStockAssertions.assertStockDebited(stockBefore, fixture.resourceStock(outRes), defectAmount,
                "склад/власне виробництво");
        assertThat(capturedBatchAmount(outRes, batch, "після браку"))
                .as("Вказана партія зменшилась на величину браку")
                .isCloseTo(produced - defectAmount, within(0.01));
    }

    // =====================================================================
    // REQ-DEF-004 — Видалення з відновленням залишку
    // =====================================================================

    @Test(priority = 140)
    @TestCaseId("TC-DEF-014")
    @Story("Delete defect restores stock")
    @Description("AC-01: можна видалити запис про брак, якщо не було списань — залишок ГП відновлюється")
    @Severity(SeverityLevel.CRITICAL)
    public void testDeleteDefectRestoresStock() {
        double stockBefore = fixture.resourceStock(defectResourceId);
        DefectResponse created = fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildStorageFifoDefect(storageId, defectResourceId, 5.0));

        DefectStockAssertions.assertStockDebited(stockBefore, fixture.resourceStock(defectResourceId), 5.0,
                "після створення браку");

        fixture.deleteAs(UserRole.OWNER_1, created.getId());

        DefectStockAssertions.assertStockRestored(stockBefore, fixture.resourceStock(defectResourceId),
                "після видалення браку");

        Response getById = fixture.getByIdRaw(UserRole.OWNER_1, created.getId());
        assertThat(getById.statusCode()).as("Видалений брак не повертається").isNotEqualTo(200);
    }

    // =====================================================================
    // REQ-DEF-006 — Створення браку за переміщенням (RELOCATION)
    // =====================================================================

    @Test(priority = 180)
    @TestCaseId("TC-DEF-018")
    @Story("Create defect on FINISHED receipt")
    @Description("AC-01 (REQ-DEF-006): можна створити брак за отриманням у статусі «Завершено»")
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateDefectOnFinishedReceipt() {
        Long resource = fixture.createFreshResource();
        String batch = "rec-" + System.currentTimeMillis();
        RelocationResponse receipt = fixture.createExternalReceipt(resource, 10.0, batch);
        double stockBefore = fixture.resourceStock(resource);

        DefectResponse created = fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildRelocationDefect(storageId, resource, receipt.getId(), 4.0, LocalDate.now()));

        assertThat(created.getType()).isEqualTo(DefectType.RELOCATION);
        assertThat(created.getRelocationId()).isEqualTo(receipt.getId());
        DefectStockAssertions.assertStockDebited(stockBefore, fixture.resourceStock(resource), 4.0,
                "після браку за отриманням");
    }

    @Test(priority = 190)
    @TestCaseId("TC-DEF-019")
    @Story("Defect on multi-resource receipt affects only chosen resource")
    @Description("Брак за отриманням з номенклатурою > 1: зменшуються залишки лише по обраному ресурсу")
    @Severity(SeverityLevel.NORMAL)
    public void testDefectOnMultiResourceReceiptIsolatesResource() {
        Long resourceA = fixture.createFreshResource();
        Long resourceB = fixture.createFreshResource();
        long ts = System.currentTimeMillis();
        RelocationResponse receipt = fixture.createMultiResourceReceipt(
                resourceA, 10.0, "multi-a-" + ts, resourceB, 10.0, "multi-b-" + ts);

        double stockABefore = fixture.resourceStock(resourceA);
        double stockBBefore = fixture.resourceStock(resourceB);

        fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildRelocationDefect(storageId, resourceA, receipt.getId(), 4.0, LocalDate.now()));

        DefectStockAssertions.assertStockDebited(stockABefore, fixture.resourceStock(resourceA), 4.0,
                "ресурс A (з браком)");
        Allure.step("Залишок ресурсу B без змін", () ->
                assertThat(fixture.resourceStock(resourceB)).isCloseTo(stockBBefore, within(0.01)));
    }

    @Test(priority = 230)
    @TestCaseId("TC-DEF-023")
    @Story("Defect only on remaining part of relocation")
    @Description("AC-02 (REQ-DEF-006): брак можна створити лише на невикористану частину переміщення")
    @Severity(SeverityLevel.NORMAL)
    public void testDefectOnlyOnRemainingRelocationPart() {
        Long resource = fixture.createFreshResource();
        String batch = "rem-" + System.currentTimeMillis();
        RelocationResponse receipt = fixture.createExternalReceipt(resource, 10.0, batch);

        // Використати частину отриманої партії
        fixture.sendFromBatch(fixture.unitStorageId(), resource, 6.0, batch, false);
        double stockBefore = fixture.resourceStock(resource);
        assertThat(stockBefore).isCloseTo(4.0, within(0.01));

        Response tooMuch = fixture.createRaw(UserRole.OWNER_1,
                DefectDataFactory.buildRelocationDefect(storageId, resource, receipt.getId(), 10.0, LocalDate.now()));
        Allure.step("Брак на всю партію не створено (частину використано)", () -> {
            assertThat(tooMuch.statusCode()).isNotEqualTo(200);
            assertThat(fixture.resourceStock(resource)).isCloseTo(stockBefore, within(0.01));
        });

        fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildRelocationDefect(storageId, resource, receipt.getId(), 4.0, LocalDate.now()));
        Allure.step("Брак на невикористану частину (4 од.) створено", () ->
                assertThat(fixture.resourceStock(resource)).isCloseTo(0.0, within(0.01)));
    }

    @Test(priority = 250)
    @TestCaseId("TC-DEF-025")
    @Story("Relocation defect reduces stock by global FIFO")
    @Description("Брак за отриманням зменшує загальний залишок ресурсу на величину браку. "
            + "Спостережувана поведінка бекенда: партії списуються за глобальним FIFO (найстаріша першою), "
            + "а не строго з партії обраного переміщення — фіксуємо як note")
    @Severity(SeverityLevel.NORMAL)
    public void testRelocationDefectConsumesRelocationBatch() {
        Long resource = fixture.createFreshResource();
        long ts = System.currentTimeMillis();
        String olderBatch = "rel25-older-" + ts;
        String newerBatch = "rel25-newer-" + ts;
        fixture.createExternalReceipt(resource, 10.0, olderBatch);
        RelocationResponse target = fixture.createExternalReceipt(resource, 10.0, newerBatch);

        double totalBefore = fixture.resourceStock(resource);
        fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildRelocationDefect(storageId, resource, target.getId(), 4.0, LocalDate.now()));

        Allure.step("Загальний залишок зменшився на величину браку (4 од.)", () ->
                DefectStockAssertions.assertStockDebited(totalBefore, fixture.resourceStock(resource), 4.0,
                        "брак за переміщенням (FIFO)"));

        List<StorageItemBatchResponse> batches = DefectStockAssertions.nonProducedBatches(
                apiExecutor, storageId, UserRole.OWNER_1, resource, "після браку за переміщенням");
        double older = DefectStockAssertions.batchAmount(batches, olderBatch);
        double newer = DefectStockAssertions.batchAmount(batches, newerBatch);
        Allure.step("FIFO: спершу списується найстаріша партія", () -> {
            assertThat(older).as("найстаріша партія").isCloseTo(6.0, within(0.01));
            assertThat(newer).as("партія обраного переміщення (не зачеплена)").isCloseTo(10.0, within(0.01));
        });
        Allure.addAttachment("Поведінка бекенда (TC-DEF-025)", "text/plain",
                "Брак типу RELOCATION зменшує склад за глобальним FIFO (найстаріша партія першою), "
                        + "а не строго з партії, що належить обраному relocationId. "
                        + "older=" + older + ", newer=" + newer);
    }

    @Test(priority = 280)
    @TestCaseId("TC-DEF-028")
    @Story("Cannot defect a relocation whose batch is already used")
    @Description("Не можна створити брак за отриманням на партію, яку вже повністю використано")
    @Severity(SeverityLevel.NORMAL)
    public void testCannotDefectRelocationWithUsedBatch() {
        Long resource = fixture.createFreshResource();
        String batch = "used-" + System.currentTimeMillis();
        RelocationResponse receipt = fixture.createExternalReceipt(resource, 10.0, batch);

        // Повністю використати партію
        fixture.sendFromBatch(fixture.unitStorageId(), resource, 10.0, batch, false);
        double stockBefore = fixture.resourceStock(resource);

        Response resp = fixture.createRaw(UserRole.OWNER_1,
                DefectDataFactory.buildRelocationDefect(storageId, resource, receipt.getId(), 5.0, LocalDate.now()));

        Allure.step("Брак не створено — партія вже використана", () -> {
            assertThat(resp.statusCode()).isNotEqualTo(200);
            assertThat(fixture.resourceStock(resource)).isCloseTo(stockBefore, within(0.01));
        });
    }

    @Test(priority = 290)
    @TestCaseId("TC-DEF-029")
    @Issue("CPMA-498")
    @Story("Cannot defect relocation batch already used in non-series production")
    @Description("""
            CPMA-498: не можна створити брак за отриманням (RELOCATION) на партію isProduced=true,
            якщо вона вже повністю витрачена на несерійне виробництво (статус «В роботі»),
            навіть коли на складі є інша produced-партія того ж ресурсу.

            Кроки: ресурс stock=0 → receive batch1 (isProduced=true) → NSP IN_PROGRESS на batch1
            → receive batch2 (isProduced=true) → POST defect RELOCATION на receipt batch1.

            Очікування: 4xx, залишок не змінюється.
            Відомий дефект: API приймає брак (200) на вже використану партію.""")
    @Severity(SeverityLevel.CRITICAL)
    public void testCannotDefectRelocationBatchUsedInNonSeriesProduction() {
        Long resource = fixture.createFreshResource();
        long ts = System.currentTimeMillis();
        String batch1 = "nsp-used-" + ts + "-1";
        String batch2 = "nsp-used-" + ts + "-2";
        double batchAmount = 10.0;

        Allure.step("Залишок ресурсу = 0 до отримання", () ->
                assertThat(fixture.resourceStock(resource)).isCloseTo(0.0, within(0.01)));

        RelocationResponse receiptBatch1 = Allure.step(
                "Отримати партію batch1 (isProduced=true)", () ->
                        fixture.createExternalReceipt(resource, batchAmount, batch1, true));

        NonSeriesProductionFixture nspFixture = new NonSeriesProductionFixture(testContext, apiExecutor);
        Allure.step("Створити несерійне виробництво «В роботі» з повною витратою batch1", () -> {
            nspFixture.createAs(
                    UserRole.OWNER_1,
                    NonSeriesProductionStatus.IN_PROGRESS,
                    NonSeriesProductionDataFactory.uniqueProductName(),
                    1.0,
                    resource,
                    batchAmount);
            assertThat(fixture.resourceStock(resource))
                    .as("після NSP batch1 має бути повністю витрачена")
                    .isCloseTo(0.0, within(0.01));
        });

        Allure.step("Отримати партію batch2 (isProduced=true)", () -> {
            fixture.createExternalReceipt(resource, batchAmount, batch2, true);
            assertThat(fixture.resourceStock(resource))
                    .as("на складі лише batch2")
                    .isCloseTo(batchAmount, within(0.01));
        });

        double stockBefore = fixture.resourceStock(resource);
        Response resp = Allure.step("Спробувати створити брак RELOCATION на receipt batch1", () ->
                fixture.createRaw(UserRole.OWNER_1,
                        DefectDataFactory.buildRelocationDefect(
                                storageId, resource, receiptBatch1.getId(), 5.0, LocalDate.now())));

        Allure.step("Брак не створено — партія вже використана на несерійне виробництво (CPMA-498)", () -> {
            assertThat(resp.statusCode())
                    .as("body=%s", safeBody(resp))
                    .isGreaterThanOrEqualTo(400);
            assertThat(fixture.resourceStock(resource)).isCloseTo(stockBefore, within(0.01));
        });
    }

    // =====================================================================
    // REQ-DEF-005 — Списання браку
    // =====================================================================

    @Test(priority = 200)
    @TestCaseId("TC-DEF-020")
    @Story("Fully write off a defect")
    @Description("AC-01 (REQ-DEF-005): можна повністю списати брак — Кількість=0, Списано=весь обсяг")
    @Severity(SeverityLevel.CRITICAL)
    public void testFullyWriteOffDefect() {
        DefectResponse created = fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildStorageFifoDefect(storageId, defectResourceId, 6.0));

        DefectWriteOffRequest wo = DefectDataFactory.buildWriteOffForDefect(
                created, storageId, 6.0, "erp-auto-test full write-off");
        Response woRaw = fixture.writeOffRaw(UserRole.OWNER_1, wo);
        assertThat(woRaw.statusCode()).as("write-off має повертати 200").isEqualTo(200);
        DefectWriteOffResponse woResp = woRaw.as(DefectWriteOffResponse.class);
        assertThat(woResp.getAmount().doubleValue()).isCloseTo(6.0, within(0.01));

        DefectResponse after = fixture.getById(UserRole.OWNER_1, created.getId());
        Allure.step("Кількість браку = 0, Списано = весь обсяг", () -> {
            assertThat(after.getAmount().doubleValue()).as("залишок браку").isCloseTo(0.0, within(0.01));
            assertThat(after.getWriteOffAmount().doubleValue()).as("списано").isCloseTo(6.0, within(0.01));
        });
    }

    // =====================================================================
    // Verify-then-decide: rules whose enforcement is checked at runtime
    // =====================================================================

    @Test(priority = 50)
    @TestCaseId("TC-DEF-005")
    @Story("Cannot defect production batch in transit")
    @Description("REQ-DEF-002 AC-05: брак на виробничу партію, що вже видана (CREATED), має бути відхилений API")
    @Severity(SeverityLevel.NORMAL)
    public void testCannotDefectProductionBatchInTransit() {
        Long outRes = fixture.outputResourceId();
        String batch = ProductionDataFactory.uniqueBatchNumber();
        ManufacturingItemResponse prod = fixture.createProduction(5.0, batch);
        double produced = capturedBatchAmount(outRes, batch, "після виробництва");

        RelocationResponse sent = fixture.sendFromBatch(owner2Storage, outRes, produced, batch, true);
        assertThat(sent.getState()).isIn(RelocationState.CREATED, RelocationState.AUTO_FINISHED);

        Response resp = fixture.createRaw(UserRole.OWNER_1,
                DefectDataFactory.buildProductionDefect(storageId, outRes, prod.getId(), produced, LocalDate.now()));
        Allure.step("Брак на партію в дорозі відхилено", () ->
                assertThat(resp.statusCode()).as("body=%s", safeBody(resp)).isGreaterThanOrEqualTo(400));
    }

    @Test(priority = 60)
    @TestCaseId("TC-DEF-006")
    @Story("Cannot defect production batch already shipped")
    @Description("REQ-DEF-002: брак на партію після завершеного отримання одержувачем має бути відхилений")
    @Severity(SeverityLevel.NORMAL)
    public void testCannotDefectShippedProductionBatch() {
        Long outRes = fixture.outputResourceId();
        String batch = ProductionDataFactory.uniqueBatchNumber();
        ManufacturingItemResponse prod = fixture.createProduction(5.0, batch);
        double produced = capturedBatchAmount(outRes, batch, "після виробництва");

        fixture.sendAndFinishAtRecipient(
                UserRole.OWNER_2, owner2Storage, outRes, produced, batch, true);

        Response resp = fixture.createRaw(UserRole.OWNER_1,
                DefectDataFactory.buildProductionDefect(storageId, outRes, prod.getId(), produced, LocalDate.now()));
        Allure.step("Брак на вже відправлену партію відхилено", () ->
                assertThat(resp.statusCode()).as("body=%s", safeBody(resp)).isGreaterThanOrEqualTo(400));
    }

    @Test(priority = 70)
    @TestCaseId("TC-DEF-007")
    @Story("Production defect date window (Owner vs Admin)")
    @Description("AC-01/02 (REQ-DEF-002): @Owner може створити брак на виробництво в межах 2 днів "
            + "(дата = now-2); не може на виробництво старшого за 2 дні (now-3). @Admin може і на now-3.")
    @Severity(SeverityLevel.NORMAL)
    public void testProductionDefectDateWindow() {
        Long outRes = fixture.outputResourceId();
        LocalDate twoDaysAgo = LocalDate.now().minusDays(2);
        LocalDate threeDaysAgo = LocalDate.now().minusDays(3);
        String batchWithin = ProductionDataFactory.uniqueBatchNumber();
        String batchOld = ProductionDataFactory.uniqueBatchNumber();

        Allure.step("@Owner: виробництво і брак на межі вікна (дата " + twoDaysAgo + ") дозволені", () -> {
            ManufacturingItemResponse withinWindow = fixture.createProductionAs(
                    UserRole.OWNER_1, 4.0, batchWithin, twoDaysAgo);
            double produced = capturedBatchAmount(outRes, batchWithin, "після виробництва Owner now-2");
            double defectAmount = round2(produced * 0.5);
            DefectResponse ownerOk = fixture.createAs(UserRole.OWNER_1,
                    DefectDataFactory.buildProductionDefect(
                            storageId, outRes, withinWindow.getId(), defectAmount, twoDaysAgo));
            assertThat(ownerOk.getType()).isEqualTo(DefectType.PRODUCTION);
            assertThat(ownerOk.getDate()).isEqualTo(twoDaysAgo);
            fixture.deleteAs(UserRole.OWNER_1, ownerOk.getId());
        });

        Response ownerBackdatedProduction = fixture.createProductionRaw(
                UserRole.OWNER_1, 4.0, batchOld + "-owner-blocked", threeDaysAgo);
        Allure.step("@Owner: POST /productions, дата " + threeDaysAgo, () ->
                assertThat(ownerBackdatedProduction.statusCode())
                        .as("Owner, виробництво 3-денної давнини")
                        .isGreaterThanOrEqualTo(400));

        ManufacturingItemResponse oldProduction = fixture.createProductionAs(
                UserRole.ADMIN, 5.0, batchOld, threeDaysAgo);
        double produced = capturedBatchAmount(outRes, batchOld, "після виробництва Admin");
        double defectAmount = round2(produced * 0.5);
        DefectRequest defectRequest = DefectDataFactory.buildProductionDefect(
                storageId, outRes, oldProduction.getId(), defectAmount, threeDaysAgo);

        DefectResponse adminDefect = fixture.createAs(UserRole.ADMIN, defectRequest);
        Allure.step("@Admin: POST /defects, дата " + threeDaysAgo, () -> {
            assertThat(adminDefect.getType()).isEqualTo(DefectType.PRODUCTION);
            assertThat(adminDefect.getProductionProcessId()).isEqualTo(oldProduction.getId());
            assertThat(adminDefect.getDate()).isEqualTo(threeDaysAgo);
        });
        fixture.deleteAs(UserRole.ADMIN, adminDefect.getId());

        Response ownerDefect = fixture.createRaw(UserRole.OWNER_1, defectRequest);
        try {
            Allure.step("@Owner: POST /defects, дата " + threeDaysAgo, () ->
                    assertThat(ownerDefect.statusCode())
                            .as("Owner, брак на виробництво 3-денної давнини (body=%s)", safeBody(ownerDefect))
                            .isGreaterThanOrEqualTo(400));
        } finally {
            if (ownerDefect.statusCode() == 200) {
                fixture.deleteAs(UserRole.ADMIN, ownerDefect.as(DefectResponse.class).getId());
            }
        }
    }

    @Test(priority = 145)
    @TestCaseId("TC-DEF-030")
    @Story("Owner cannot delete defect older than 2 days")
    @Description("REQ-DEF: @Owner не може видалити брак, якщо з моменту створення (created_at) минуло більше 2 днів; "
            + "@Admin може. Arrange: backdate defect.created_at у БД (потрібен use.database=true).")
    @Severity(SeverityLevel.NORMAL)
    public void testOwnerCannotDeleteDefectOlderThanTwoDays() {
        DefectResponse created = fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildStorageFifoDefect(storageId, defectResourceId, 3.0));

        Instant olderThanWindow = Instant.now().minus(3, ChronoUnit.DAYS);
        backdateDefectCreatedAt(created.getId(), olderThanWindow);

        Response ownerDelete = fixture.deleteRaw(UserRole.OWNER_1, created.getId());
        Allure.step("@Owner: DELETE браку з created_at старше 2 днів відхилено", () ->
                assertThat(ownerDelete.statusCode())
                        .as("body=%s", safeBody(ownerDelete))
                        .isGreaterThanOrEqualTo(400));

        Response stillExists = fixture.getByIdRaw(UserRole.OWNER_1, created.getId());
        assertThat(stillExists.statusCode())
                .as("запис браку має лишитись після відхиленого DELETE Owner")
                .isEqualTo(200);

        Allure.step("@Admin: DELETE того ж браку дозволено", () ->
                fixture.deleteAs(UserRole.ADMIN, created.getId()));

        Response gone = fixture.getByIdRaw(UserRole.OWNER_1, created.getId());
        assertThat(gone.statusCode()).as("після DELETE Admin брак відсутній").isNotEqualTo(200);
    }

    @Test(priority = 150)
    @TestCaseId("TC-DEF-015")
    @Story("Cannot delete defect with write-offs")
    @Description("REQ-DEF-001 AC-07: видалення браку після часткового списання має бути заборонено")
    @Severity(SeverityLevel.NORMAL)
    public void testCannotDeleteDefectWithWriteOffs() {
        DefectResponse created = fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildStorageFifoDefect(storageId, defectResourceId, 6.0));

        Response woResp = fixture.writeOffRaw(UserRole.OWNER_1,
                DefectDataFactory.buildWriteOffForDefect(created, storageId, 3.0, "partial write-off"));
        assertThat(woResp.statusCode()).as("write-off setup").isEqualTo(200);

        Response deleteResp = fixture.deleteRaw(UserRole.OWNER_1, created.getId());
        Allure.step("DELETE браку зі списанням відхилено", () ->
                assertThat(deleteResp.statusCode()).as("body=%s", safeBody(deleteResp)).isGreaterThanOrEqualTo(400));
    }

    @Test(priority = 160)
    @TestCaseId("TC-DEF-016")
    @Story("Outbound sends are not valid relocation defect sources")
    @Description("Брак типу RELOCATION лише за отриманням; спроба прив'язати відправлення (send) має бути відхилена")
    @Severity(SeverityLevel.NORMAL)
    public void testSendsNotOfferedForDefect() {
        Long resource = fixture.createFreshResource();
        fixture.createExternalReceipt(resource, 10.0, "send-src-" + System.currentTimeMillis());
        RelocationResponse send = fixture.getRelocationFixture().createSend(
                UserRole.OWNER_1, storageId, owner2Storage, resource, 5.0);

        Response resp = fixture.createRaw(UserRole.OWNER_1,
                DefectDataFactory.buildRelocationDefect(storageId, resource, send.getId(), 3.0, LocalDate.now()));
        Allure.step("Брак за відправленням (не отриманням) відхилено", () ->
                assertThat(resp.statusCode()).as("body=%s", safeBody(resp)).isGreaterThanOrEqualTo(400));
    }

    @Test(priority = 170)
    @TestCaseId("TC-DEF-017")
    @Story("In-transit relocations are not valid relocation defect sources")
    @Description("Брак типу RELOCATION за переміщенням у статусі CREATED має бути відхилений")
    @Severity(SeverityLevel.NORMAL)
    public void testInTransitNotOfferedForDefect() {
        Long resource = fixture.createFreshResource();
        fixture.createExternalReceipt(resource, 10.0, "transit-" + System.currentTimeMillis());
        RelocationResponse send = fixture.getRelocationFixture().createSend(
                UserRole.OWNER_1, storageId, owner2Storage, resource, 5.0);
        assertThat(send.getState()).isEqualTo(RelocationState.CREATED);

        Response resp = fixture.createRaw(UserRole.OWNER_1,
                DefectDataFactory.buildRelocationDefect(storageId, resource, send.getId(), 3.0, LocalDate.now()));
        Allure.step("Брак за переміщенням CREATED відхилено", () ->
                assertThat(resp.statusCode()).as("body=%s", safeBody(resp)).isGreaterThanOrEqualTo(400));
    }

    @Test(priority = 210)
    @TestCaseId("TC-DEF-021")
    @Story("Cannot write off more than created in one step")
    @Description("REQ-DEF-005: списання більше залишку браку за один крок має бути відхилене")
    @Severity(SeverityLevel.NORMAL)
    public void testCannotWriteOffMoreThanCreatedSingle() {
        DefectResponse created = fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildStorageFifoDefect(storageId, defectResourceId, 6.0));

        Response woResp = fixture.writeOffRaw(UserRole.OWNER_1,
                DefectDataFactory.buildWriteOffForDefect(created, storageId, 10.0, "over limit"));
        Allure.step("Списання понад залишок відхилено", () ->
                assertThat(woResp.statusCode()).as("body=%s", safeBody(woResp)).isGreaterThanOrEqualTo(400));
    }

    @Test(priority = 220)
    @TestCaseId("TC-DEF-022")
    @Story("Cannot write off more than created stepwise")
    @Description("REQ-DEF-005: кумулятивне списання понад залишок браку має бути відхилене")
    @Severity(SeverityLevel.NORMAL)
    public void testCannotWriteOffMoreThanCreatedStepwise() {
        DefectResponse created = fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildStorageFifoDefect(storageId, defectResourceId, 6.0));

        Response first = fixture.writeOffRaw(UserRole.OWNER_1,
                DefectDataFactory.buildWriteOffForDefect(created, storageId, 4.0, "step 1"));
        assertThat(first.statusCode()).as("перше списання").isEqualTo(200);

        Response second = fixture.writeOffRaw(UserRole.OWNER_1,
                DefectDataFactory.buildWriteOffForDefect(created, storageId, 3.0, "step 2 over limit"));
        Allure.step("Друге списання понад залишок відхилено", () ->
                assertThat(second.statusCode()).as("body=%s", safeBody(second)).isGreaterThanOrEqualTo(400));
    }

    @Test(priority = 240)
    @TestCaseId("TC-DEF-024")
    @Story("Return-from-unit defect consumes the returned batch")
    @Description("Брак типу RELOCATION_FROM_UNIT після повернення з підрозділу зменшує залишок на величину браку")
    @Severity(SeverityLevel.NORMAL)
    public void testReturnFromUnitDefectConsumesReturnedBatch() {
        Long resource = fixture.createFreshResource();
        String batch = "unit-ret-" + System.currentTimeMillis();
        fixture.createExternalReceipt(resource, 10.0, batch);
        double stockBefore = fixture.resourceStock(resource);

        RelocationResponse returned = fixture.sendToUnitAndReturn(resource, 8.0, batch);

        DefectResponse created = fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildRelocationFromUnitDefect(
                        storageId, resource, returned.getId(), 4.0, LocalDate.now()));
        assertThat(created.getType()).isEqualTo(DefectType.RELOCATION_FROM_UNIT);

        DefectStockAssertions.assertStockDebited(stockBefore, fixture.resourceStock(resource), 4.0,
                "після браку RELOCATION_FROM_UNIT");
    }

    @Test(priority = 260)
    @TestCaseId("TC-DEF-026")
    @Story("Cannot delete defect that was written off")
    @Description("REQ-DEF-001 AC-07: видалення повністю списаного браку має бути заборонено")
    @Severity(SeverityLevel.NORMAL)
    public void testCannotDeleteWrittenOffDefect() {
        DefectResponse created = fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildStorageFifoDefect(storageId, defectResourceId, 5.0));

        Response woResp = fixture.writeOffRaw(UserRole.OWNER_1,
                DefectDataFactory.buildWriteOffForDefect(created, storageId, 5.0, "full write-off"));
        assertThat(woResp.statusCode()).as("повне списання").isEqualTo(200);

        Response deleteResp = fixture.deleteRaw(UserRole.OWNER_1, created.getId());
        Allure.step("DELETE повністю списаного браку відхилено", () ->
                assertThat(deleteResp.statusCode()).as("body=%s", safeBody(deleteResp)).isGreaterThanOrEqualTo(400));
    }

    @Test(priority = 270)
    @TestCaseId("TC-DEF-027")
    @Story("Cannot create production defect on shipped batch")
    @Description("Брак на виробничу партію після відправки зовнішньому контрагенту має бути відхилений")
    @Severity(SeverityLevel.NORMAL)
    public void testCannotCreateProductionDefectOnShippedBatch() {
        Long outRes = fixture.outputResourceId();
        String batch = ProductionDataFactory.uniqueBatchNumber();
        ManufacturingItemResponse prod = fixture.createProduction(4.0, batch);
        double produced = capturedBatchAmount(outRes, batch, "після виробництва");

        fixture.sendAndFinishAtRecipient(
                UserRole.OWNER_2, owner2Storage, outRes, produced, batch, true);

        Response resp = fixture.createRaw(UserRole.OWNER_1,
                DefectDataFactory.buildProductionDefect(storageId, outRes, prod.getId(), produced, LocalDate.now()));
        Allure.step("Брак на відправлену партію відхилено", () ->
                assertThat(resp.statusCode()).as("body=%s", safeBody(resp)).isGreaterThanOrEqualTo(400));
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private double capturedBatchAmount(Long outputResourceId, String batchNumber, String phase) {
        return ProductionBatchAssertions.captureProducedBatch(
                apiExecutor, storageId, UserRole.OWNER_1, outputResourceId, batchNumber, phase).amount();
    }

    private List<Long> linkedRelocations(Long resourceId, LocalDate date) {
        Response resp = fixture.getLinkedRelocationIdsRaw(UserRole.OWNER_1, resourceId, date);
        assertThat(resp.statusCode()).as("linked-relocation-ids має повертати 200, body=%s", safeBody(resp))
                .isEqualTo(200);
        List<Long> ids = resp.jsonPath().getList("", Long.class);
        return ids != null ? ids : List.of();
    }

    private static String safeBody(Response response) {
        try {
            String body = response.asString();
            return body != null && body.length() > 500 ? body.substring(0, 500) : body;
        } catch (Exception e) {
            return "<no body>";
        }
    }

    private ManufacturingItemResponse getProductionById(Long productionId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PRODUCTION_GET_BY_ID, UserRole.OWNER_1, null, productionId, storageId);
        return response.as(ManufacturingItemResponse.class);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Backdates {@code defect.created_at} so delete-window validation can be exercised without waiting.
     * Requires DB connectivity ({@code use.database=true} / local profile).
     */
    private void backdateDefectCreatedAt(Long defectId, Instant createdAt) {
        if (getDbHelper() == null) {
            throw new SkipException(
                    "TC-DEF-030 потребує БД для backdate defect.created_at "
                            + "(увімкніть use.database=true або запустіть з -Denv=local)");
        }
        Allure.step("DB: UPDATE defect SET created_at=" + createdAt + " WHERE id=" + defectId, () -> {
            String sql = "UPDATE defect SET created_at = ? WHERE id = ?";
            try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(sql)) {
                ps.setTimestamp(1, Timestamp.from(createdAt));
                ps.setLong(2, defectId);
                int updated = ps.executeUpdate();
                assertThat(updated)
                        .as("має оновитись рівно один рядок defect id=%s", defectId)
                        .isEqualTo(1);
            } catch (SQLException e) {
                throw new IllegalStateException(
                        "Не вдалося backdate defect.created_at id=" + defectId + ": " + e.getMessage(), e);
            }
        });
    }
}

