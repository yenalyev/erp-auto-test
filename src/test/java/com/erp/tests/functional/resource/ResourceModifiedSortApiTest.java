package com.erp.tests.functional.resource;

import com.erp.annotations.TestCaseId;
import com.erp.fixtures.ResourceModifiedSortFixture;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.*;
import org.testng.annotations.*;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Master Data")
@Feature("Resources — сортування за Змінено")
public class ResourceModifiedSortApiTest extends BaseFunctionalTest {
    private ResourceModifiedSortFixture fixture;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareSortData() {
        fixture = new ResourceModifiedSortFixture(testContext, apiExecutor);
        fixture.prepare();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupSortData() { if (fixture != null) fixture.cleanup(); }

    @Test
    @TestCaseId("TC-RES-SORT-API-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Глобальний каталог: updated_at ASC/DESC повертає 200 та точний порядок за updatedAt після зміни ресурсу.")
    public void sortsGlobalCatalogByModificationTime() {
        for (String direction : List.of("ASC", "DESC")) {
            var response = fixture.list(null, direction, fixture.marker, 0, 10);
            assertThat(ResourceModifiedSortFixture.rows(response.asString())).isEqualTo(fixture.expected(direction));
            assertThat(response.jsonPath().getInt("page.totalElements")).isEqualTo(3);
        }
    }

    @Test
    @TestCaseId("TC-RES-SORT-API-002")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Обмежена локація (native query): ASC/DESC, пагінація size=1, пошук, порожня вибірка та повернення до назви без 500.")
    public void sortsRestrictedCatalogAcrossPagesAndFilters() {
        for (String direction : List.of("ASC", "DESC")) {
            List<ResourceModifiedSortFixture.Row> actual = new ArrayList<>();
            for (int page = 0; page < 3; page++) {
                var response = fixture.list(fixture.storageId(), direction, fixture.marker, page, 1);
                var rows = ResourceModifiedSortFixture.rows(response.asString());
                assertThat(rows).hasSize(1);
                assertThat(response.jsonPath().getInt("page.totalElements")).isEqualTo(3);
                assertThat(response.jsonPath().getInt("page.totalPages")).isEqualTo(3);
                actual.addAll(rows);
            }
            assertThat(actual).isEqualTo(fixture.expected(direction));
            var empty = fixture.list(fixture.storageId(), direction, fixture.marker + "-missing", 0, 10);
            assertThat(ResourceModifiedSortFixture.rows(empty.asString())).isEmpty();
            assertThat(empty.jsonPath().getInt("page.totalElements")).isZero();
        }
        var reset = fixture.list(fixture.storageId(), null, fixture.marker, 0, 10);
        assertThat(ResourceModifiedSortFixture.rows(reset.asString())).extracting(ResourceModifiedSortFixture.Row::name)
                .containsExactlyElementsOf(fixture.expected("ASC").stream()
                        .map(ResourceModifiedSortFixture.Row::name).sorted().toList());
    }
}
