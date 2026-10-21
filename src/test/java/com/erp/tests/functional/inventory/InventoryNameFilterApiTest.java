package com.erp.tests.functional.inventory;

import com.erp.annotations.TestCaseId;
import com.erp.fixtures.InventoryNameFilterFixture;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.assertions.InventoryProcessExcelAssertions;
import com.erp.utils.assertions.InventoryProcessExcelAssertions.Difference;
import io.qameta.allure.*;
import org.testng.annotations.*;
import java.io.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@Epic("Inventory")
@Feature("REQ-INV-NAME")
@Story("Інвентаризація → Закриті — фільтр по назві ресурсу")
public class InventoryNameFilterApiTest extends BaseFunctionalTest {
    private InventoryNameFilterFixture data;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void arrange() {
        data = new InventoryNameFilterFixture(testContext, apiExecutor);
        data.arrange();
    }
    @AfterMethod(alwaysRun = true)
    public void unchanged() { if (data != null) data.assertUnchanged(); }
    @AfterClass(alwaysRun = true)
    public void cleanup() { if (data != null) data.cleanup(); }

    @Test
    @TestCaseId("TC-INV-NAME-API-001")
    @Description("Повна назва, підрядок, Unicode і регістр: лише відповідні процеси та відповідні ресурси всередині diff.")
    public void fullPartialAndCaseInsensitiveNamesFilterNestedDifferences() {
        for (String term : List.of(data.alpha.getName(), data.alpha.getName().toUpperCase(Locale.ROOT))) {
            var processes = data.processes(Map.of("resourceName", term));
            assertThat(processes).extracting(p -> p.getId()).containsExactly(data.mixed.getId());
            InventoryNameFilterFixture.assertDiff(processes.getFirst(), Map.of(data.alpha.getId(), 2.25));
            assertThat(processes.getFirst().getDiff().get(0).path("resourceName").asText()).isEqualTo(data.alpha.getName());
        }
        var partial = data.processes(Map.of("resourceName", "АКУМУЛЯТОР"));
        assertThat(partial).extracting(p -> p.getId()).containsExactly(data.mixed.getId());
        InventoryNameFilterFixture.assertDiff(partial.getFirst(), Map.of(data.alpha.getId(), 2.25, data.beta.getId(), -3.5));
    }

    @Test
    @TestCaseId("TC-INV-NAME-API-002")
    @Description("Пошук без збігів → content=[] і totalElements=0; очищення повертає всі 4 процеси та повний diff.")
    public void noMatchAndResetRestoreAllClosedAndRejectedProcesses() {
        var empty = data.list(Map.of("resourceName", "missing-" + data.marker));
        assertThat(empty.jsonPath().getList("content")).isEmpty();
        assertThat(empty.jsonPath().getLong("page.totalElements")).isZero();
        var reset = data.processes(Map.of());
        assertThat(reset).extracting(p -> p.getId()).containsExactlyInAnyOrder(
                data.mixed.getId(), data.cableOnly.getId(), data.manual.getId(), data.rejected.getId());
        InventoryNameFilterFixture.assertDiff(reset.stream().filter(p -> p.getId().equals(data.mixed.getId())).findFirst().orElseThrow(),
                Map.of(data.alpha.getId(), 2.25, data.beta.getId(), -3.5, data.cable.getId(), 4.0));
    }

    @Test
    @TestCaseId("TC-INV-NAME-API-003")
    @Description("Пагінація рахує інвентаризації, а не ресурси; resourceName AND requestedBy AND states; зміна фільтра не кешує попередній diff.")
    public void paginationCountsProcessesAndFiltersIntersect() {
        var first = data.list(Map.of("resourceName", data.marker, "size", 1, "page", 0));
        var second = data.list(Map.of("resourceName", data.marker, "size", 1, "page", 1));
        assertThat(first.jsonPath().getLong("page.totalElements")).isEqualTo(2);
        assertThat(second.jsonPath().getLong("page.totalElements")).isEqualTo(2);
        assertThat(List.of(first.jsonPath().getLong("content[0].id"), second.jsonPath().getLong("content[0].id")))
                .containsExactlyInAnyOrder(data.mixed.getId(), data.cableOnly.getId());
        assertThat(data.processes(Map.of("resourceName", data.alpha.getName(), "requestedBy", "missing-" + data.marker))).isEmpty();
        assertThat(data.processes(Map.of("resourceName", data.alpha.getName(), "states", List.of("REJECTED")))).isEmpty();
        var found = data.processes(Map.of("resourceName", data.alpha.getName(), "requestedBy", data.mixed.getRequestedBy(),
                "categoryIds", List.of(data.alpha.getCategory().getId())));
        assertThat(found).hasSize(1);
        InventoryNameFilterFixture.assertDiff(found.getFirst(), Map.of(data.alpha.getId(), 2.25));
    }

    @Test
    @TestCaseId("TC-INV-NAME-API-004")
    @Description("XLSX відфільтрований усередині mixed diff: 1 повний збіг; 2 часткові; зміна на Кабель → 2 процеси. Числові +2.25/-3.5/+4/+1 без зайвих ресурсів.")
    public void excelContainsOnlyCurrentMatchingResources() throws IOException {
        excel(Map.of("resourceName", data.alpha.getName()), List.of(new Difference(data.mixed.getId(), data.alpha.getName(), 2.25)));
        excel(Map.of("resourceName", "АКУМУЛЯТОР"), List.of(
                new Difference(data.mixed.getId(), data.alpha.getName(), 2.25),
                new Difference(data.mixed.getId(), data.beta.getName(), -3.5)));
        excel(Map.of("resourceName", data.cable.getName()), List.of(
                new Difference(data.mixed.getId(), data.cable.getName(), 4.0),
                new Difference(data.cableOnly.getId(), data.cable.getName(), 1.0)));
    }

    @Test
    @TestCaseId("TC-INV-NAME-API-005")
    @Description("Немає збігів → XLSX лише із заголовками; очищення → усі ресурси й процеси без diff; export з AND фільтром повертає порожню книгу.")
    public void emptyExcelResetAndCombinedFiltersUseFreshSelection() throws IOException {
        excel(Map.of("resourceName", "missing-" + data.marker), List.of());
        excel(Map.of(), InventoryProcessExcelAssertions.all(data));
        excel(Map.of("resourceName", data.alpha.getName(), "requestedBy", "missing-" + data.marker), List.of());
    }

    private void excel(Map<String, ?> filters, List<Difference> expected) throws IOException {
        byte[] bytes = data.export(filters).asByteArray();
        Allure.addAttachment("Filtered inventory processes Excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ByteArrayInputStream(bytes), ".xlsx");
        InventoryProcessExcelAssertions.assertWorkbook(bytes, expected);
    }
}
