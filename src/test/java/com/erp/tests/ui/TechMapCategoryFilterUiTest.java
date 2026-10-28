package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.TechMapCategoryFilterFixture;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.pages.TechnologicalMapsListPage;
import io.qameta.allure.*;
import org.testng.annotations.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

@Epic("Technological Maps") @Feature("Категорія виробу")
@Severity(SeverityLevel.CRITICAL)
public class TechMapCategoryFilterUiTest extends BaseUITest {
    private TechMapCategoryFilterFixture f;

    @BeforeClass(alwaysRun = true)
    @Override public void baseTestClassSetup() {
        super.baseTestClassSetup();
        f = new TechMapCategoryFilterFixture(testContext, apiExecutor);
        f.prepare();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupCategories() { if (f != null) f.cleanup(); }

    private TechnologicalMapsListPage open(UserRole role) {
        browserContext.clearCookies();
        injectSessionCookies(cachedSessionCookies(role), sessionCookieDomain());
        injectWorkspaceView(role, f.storageId);
        // Init scripts apply to every new document, including this first navigation.
        return new TechnologicalMapsListPage(page).openForStorage(f.storageId).filterByName(f.marker);
    }

    private void rows(TechnologicalMapResponse... expected) {
        assertThat(page.locator("table tbody tr")).hasCount(expected.length);
        for (var map : expected) assertThat(page.getByTestId("technological-map-row-" + map.getId())
                .getByText(map.getName(),
                        new com.microsoft.playwright.Locator.GetByTextOptions().setExact(true))).isVisible();
    }

    private List<Long> categoryIdsInUrl() {
        String query = URI.create(page.url()).getRawQuery();
        if (query == null) return List.of();
        return Arrays.stream(query.split("&"))
                .filter(p -> p.startsWith("category="))
                .flatMap(p -> Arrays.stream(URLDecoder.decode(p.substring(9), StandardCharsets.UTF_8).split(",")))
                .map(Long::valueOf).toList();
    }

    @DataProvider public Object[][] roles() {
        return new Object[][]{{UserRole.ADMIN}, {UserRole.OWNER_1}};
    }

    @Test(dataProvider = "roles") @TestCaseId("TC-TM-CAT-UI-001")
    @Description("ADMIN/OWNER бачать порожній початковий вибір; категорія виробу відсіює карти, у яких ця категорія є лише в сировині.")
    public void singleCategoryFiltersOutput(UserRole role) {
        var list = open(role);
        assertThat(list.categoryFilter()).isVisible();
        assertThat(list.categoryFilter()).hasText("Категорії");
        assertThat(categoryIdsInUrl()).isEmpty();
        rows(f.a, f.b, f.parentMap, f.multi);
        list.toggleProductCategory(f.categoryA.getId());
        list.attachScreenshot("TC-TM-CAT-UI-001 — category A — " + role);
        rows(f.a, f.multi);
        assertThat(categoryIdsInUrl()).containsExactly(f.categoryA.getId());
    }

    @Test @TestCaseId("TC-TM-CAT-UI-002")
    @Description("Кілька категорій об'єднують результати без дублів; повторний клік прибирає категорію, Скинути вибір відновлює список і прибирає category з URL.")
    public void multipleSelectionToggleAndClear() {
        var list = open(UserRole.ADMIN);
        list.toggleProductCategory(f.categoryA.getId()).toggleProductCategory(f.categoryB.getId());
        rows(f.a, f.b, f.multi);
        assertThat(categoryIdsInUrl()).containsExactlyInAnyOrder(f.categoryA.getId(), f.categoryB.getId());
        list.attachScreenshot("TC-TM-CAT-UI-002 — multiple categories");
        list.toggleProductCategory(f.categoryA.getId());
        rows(f.b, f.multi);
        list.clearProductCategories();
        rows(f.a, f.b, f.parentMap, f.multi);
        assertThat(categoryIdsInUrl()).isEmpty();
        assertThat(list.categoryFilter()).hasText("Категорії");
    }

    @Test @TestCaseId("TC-TM-CAT-UI-003")
    @Description("Батьківська категорія вибирає все піддерево; дочірню можна зняти окремо. Стороння категорія не потрапляє в результат.")
    public void parentSelectsSubtreeAndChildCanBeRemoved() {
        var list = open(UserRole.ADMIN);
        list.toggleProductCategory(f.parent.getId());
        rows(f.a, f.parentMap, f.multi);
        assertThat(categoryIdsInUrl()).containsExactlyInAnyOrder(f.parent.getId(), f.categoryA.getId());
        list.attachScreenshot("TC-TM-CAT-UI-003 — parent and descendants");
        list.toggleProductCategory(f.categoryA.getId());
        rows(f.parentMap);
        assertThat(categoryIdsInUrl()).containsExactly(f.parent.getId());
    }

    @Test @TestCaseId("TC-TM-CAT-UI-004")
    @Description("Категорія поєднується з пошуком продукту й сировини через AND; несумісний продукт дає Немає даних, очищення категорії зберігає текстові фільтри.")
    public void searchIntersectionAndEmptyState() {
        var list = open(UserRole.ADMIN);
        list.filterByIngredient(f.raw.getName()).filterByProduct(f.productB.getName());
        rows(f.b, f.multi);
        list.toggleProductCategory(f.categoryA.getId());
        assertThat(page.getByText("Немає даних", new com.microsoft.playwright.Page.GetByTextOptions().setExact(true)))
                .isVisible();
        list.attachScreenshot("TC-TM-CAT-UI-004 — incompatible product and category");
        list.clearProductCategories();
        rows(f.b, f.multi);
        assertThat(page.getByPlaceholder("Введіть назву продукту...")).hasValue(f.productB.getName());
        assertThat(page.getByPlaceholder("Введіть назву сировини...")).hasValue(f.raw.getName());
    }

    @Test @TestCaseId("TC-TM-CAT-UI-005")
    @Description("Вибір категорії зберігається після reload та переходу в історію версій і назад; зміна категорії скидає page на першу.")
    public void selectionSurvivesReloadNavigationAndResetsPage() {
        var list = open(UserRole.ADMIN);
        list.toggleProductCategory(f.categoryA.getId());
        page.reload();
        list.waitForLoaded();
        rows(f.a, f.multi);
        assertThat(list.categoryFilter()).containsText(f.categoryA.getName());
        list.clickTechMapAction(f.a.getName(), "Переглянути версії");
        page.waitForURL("**/technological-maps/versions/**");
        page.goBack();
        list.waitForLoaded();
        rows(f.a, f.multi);
        assertThat(categoryIdsInUrl()).containsExactly(f.categoryA.getId());
        page.navigate(page.url() + "&page=1");
        list.waitForLoaded();
        list.toggleProductCategory(f.categoryB.getId());
        rows(f.a, f.b, f.multi);
        assertThat(URI.create(page.url()).getQuery()).doesNotContain("page=");
        list.attachScreenshot("TC-TM-CAT-UI-005 — selection restored and page reset");
    }
}
