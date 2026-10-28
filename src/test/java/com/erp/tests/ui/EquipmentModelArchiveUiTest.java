package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.EquipmentStatus;
import com.erp.enums.UserRole;
import com.erp.fixtures.EquipmentModelArchiveFixture;
import com.erp.pages.EquipmentCreatePage;
import com.erp.pages.EquipmentDictionaryPage;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.*;
import org.testng.annotations.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Epic("Equipment") @Feature("Архівація моделі обладнання")
public class EquipmentModelArchiveUiTest extends BaseUITest {
    private EquipmentModelArchiveFixture fixture;
    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareArchive() {
        fixture = new EquipmentModelArchiveFixture(testContext, apiExecutor);
        fixture.prepare();
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
        injectWorkspaceView(UserRole.ADMIN, fixture.storageId);
    }
    @AfterClass(alwaysRun = true)
    public void cleanupArchive() { if (fixture != null) fixture.cleanup(); }

    @Test @TestCaseId("TC-EQA-UI-001")
    @Description("Початково обрано ACTIVE; архівна модель відсутня. Перевірка фактичних рядків та query status.")
    public void defaultsToActiveModels() {
        var active = fixture.create("default-active", EquipmentStatus.AVAILABLE);
        var archived = fixture.create("default-archived", EquipmentStatus.RETIRED);
        assertThat(fixture.archive(archived).statusCode()).isEqualTo(204);
        List<String> requests = new ArrayList<>();
        page.onRequest(r -> { if (r.url().contains("/equipment/models?")) requests.add(r.url()); });
        var dictionary = new EquipmentDictionaryPage(page).open().search(fixture.marker + " default-");
        assertThat(page.getByTestId("equipment-models-filter-status")).hasText("Активна");
        dictionary.expectNames(List.of(active.name()));
        assertThat(requests).isNotEmpty().allSatisfy(url -> assertThat(url).contains("status=ACTIVE"));
        attachScreenshot("TC-EQA-UI-001 — типовий активний статус");
    }

    @Test @TestCaseId("TC-EQA-UI-002")
    @Description("Активні/Архівні/Всі; пошук AND категорія зберігаються, порожній результат і повернення до активних.")
    public void switchesStatusAndCombinesFilters() {
        var active = fixture.create("switch-active", EquipmentStatus.AVAILABLE);
        var archived = fixture.create("switch-archived", EquipmentStatus.RETIRED);
        assertThat(fixture.archive(archived).statusCode()).isEqualTo(204);
        var dictionary = new EquipmentDictionaryPage(page).open().search(fixture.marker + " switch-")
                .toggleCategory(fixture.categoryId);
        dictionary.expectNames(List.of(active.name()));
        dictionary.status("archived").expectNames(List.of(archived.name()));
        assertThat(dictionary.archiveButton(archived.modelId())).hasCount(0);
        dictionary.status("all").expectNames(List.of(active.name(), archived.name()));
        dictionary.search(archived.name()).status("active").expectNames(List.of());
        dictionary.status("archived").expectNames(List.of(archived.name()));
        dictionary.search(fixture.marker + " switch-").status("active").expectNames(List.of(active.name()));
        attachScreenshot("TC-EQA-UI-002 — статус і комбіновані фільтри");
    }

    @Test @TestCaseId("TC-EQA-UI-003")
    @Description("Скасування діалогу архівації не надсилає PUT та залишає модель ACTIVE.")
    public void cancelsArchiveWithoutMutation() {
        var sample = fixture.create("cancel", EquipmentStatus.RETIRED);
        var dictionary = new EquipmentDictionaryPage(page).open().search(sample.name());
        List<String> writes = new ArrayList<>();
        page.onRequest(r -> { if (r.url().endsWith("/archive") && r.method().equals("PUT")) writes.add(r.url()); });
        dictionary.openArchive(sample.modelId());
        assertThat(page.getByRole(AriaRole.ALERTDIALOG)).containsText(sample.name());
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Скасувати").setExact(true)).click();
        assertThat(page.getByRole(AriaRole.ALERTDIALOG)).hasCount(0);
        dictionary.expectNames(List.of(sample.name()));
        assertThat(fixture.model(sample).getStatus()).isEqualTo("ACTIVE");
        assertThat(writes).isEmpty();
        attachScreenshot("TC-EQA-UI-003 — скасування");
    }

    @Test @TestCaseId("TC-EQA-UI-004")
    @Description("Кнопка й підтвердження виконують реальний PUT 204; рядок зникає з активних, є в архіві після reload.")
    public void confirmsArchiveAndPersistsIt() {
        var sample = fixture.create("confirm", EquipmentStatus.RETIRED);
        var dictionary = new EquipmentDictionaryPage(page).open().search(sample.name());
        assertThat(dictionary.archiveButton(sample.modelId())).isEnabled();
        dictionary.openArchive(sample.modelId());
        assertThat(dictionary.confirmArchive(sample.modelId()).status()).isEqualTo(204);
        dictionary.expectNames(List.of());
        assertThat(fixture.model(sample).getStatus()).isEqualTo("ARCHIVED");
        dictionary.status("archived").expectNames(List.of(sample.name()));
        dictionary.awaitData(page::reload);
        dictionary.search(sample.name()).status("archived").expectNames(List.of(sample.name()));
        assertThat(dictionary.archiveButton(sample.modelId())).hasCount(0);
        attachScreenshot("TC-EQA-UI-004 — архівна модель");
    }

    @Test @TestCaseId("TC-EQA-UI-005")
    @Description("Несписана одиниця та одиниця в дорозі показують disabled архівацію, кількість 1 і пояснення.")
    public void stockAndTransitDisableArchive() {
        var stock = fixture.create("disabled-stock", EquipmentStatus.AVAILABLE);
        var transit = fixture.create("disabled-transit", EquipmentStatus.AVAILABLE);
        fixture.send(transit);
        var dictionary = new EquipmentDictionaryPage(page).open().search(fixture.marker + " disabled-");
        for (var sample : List.of(stock, transit)) {
            assertThat(dictionary.archiveButton(sample.modelId())).isDisabled();
            assertThat(dictionary.row(sample.modelId()).locator("td").nth(2)).hasText("1");
            page.getByTestId("equipment-models-row-" + sample.modelId() + "-archive-hint").hover();
            assertThat(page.getByRole(AriaRole.TOOLTIP)).containsText("Модель використовується в 1 не списаному обладнанні");
            assertThat(fixture.model(sample).getStatus()).isEqualTo("ACTIVE");
        }
        attachScreenshot("TC-EQA-UI-005 — блокування архівації");
    }

    @Test @TestCaseId("TC-EQA-UI-006")
    @Description("Реальний автокомпліт і suggestions містять активний контроль, виключають архівну модель, точний пошук теж порожній.")
    public void autocompleteExcludesArchivedModels() {
        var active = fixture.create("autocomplete-active", EquipmentStatus.AVAILABLE);
        var archived = fixture.create("autocomplete-archived", EquipmentStatus.RETIRED);
        assertThat(fixture.archive(archived).statusCode()).isEqualTo(204);
        new EquipmentCreatePage(page).openForStorage(null);
        Locator input = page.locator("[data-slot='card']").first().locator("input[autocomplete='off']").first();
        var response = page.waitForResponse(r -> r.url().contains("/equipment/suggestions?")
                && r.request().method().equals("GET"), () -> input.fill(fixture.marker + " autocomplete-"));
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.text()).contains(active.name()).doesNotContain(archived.name());
        Locator activeOption = page.getByRole(AriaRole.BUTTON).filter(new Locator.FilterOptions().setHasText(active.name()));
        assertThat(activeOption).isVisible();
        assertThat(page.getByRole(AriaRole.BUTTON).filter(new Locator.FilterOptions().setHasText(archived.name()))).hasCount(0);
        activeOption.click();
        assertThat(input).hasValue(active.name());
        response = page.waitForResponse(r -> r.url().contains("/equipment/suggestions?")
                && r.request().method().equals("GET"), () -> input.fill(archived.name()));
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.text().trim()).isEqualTo("[]");
        assertThat(page.getByRole(AriaRole.BUTTON).filter(new Locator.FilterOptions().setHasText(archived.name()))).hasCount(0);
        attachScreenshot("TC-EQA-UI-006 — архів відсутній в автокомпліті");
    }

    @Test @TestCaseId("TC-EQA-UI-007")
    @Description("Після відкриття підтвердження списану одиницю відновлено: сервер відмовляє, UI показує помилку, модель ACTIVE.")
    public void revalidatesStockAtConfirmation() {
        var sample = fixture.create("race", EquipmentStatus.RETIRED);
        var dictionary = new EquipmentDictionaryPage(page).open().search(sample.name());
        dictionary.openArchive(sample.modelId());
        fixture.retireOrChange(sample, EquipmentStatus.AVAILABLE);
        assertThat(dictionary.confirmArchive(sample.modelId()).status()).isEqualTo(400);
        assertThat(page.getByText("Модель використовується обладнанням — архівація неможлива.",
                new Page.GetByTextOptions().setExact(true))).isVisible();
        assertThat(fixture.model(sample).getStatus()).isEqualTo("ACTIVE");
        dictionary.open().search(sample.name()).expectNames(List.of(sample.name()));
        assertThat(dictionary.archiveButton(sample.modelId())).isDisabled();
        attachScreenshot("TC-EQA-UI-007 — актуальна серверна валідація");
    }
}
