package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.EquipmentDictionaryFixture;
import com.erp.models.response.EquipmentModelResponse;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.EquipmentDictionaryPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import org.testng.annotations.*;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Epic("Master Data")
@Feature("Довідники — Обладнання")
public class EquipmentDictionaryUiTest extends BaseUITest {
    private EquipmentDictionaryFixture fixture;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareDictionary() {
        fixture = new EquipmentDictionaryFixture(testContext, apiExecutor);
        fixture.prepare();
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
        injectWorkspaceView(UserRole.ADMIN, fixture.storageId());
    }

    @AfterClass(alwaysRun = true)
    public void cleanupDictionary() { if (fixture != null) fixture.cleanup(); }

    @Test
    @TestCaseId("TC-EQD-UI-001")
    @Description("Довідники ресурсів → вкладка Обладнання відкриває /equipment-models, а не журнал одиниць /equipment.")
    public void opensEquipmentTabFromResourceDictionaries() {
        page.navigate(ConfigProvider.getBaseUrl() + "/resources");
        var dictionary = new EquipmentDictionaryPage(page);
        dictionary.awaitData(() -> new AppSidebarPage(page).openPageTab("Обладнання"));
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*/equipment-models(?:\\?.*)?$"));
        dictionary.search(fixture.marker);
        dictionary.expectNames(fixture.ascendingNames());
        dictionary.attachScreenshot("TC-EQD-UI-001 — вкладка довідника");
    }

    @Test
    @TestCaseId("TC-EQD-UI-002")
    @Description("Довідник із архівацією: Назва моделі, Категорії, Кількість, Створено, Автор і колонка дій.")
    public void showsRequiredColumnLabels() {
        var dictionary = new EquipmentDictionaryPage(page).open();
        dictionary.attachScreenshot("TC-EQD-UI-002 — заголовки таблиці");
        assertThat(dictionary.headers()).containsExactly("Назва моделі", "Категорії", "Кількість", "Створено", "Автор", "");
    }

    @Test
    @TestCaseId("TC-EQD-UI-003")
    @Description("Один рядок на модель; усі категорії без повторів, дата та автор відображають значення API моделі. "
            + "Для Категорії та автора сортування недоступне.")
    public void rendersCategoriesAndModelAuditFields() {
        var dictionary = new EquipmentDictionaryPage(page).open().search(fixture.marker);
        dictionary.expectNames(fixture.ascendingNames());
        var formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        String zone = (String) page.evaluate("Intl.DateTimeFormat().resolvedOptions().timeZone");
        for (var model : fixture.list(Map.of()).getContent()) {
            var cells = dictionary.row(model.getId()).locator("td");
            assertThat(cells.nth(0)).hasText(model.getName());
            List<String> expectedCategories = model.getName().equals(fixture.alpha)
                    ? List.of(fixture.categoryName(fixture.categoryA()), fixture.categoryName(fixture.categoryB()))
                    : List.of(fixture.categoryName(model.getName().equals(fixture.ghe)
                            ? fixture.categoryA() : fixture.categoryB()));
            assertThat(cells.nth(1).locator("div > div").allTextContents())
                    .containsExactlyInAnyOrderElementsOf(expectedCategories);
            assertThat(cells.nth(2)).hasText(String.valueOf(model.getEquipmentCount()));
            assertThat(cells.nth(3)).hasText(formatter.format(model.getCreatedAt().atZone(ZoneId.of(zone))));
            assertThat(cells.nth(4)).hasText(model.getCreatedBy());
        }
        assertThat(dictionary.canSort("Категорії")).isFalse();
        // The author column is informational and must not expose a sort button.
        assertThat(page.locator("table thead th").nth(4).getByRole(com.microsoft.playwright.options.AriaRole.BUTTON)).hasCount(0);
        dictionary.attachScreenshot("TC-EQD-UI-003 — категорії та аудит моделі");
    }

    @Test
    @TestCaseId("TC-EQD-UI-004")
    @Description("За замовчуванням А–Я; переключення назви та дати ASC/DESC змінює фактичний порядок рядків.")
    public void sortsNamesAndCreationDatesInBothDirections() {
        var dictionary = new EquipmentDictionaryPage(page).open().search(fixture.marker);
        dictionary.expectNames(fixture.ascendingNames());
        dictionary.clickSort("Назва");
        List<String> reverse = new ArrayList<>(fixture.ascendingNames());
        Collections.reverse(reverse);
        dictionary.expectNames(reverse);
        dictionary.clickSort("Назва"); // TanStack cycles DESC -> unsorted; API defaults to ASC.
        dictionary.expectNames(fixture.ascendingNames());
        var byDate = fixture.list(Map.of("sort", "createdAt,ASC")).getContent().stream()
                .map(EquipmentModelResponse::getName).toList();
        dictionary.clickSort("Створено");
        dictionary.expectNames(byDate);
        dictionary.clickSort("Створено");
        List<String> byDateDesc = fixture.list(Map.of("sort", "createdAt,DESC")).getContent().stream()
                .map(EquipmentModelResponse::getName).toList();
        dictionary.expectNames(byDateDesc);
        dictionary.attachScreenshot("TC-EQD-UI-004 — сортування");
    }

    @Test
    @TestCaseId("TC-EQD-UI-005")
    @Description("Пошук повної назви, підрядка в іншому регістрі, відсутній збіг та очищення поля відновлюють результати.")
    public void searchesNameAndClearsEmptyResult() {
        var dictionary = new EquipmentDictionaryPage(page).open().search(fixture.alpha);
        dictionary.expectNames(List.of(fixture.alpha));
        dictionary.search(fixture.marker.toUpperCase(Locale.ROOT)).expectNames(fixture.ascendingNames());
        dictionary.search(fixture.marker + "-missing").expectNames(List.of());
        assertThat(page.getByText("Немає записів", new com.microsoft.playwright.Page.GetByTextOptions().setExact(true))).isVisible();
        dictionary.search("");
        assertThat(dictionary.rows()).not().hasCount(0);
        dictionary.search(fixture.marker).expectNames(fixture.ascendingNames());
        dictionary.attachScreenshot("TC-EQD-UI-005 — пошук та очищення");
    }

    @Test
    @TestCaseId("TC-EQD-UI-006")
    @Description("Одна й кілька категорій, категорія без обладнання, назва AND категорія; скидання відновлює записи.")
    public void filtersCategoriesAndCombinesWithName() {
        var dictionary = new EquipmentDictionaryPage(page).open().search(fixture.marker);
        dictionary.toggleCategory(fixture.categoryA()).expectNames(List.of(fixture.alpha, fixture.ghe));
        dictionary.toggleCategory(fixture.categoryB()).expectNames(fixture.ascendingNames());
        dictionary.clearCategories().expectNames(fixture.ascendingNames());
        dictionary.toggleCategory(fixture.unusedCategory()).expectNames(List.of());
        dictionary.clearCategories().expectNames(fixture.ascendingNames());
        dictionary.search(fixture.ghe).toggleCategory(fixture.categoryB()).expectNames(List.of());
        dictionary.clearCategories().expectNames(List.of(fixture.ghe));
        dictionary.toggleCategory(fixture.categoryA()).expectNames(List.of(fixture.ghe));
        dictionary.attachScreenshot("TC-EQD-UI-006 — перетин фільтрів");
    }
}
