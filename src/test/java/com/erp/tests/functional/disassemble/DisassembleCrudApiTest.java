package com.erp.tests.functional.disassemble;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.DisassembleFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.models.response.DisassembleItemResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.helpers.ProductionStockAssertions;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Slf4j
@Epic("Production")
@Feature("REQ-MFG Disassemble")
public class DisassembleCrudApiTest extends BaseFunctionalTest {

    private DisassembleFixture fixture;
    private StorageFixture storageFixture;
    private ResourceFixture resourceFixture;
    private long storageId;
    private TechnologicalMapResponse techMap;
    private long inputResourceId;
    private long outputResourceId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupDisassembleTests() {
        fixture = new DisassembleFixture(testContext, apiExecutor);
        storageFixture = new StorageFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        resourceFixture.prepareContext();

        StorageResponse isolated = storageFixture.createProductionStorage(
                storageFixture.resolveParentUnit().getId(), "dis-crud-");
        ResourceResponse input = resourceFixture.createUniqueResource("dis-in-");
        ResourceResponse output = resourceFixture.createUniqueResource("dis-out-");
        storageId = isolated.getId();
        techMap = fixture.createDisassembleTechMapAs(
                UserRole.ADMIN, storageId, List.of(input, output));
        inputResourceId = input.getId();
        outputResourceId = output.getId();
        fixture.seedInputStock(UserRole.ADMIN, storageId, inputResourceId);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupIsolatedStorage() {
        if (storageFixture != null) {
            storageFixture.deactivateTrackedStorages(UserRole.ADMIN);
        }
    }

    @BeforeMethod(alwaysRun = true)
    public void seedInputStock() {
        fixture.seedInputStock(UserRole.ADMIN, storageId, inputResourceId);
    }

    @Test(priority = 10)
    @TestCaseId("TC-DIS-001")
    @Story("Create disassemble")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST розбір списує input і додає output; GET by id повертає запис.")
    public void createDisassembleAdjustsStock() {
        Set<Long> tracked = Set.of(inputResourceId, outputResourceId);
        ProductionStockAssertions.StockSnapshot before = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.ADMIN, tracked, "до розбору");

        Response created = fixture.createAs(UserRole.ADMIN, storageId, techMap, 2.0, 2.0,
                "dis-" + System.currentTimeMillis() % 1_000_000);
        assertThat(created.statusCode()).isEqualTo(200);
        List<DisassembleItemResponse> items = created.jsonPath().getList("", DisassembleItemResponse.class);
        assertThat(items).isNotEmpty();
        DisassembleItemResponse fetched = fixture.getById(UserRole.ADMIN, items.getFirst().getId(), storageId);
        assertThat(fetched.getId()).isEqualTo(items.getFirst().getId());

        ProductionStockAssertions.StockSnapshot after = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.ADMIN, tracked, "після розбору");
        assertThat(after.amountOf(inputResourceId))
                .isLessThan(before.amountOf(inputResourceId));
    }

    @Test(priority = 15)
    @TestCaseId("TC-DIS-003")
    @Story("Update disassemble")
    @Severity(SeverityLevel.CRITICAL)
    @Description("PUT розбір змінює кількість; залишок input змінюється відносно create.")
    public void updateDisassembleAdjustsStock() {
        Set<Long> tracked = Set.of(inputResourceId, outputResourceId);
        Response created = fixture.createAs(UserRole.ADMIN, storageId, techMap, 1.0, 1.0,
                "dis-upd-" + System.currentTimeMillis() % 1_000_000);
        List<DisassembleItemResponse> items = created.jsonPath().getList("", DisassembleItemResponse.class);
        long id = items.getFirst().getId();

        ProductionStockAssertions.StockSnapshot beforeUpdate = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.ADMIN, tracked, "перед update");
        Response updated = fixture.updateRaw(
                UserRole.ADMIN, id, storageId, techMap, 2.0, 2.0, items.getFirst().getBatchNumber());
        assertThat(updated.statusCode()).isIn(200, 204);
        ProductionStockAssertions.StockSnapshot afterUpdate = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.ADMIN, tracked, "після update");
        assertThat(afterUpdate.amountOf(inputResourceId))
                .isLessThanOrEqualTo(beforeUpdate.amountOf(inputResourceId));
    }

    @Test(priority = 20)
    @TestCaseId("TC-DIS-002")
    @Story("Delete disassemble")
    @Severity(SeverityLevel.CRITICAL)
    @Description("DELETE розбір повертає input.")
    public void deleteDisassembleRestoresInput() {
        Set<Long> tracked = Set.of(inputResourceId, outputResourceId);
        Response created = fixture.createAs(UserRole.ADMIN, storageId, techMap, 1.0, 1.0,
                "dis-del-" + System.currentTimeMillis() % 1_000_000);
        List<DisassembleItemResponse> items = created.jsonPath().getList("", DisassembleItemResponse.class);
        long id = items.getFirst().getId();

        ProductionStockAssertions.StockSnapshot beforeDelete = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.ADMIN, tracked, "перед delete");
        Response deleted = fixture.deleteRaw(UserRole.ADMIN, id, storageId);
        assertThat(deleted.statusCode()).isIn(200, 204);
        ProductionStockAssertions.StockSnapshot afterDelete = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.ADMIN, tracked, "після delete");
        assertThat(afterDelete.amountOf(inputResourceId))
                .isGreaterThanOrEqualTo(beforeDelete.amountOf(inputResourceId));
    }
    @Test(priority = 30)
    @TestCaseId("TC-DIS-004")
    @Story("Received components after disassembly")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Розбір додає до запасу фактично отриману кількість компонента.")
    public void createDisassembleAddsProducedComponent() {
        Set<Long> tracked = Set.of(inputResourceId, outputResourceId);
        ProductionStockAssertions.StockSnapshot before = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.ADMIN, tracked, "до створення розбору");

        Response created = fixture.createAs(UserRole.ADMIN, storageId, techMap, 2.0, 2.0,
                "dis-004-" + System.currentTimeMillis() % 1_000_000);
        assertThat(created.statusCode()).isEqualTo(200);

        ProductionStockAssertions.StockSnapshot after = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.ADMIN, tracked, "після створення розбору");
        assertThat(after.amountOf(outputResourceId) - before.amountOf(outputResourceId))
                .isCloseTo(2.0, within(0.01));
    }

    @Test(priority = 31)
    @TestCaseId("TC-DIS-005")
    @Story("Disassembly amount recalculates both stocks")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Збільшення кількості розбору на одиницю коригує запас предмета й отриманого компонента.")
    public void updateDisassembleRecalculatesBothStocks() {
        Set<Long> tracked = Set.of(inputResourceId, outputResourceId);
        Response created = fixture.createAs(UserRole.ADMIN, storageId, techMap, 1.0, 1.0,
                "dis-005-" + System.currentTimeMillis() % 1_000_000);
        long id = created.jsonPath().getList("", DisassembleItemResponse.class).getFirst().getId();
        String batchNumber = fixture.getById(UserRole.ADMIN, id, storageId).getBatchNumber();
        ProductionStockAssertions.StockSnapshot before = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.ADMIN, tracked, "перед зміною розбору");

        Response updated = fixture.updateRaw(UserRole.ADMIN, id, storageId, techMap, 2.0, 2.0, batchNumber);
        assertThat(updated.statusCode()).isIn(200, 204);

        ProductionStockAssertions.StockSnapshot after = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.ADMIN, tracked, "після зміни розбору");
        assertThat(after.amountOf(inputResourceId) - before.amountOf(inputResourceId))
                .isCloseTo(-1.0, within(0.01));
        assertThat(after.amountOf(outputResourceId) - before.amountOf(outputResourceId))
                .isCloseTo(1.0, within(0.01));
    }

    @Test(priority = 32)
    @TestCaseId("TC-DIS-006")
    @Story("Deleting disassembly reverses both stocks")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Видалення розбору повертає предмет до запасу й скасовує надходження компонента.")
    public void deleteDisassembleReversesBothStocks() {
        Set<Long> tracked = Set.of(inputResourceId, outputResourceId);
        Response created = fixture.createAs(UserRole.ADMIN, storageId, techMap, 1.0, 1.0,
                "dis-006-" + System.currentTimeMillis() % 1_000_000);
        long id = created.jsonPath().getList("", DisassembleItemResponse.class).getFirst().getId();
        ProductionStockAssertions.StockSnapshot before = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.ADMIN, tracked, "перед видаленням розбору");

        Response deleted = fixture.deleteRaw(UserRole.ADMIN, id, storageId);
        assertThat(deleted.statusCode()).isIn(200, 204);

        ProductionStockAssertions.StockSnapshot after = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.ADMIN, tracked, "після видалення розбору");
        assertThat(after.amountOf(inputResourceId) - before.amountOf(inputResourceId))
                .isCloseTo(1.0, within(0.01));
        assertThat(after.amountOf(outputResourceId) - before.amountOf(outputResourceId))
                .isCloseTo(-1.0, within(0.01));
    }

    @Test(priority = 33)
    @TestCaseId("TC-DIS-007")
    @Story("Disassembly stays within its location")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Розбір на одній локації не змінює залишки предмета й компонента на іншій.")
    public void disassembleDoesNotChangeAnotherLocationStock() {
        StorageResponse other = storageFixture.createProductionStorage(
                storageFixture.resolveParentUnit().getId(), "dis-other-");
        fixture.seedInputStock(UserRole.ADMIN, other.getId(), inputResourceId);
        Set<Long> tracked = Set.of(inputResourceId, outputResourceId);
        ProductionStockAssertions.StockSnapshot before = ProductionStockAssertions.capture(
                apiExecutor, other.getId(), UserRole.ADMIN, tracked, "інша локація до розбору");

        Response created = fixture.createAs(UserRole.ADMIN, storageId, techMap, 1.0, 1.0,
                "dis-007-" + System.currentTimeMillis() % 1_000_000);
        assertThat(created.statusCode()).isEqualTo(200);

        ProductionStockAssertions.StockSnapshot after = ProductionStockAssertions.capture(
                apiExecutor, other.getId(), UserRole.ADMIN, tracked, "інша локація після розбору");
        assertThat(after.amountOf(inputResourceId)).isCloseTo(before.amountOf(inputResourceId), within(0.01));
        assertThat(after.amountOf(outputResourceId)).isCloseTo(before.amountOf(outputResourceId), within(0.01));
    }

}
