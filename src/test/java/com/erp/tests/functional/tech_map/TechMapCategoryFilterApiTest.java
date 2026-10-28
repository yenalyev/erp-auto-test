package com.erp.tests.functional.tech_map;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.TechMapCategoryFilterFixture;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.*;
import org.testng.annotations.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@Epic("Technological Maps") @Feature("Категорія виробу")
@Severity(SeverityLevel.CRITICAL)
public class TechMapCategoryFilterApiTest extends BaseFunctionalTest {
    private TechMapCategoryFilterFixture f;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareCategories() {
        f = new TechMapCategoryFilterFixture(testContext, apiExecutor);
        f.prepare();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupCategories() { if (f != null) f.cleanup(); }

    @DataProvider public Object[][] roles() {
        return new Object[][]{{UserRole.ADMIN}, {UserRole.OWNER_1}};
    }

    @Test(dataProvider = "roles") @TestCaseId("TC-TM-CAT-API-001")
    @Description("GET як tk-ui: categoryIds, storageIds, isActive=true, type=PRODUCTION. Категорія виходу, а не сировини; однакова поведінка ADMIN/OWNER.")
    public void singleCategoryUsesOutputOnly(UserRole role) {
        assertThat(f.ids(role, f.query().toBuilder().categoryIds(List.of(f.categoryA.getId())).build()))
                .containsExactlyInAnyOrder(f.a.getId(), f.multi.getId());
        assertThat(f.ids(role, f.query().toBuilder().categoryIds(List.of(f.categoryB.getId())).build()))
                .containsExactlyInAnyOrder(f.b.getId(), f.multi.getId());
    }

    @Test @TestCaseId("TC-TM-CAT-API-002")
    @Description("Кілька categoryIds мають OR-семантику; техкарта з двома відповідними виходами повертається один раз.")
    public void multipleCategoriesReturnDistinctUnion() {
        assertThat(f.ids(UserRole.ADMIN, f.query().toBuilder()
                .categoryIds(List.of(f.categoryA.getId(), f.categoryB.getId())).build()))
                .containsExactlyInAnyOrder(f.a.getId(), f.b.getId(), f.multi.getId());
    }

    @Test @TestCaseId("TC-TM-CAT-API-003")
    @Description("Батьківську категорію UI розгортає в явні categoryIds піддерева; прямий API фільтрує лише передані id.")
    public void expandedParentIncludesChildren() {
        assertThat(f.ids(UserRole.ADMIN, f.query().toBuilder().categoryIds(List.of(f.parent.getId())).build()))
                .containsExactly(f.parentMap.getId());
        assertThat(f.ids(UserRole.ADMIN, f.query().toBuilder()
                .categoryIds(List.of(f.parent.getId(), f.categoryA.getId())).build()))
                .containsExactlyInAnyOrder(f.parentMap.getId(), f.a.getId(), f.multi.getId());
    }

    @Test @TestCaseId("TC-TM-CAT-API-004")
    @Description("Категорія AND назва/сировина/продукт; назва продукту й категорія повинні відповідати тому самому output-ресурсу.")
    public void categoryIntersectsOtherSearchFields() {
        var query = f.query().toBuilder().categoryIds(List.of(f.categoryA.getId()))
                .ingredientTerm(f.raw.getName()).productTerm(f.productA.getName()).build();
        assertThat(f.ids(UserRole.ADMIN, query)).containsExactlyInAnyOrder(f.a.getId(), f.multi.getId());
        assertThat(f.ids(UserRole.ADMIN, query.toBuilder().name(f.a.getName()).build()))
                .containsExactly(f.a.getId());
        assertThat(f.ids(UserRole.ADMIN, query.toBuilder().productTerm(f.productB.getName()).build()))
                .as("Multi-output карта не поєднує назву одного виходу з категорією іншого").isEmpty();
    }

    @Test @TestCaseId("TC-TM-CAT-API-005")
    @Description("Порожня категорія дає порожній список; очищення відновлює активні PRODUCTION карти. Архів та DISASSEMBLE з тією ж категорією не потрапляють у список.")
    public void emptyResultClearAndStateTypeIsolation() {
        assertThat(f.ids(UserRole.ADMIN, f.query().toBuilder()
                .categoryIds(List.of(f.emptyCategory.getId())).build())).isEmpty();
        assertThat(f.ids(UserRole.ADMIN, f.query().toBuilder().categoryIds(List.of()).build()))
                .containsExactlyInAnyOrder(f.a.getId(), f.b.getId(), f.parentMap.getId(), f.multi.getId());
        var filtered = f.query().toBuilder().categoryIds(List.of(f.categoryA.getId()));
        assertThat(f.ids(UserRole.ADMIN, filtered.isActive(false).build())).containsExactly(f.archived.getId());
        assertThat(f.ids(UserRole.ADMIN, filtered.isActive(true).type("DISASSEMBLE").build()))
                .containsExactly(f.disassemble.getId());
    }

    @Test @TestCaseId("TC-TM-CAT-API-006")
    @Description("Пагінація виконується після фільтрації категорії: size=1, дві сторінки без дублів, totalElements=2.")
    public void paginationCountsFilteredMaps() {
        var query = f.query().toBuilder().categoryIds(List.of(f.categoryB.getId())).pageSize(1).build();
        var first = f.maps.listRaw(UserRole.ADMIN, query);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.jsonPath().getLong("page.totalElements")).isEqualTo(2);
        assertThat(first.jsonPath().getInt("page.totalPages")).isEqualTo(2);
        var ids = new java.util.ArrayList<>(f.ids(UserRole.ADMIN, query));
        ids.addAll(f.ids(UserRole.ADMIN, query.toBuilder().page(1).build()));
        assertThat(ids).containsExactlyInAnyOrder(f.b.getId(), f.multi.getId());
    }

    @Test @TestCaseId("TC-TM-CAT-API-007")
    @Description("GET tag-statistics отримує ті самі categoryIds як список: теги відфільтрованих карт, точні counts, без тегів іншої категорії. Відомий дефект у локальному SUT: native query getTagStatistics не застосовує categoryIds; очікуємо відфільтровані теги, зайвий тег має залишати тест червоним.")
    public void tagStatisticsRespectCategory() {
        var statistics = f.maps.getTagStatistics(UserRole.ADMIN, f.query().toBuilder()
                .categoryIds(List.of(f.categoryA.getId())).build());
        assertThat(statistics).extracting(s -> s.getTag()).containsExactlyInAnyOrder(f.tag("a"), f.tag("multi"));
        assertThat(statistics).allSatisfy(s -> assertThat(s.getCount()).isEqualTo(1));
    }
}
