package com.erp.tests.functional.equipment;

import com.erp.annotations.TestCaseId;
import com.erp.enums.EquipmentStatus;
import com.erp.fixtures.EquipmentModelArchiveFixture;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.*;
import org.testng.annotations.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@Epic("Equipment")
@Feature("Архівація моделі обладнання")
public class EquipmentModelArchiveApiTest extends BaseFunctionalTest {
    private EquipmentModelArchiveFixture fixture;
    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareArchive() {
        String recovery = System.getProperty("equipment.archive.recovery");
        if (recovery != null) EquipmentModelArchiveFixture.recoverOwnedTransit(testContext, apiExecutor, recovery);
        fixture = new EquipmentModelArchiveFixture(testContext, apiExecutor); fixture.prepare();
    }
    @AfterClass(alwaysRun = true)
    public void cleanupArchive() { if (fixture != null) fixture.cleanup(); }

    @Test @TestCaseId("TC-EQA-API-001")
    @Description("Модель лише зі списаним обладнанням архівується; історичне обладнання та метадані зберігаються.")
    public void archivesRetiredModelAndPreservesHistory() {
        var sample = fixture.create("history", EquipmentStatus.RETIRED);
        var before = fixture.model(sample);
        var item = fixture.equipment(sample);
        assertThat(before.getEquipmentCount()).isZero();
        assertThat(fixture.archive(sample).statusCode()).isEqualTo(204);
        var after = fixture.model(sample);
        assertThat(after.getStatus()).isEqualTo("ARCHIVED");
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.getName()).isEqualTo(before.getName());
        assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
        assertThat(after.getCreatedBy()).isEqualTo(before.getCreatedBy());
        assertThat(after.getCategories()).isEqualTo(before.getCategories());
        assertThat(fixture.equipment(sample)).usingRecursiveComparison().isEqualTo(item);
    }

    @DataProvider public Object[][] stockStatuses() {
        return new Object[][] {{EquipmentStatus.AVAILABLE}, {EquipmentStatus.IN_REPAIR}};
    }
    @Test(dataProvider = "stockStatuses") @TestCaseId("TC-EQA-API-002")
    @Description("AVAILABLE та IN_REPAIR блокують архів незалежно від можливості прямого PUT; відмова атомарна.")
    public void nonRetiredStockBlocksArchive(EquipmentStatus status) {
        var sample = fixture.create("stock-" + status, status);
        assertBlocked(sample);
    }

    @Test @TestCaseId("TC-EQA-API-003")
    @Description("Єдина одиниця в IN_TRANSIT блокує архів; перевіряється реальна активна видача.")
    public void activeRelocationBlocksArchive() {
        var sample = fixture.create("transit", EquipmentStatus.AVAILABLE);
        fixture.send(sample);
        assertBlocked(sample);
    }

    @Test @TestCaseId("TC-EQA-API-004")
    @Description("Після списання останньої одиниці заборона зникає; повторний PUT архівної моделі відхиляється.")
    public void clearingLastBlockerAllowsArchiveAndRepeatIsRejected() {
        var sample = fixture.create("clear", EquipmentStatus.AVAILABLE);
        assertBlocked(sample);
        fixture.retireOrChange(sample, EquipmentStatus.RETIRED);
        assertThat(fixture.archive(sample).statusCode()).isEqualTo(204);
        var before = fixture.model(sample);
        var response = fixture.archive(sample);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.asString()).contains("Модель вже в архіві");
        assertThat(fixture.model(sample)).usingRecursiveComparison().isEqualTo(before);
    }

    @Test @TestCaseId("TC-EQA-API-005")
    @Description("ACTIVE, ARCHIVED та без статусу; статус AND назва AND категорія, без дублів, з коректною пагінацією.")
    public void filtersStatusWithNameCategoryAndPagination() {
        var active = fixture.create("filter-active", EquipmentStatus.AVAILABLE);
        var archived = fixture.create("filter-archived", EquipmentStatus.RETIRED);
        assertThat(fixture.archive(archived).statusCode()).isEqualTo(204);
        for (String status : List.of("ACTIVE", "ARCHIVED")) {
            var expected = status.equals("ACTIVE") ? active : archived;
            var result = fixture.list(Map.of("modelName", fixture.marker + " filter-", "status", status,
                    "categoryIds", fixture.categoryId, "size", 1));
            assertThat(result.getContent()).extracting("id").containsExactly(expected.modelId());
            assertThat(result.getContent()).extracting("status").containsExactly(status);
            assertThat(result.getPage().getTotalElements()).isEqualTo(1);
            assertThat(fixture.list(Map.of("modelName", expected.name(), "status", status,
                    "page", 1, "size", 1)).getContent()).isEmpty();
        }
        assertThat(fixture.list(Map.of("modelName", fixture.marker + " filter-")).getContent())
                .extracting("id").containsExactlyInAnyOrder(active.modelId(), archived.modelId());
        assertThat(fixture.list(Map.of("modelName", archived.name(), "status", "ACTIVE")).getContent()).isEmpty();
        assertThat(fixture.list(Map.of("modelName", active.name(), "status", "ARCHIVED")).getContent()).isEmpty();
    }

    @Test @TestCaseId("TC-EQA-API-006")
    @Description("Suggestions містить активну модель до архівації й не містить її після; активний контроль лишається.")
    public void suggestionsExcludeArchivedModels() {
        var control = fixture.create("suggest-active", EquipmentStatus.AVAILABLE);
        var target = fixture.create("suggest-archived", EquipmentStatus.RETIRED);
        assertThat(fixture.suggestions(fixture.marker + " suggest-")).contains(control.name(), target.name());
        assertThat(fixture.archive(target).statusCode()).isEqualTo(204);
        assertThat(fixture.suggestions(fixture.marker + " suggest-")).containsExactly(control.name());
        assertThat(fixture.suggestions(target.name())).isEmpty();
    }

    private void assertBlocked(EquipmentModelArchiveFixture.Sample sample) {
        var before = fixture.model(sample);
        var item = fixture.equipment(sample);
        assertThat(before.getEquipmentCount()).isEqualTo(1);
        var response = fixture.archive(sample);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.asString()).contains("Модель використовується обладнанням");
        assertThat(fixture.model(sample)).usingRecursiveComparison().isEqualTo(before);
        assertThat(fixture.equipment(sample)).usingRecursiveComparison().isEqualTo(item);
    }
}
