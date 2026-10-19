package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.NonSeriesProductionStatus;
import com.erp.enums.UserRole;
import com.erp.fixtures.NonSeriesResourceFilterFixture;
import com.erp.pages.NonSeriesProductionListPage;
import com.erp.utils.helpers.XlsxWorkbookReader;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.*;
import org.testng.annotations.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Epic("Non-Series Production")
@Feature("Фільтр за використаною сировиною")
@Story("Пошук за матеріалами")
@Severity(SeverityLevel.CRITICAL)
public class NonSeriesResourceFilterUiTest extends BaseUITest {
    private NonSeriesResourceFilterFixture fixture;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareFilterData() {
        fixture = new NonSeriesResourceFilterFixture(testContext, apiExecutor);
        fixture.prepare();
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
        injectWorkspaceView(UserRole.ADMIN, fixture.storageId);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupFilterData() { if (fixture != null) fixture.cleanup(); }

    private NonSeriesProductionListPage openWithAllDates() {
        return new NonSeriesProductionListPage(page).open().filterByResource(fixture.materialTerm)
                .clearAllFilters(fixture.materialTerm).expectProducts(fixture.allProducts())
                .waitForDisplayedTotalVolume(BigDecimal.valueOf(28));
    }

    @Test
    @TestCaseId("TC-NSP-RF-UI-001")
    @Description("resourceSearch у списку й /total: повна назва, підрядок та інший регістр; точні рядки та об'єм.")
    public void searchesUsedMaterialAndUpdatesTotal() {
        var journal = openWithAllDates();
        assertThat(journal.resourceSearchInput()).hasAttribute("placeholder", "Назва матеріалу...");
        journal.filterByResource(fixture.materialA.getName()).expectProducts(fixture.matchingProducts())
                .waitForDisplayedTotalVolume(BigDecimal.valueOf(16));
        journal.filterByResource(fixture.materialTerm.toUpperCase(Locale.ROOT))
                .expectProducts(fixture.matchingProducts()).waitForDisplayedTotalVolume(BigDecimal.valueOf(16));
        journal.attachScreenshot("TC-NSP-RF-UI-001 — точні рядки та об'єм 16");
    }

    @Test
    @TestCaseId("TC-NSP-RF-UI-002")
    @Description("Без збігів — Записів не знайдено й 0. Очищення матеріалу зберігає назву виробу; "
            + "загальна Очистити скидає обидва поля та статус і повертає всі записи.")
    public void handlesNoMatchesAndClearsFilters() {
        var journal = openWithAllDates();
        journal.filterByResource(fixture.materialTerm + "-missing").expectProducts(List.of())
                .waitForDisplayedTotalVolume(BigDecimal.ZERO);
        journal.attachScreenshot("TC-NSP-RF-UI-002 — порожня вибірка");
        journal.filterByResource("").expectProducts(fixture.allProducts())
                .waitForDisplayedTotalVolume(BigDecimal.valueOf(28));
        journal.awaitResourceFilter("", () -> page.getByPlaceholder("Назва продукту...")
                .fill(fixture.alpha.getProduct())).expectProducts(List.of(fixture.alpha.getProduct()));
        journal.filterByResource(fixture.materialB.getName()).expectProducts(List.of())
                .waitForDisplayedTotalVolume(BigDecimal.ZERO);
        journal.filterByResource("").expectProducts(List.of(fixture.alpha.getProduct()))
                .waitForDisplayedTotalVolume(BigDecimal.valueOf(2));
        assertThat(page.getByPlaceholder("Назва продукту...")).hasValue(fixture.alpha.getProduct());
        journal.filterByResource(fixture.materialTerm);
        journal.awaitResourceFilter(fixture.materialTerm, () -> journal.filterByStatus("В роботі"));
        journal.clearAllFilters(fixture.materialTerm).expectProducts(fixture.allProducts())
                .waitForDisplayedTotalVolume(BigDecimal.valueOf(28));
        assertThat(journal.resourceSearchInput()).hasValue("");
        assertThat(page.getByPlaceholder("Назва продукту...")).hasValue("");
        journal.attachScreenshot("TC-NSP-RF-UI-002 — фільтри очищено");
    }

    @Test
    @TestCaseId("TC-NSP-RF-UI-003")
    @Description("Матеріал AND назва виробу AND статус обмежують рядки й об'єм; Excel містить той самий виріб "
            + "та всю його сировину. Перевіряється фактичний файл, завантажений UI.")
    public void combinesFiltersAndExportsMatchingRows() throws Exception {
        var journal = openWithAllDates().filterByResource(fixture.materialTerm);
        journal.awaitResourceFilter(fixture.materialTerm, () -> journal.filterByStatus("Завершено"))
                .expectProducts(List.of(fixture.both.getProduct())).waitForDisplayedTotalVolume(BigDecimal.valueOf(3));
        journal.awaitResourceFilter(fixture.materialTerm,
                () -> page.getByPlaceholder("Назва продукту...").fill(fixture.both.getProduct()))
                .expectProducts(List.of(fixture.both.getProduct()));
        journal.attachScreenshot("TC-NSP-RF-UI-003 — поєднані фільтри перед експортом");
        var rows = XlsxWorkbookReader.firstSheet(journal.exportFiltered(fixture.materialTerm));
        int product = XlsxWorkbookReader.columnIndex(rows.getFirst(), "Продукт");
        int material = XlsxWorkbookReader.columnIndex(rows.getFirst(), "Використана сировина");
        var data = rows.subList(1, rows.size());
        assertThat(data.stream().map(row -> row.get(product)).filter(value -> !value.isBlank()).toList())
                .containsExactly(fixture.both.getProduct());
        assertThat(data.stream().map(row -> row.get(material)).toList()).containsExactlyInAnyOrder(
                fixture.materialA.getName(), fixture.materialA2.getName(), fixture.materialB.getName());
    }

    @Test(priority = 100)
    @TestCaseId("TC-NSP-RF-UI-004")
    @Description("Введення матеріалу з другої сторінки повертає на першу: три вироби, Сторінка 1 з 1, об'єм 16.")
    public void resetsPaginationWhenMaterialChanges() {
        for (int i = 0; i < 21; i++) {
            fixture.create(fixture.storageId, "Додатковий " + fixture.marker + " " + i, 1,
                    NonSeriesProductionStatus.IN_PROGRESS, fixture.today);
        }
        var journal = new NonSeriesProductionListPage(page).open().filterByResource(fixture.materialTerm)
                .clearAllFilters(fixture.materialTerm);
        page.getByRole(AriaRole.COMBOBOX).last().click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName("25").setExact(true)).click();
        assertThat(page.getByText("Сторінка 1 з 2", new Page.GetByTextOptions().setExact(true))).isVisible();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Далі").setExact(true)).click();
        assertThat(page.getByText("Сторінка 2 з 2", new Page.GetByTextOptions().setExact(true))).isVisible();
        journal.filterByResource(fixture.materialTerm).expectProducts(fixture.matchingProducts())
                .waitForDisplayedTotalVolume(BigDecimal.valueOf(16));
        assertThat(page.getByText("Сторінка 1 з 1", new Page.GetByTextOptions().setExact(true))).isVisible();
        journal.attachScreenshot("TC-NSP-RF-UI-004 — фільтр повернув першу сторінку");
    }
}
