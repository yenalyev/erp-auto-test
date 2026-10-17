package com.erp.pages;

import com.erp.fixtures.FaitaExpenseMappingFixture.Kind;
import com.erp.fixtures.FaitaExpenseMappingFixture.Row;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.assertions.LocatorAssertions;
import io.qameta.allure.Step;

import java.util.List;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** Locators traced to tk-ui FaitaMappingPage / FaitaMappingTab. Requirement gaps remain assertions. */
public class FaitaExpenseMappingPage extends BasePage {
    public FaitaExpenseMappingPage(Page page) { super(page); }

    @Step("Відкрити «Словники Файти» через сайдбар")
    public FaitaExpenseMappingPage openFromSidebar() {
        navigateTo(ConfigProvider.getBaseUrl());
        new AppSidebarPage(page).waitForSidebarLoaded();
        attachScreenshot("FAITA mapping — sidebar before navigation");
        Locator link = page.locator("[data-sidebar='sidebar'] a[href='/faita-mapping']");
        assertThat(link).hasCount(1);
        assertThat(link).containsText("Словники Файти");
        link.click();
        return this;
    }

    public FaitaExpenseMappingPage open() {
        navigateTo(ConfigProvider.getBaseUrl() + "/faita-mapping");
        assertThat(page.getByRole(AriaRole.TAB).filter(new Locator.FilterOptions().setHasText("Ініціатори"))).isVisible();
        return this;
    }

    public FaitaExpenseMappingPage tab(Kind kind) {
        String label = kind == Kind.AMMO ? "Амуніція" : "Ініціатори";
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(Pattern.compile("^" + label + "(?:\\s*\\d+)?$"))).click();
        page.locator("[data-slot='toggle-group-item']")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^Співставлені$"))).click();
        assertThat(page.getByRole(AriaRole.TABLE)).isVisible();
        return this;
    }
    public FaitaExpenseMappingPage filterAlias(String text) {
        filter("Назва Файти", "faitaName", text);
        return this;
    }
    public FaitaExpenseMappingPage filterResource(String text) {
        filter("Ресурс", "resourceName", text);
        return this;
    }
    private void filter(String placeholder, String parameter, String text) {
        Locator input = page.getByPlaceholder(placeholder, new Page.GetByPlaceholderOptions().setExact(true));
        if (input.inputValue().equals(text)) return;
        // SearchInput debounces changes. A transient empty table is not proof that the new query completed.
        Response response = page.waitForResponse(r -> {
            var uri = java.net.URI.create(r.url());
            if (!uri.getPath().matches(".*/api/v1/faita-mapping/(ammunition|initiators)(/unmapped)?")) return false;
            String query = uri.getRawQuery();
            String actual = "";
            if (query != null) for (String part : query.split("&")) {
                String[] pair = part.split("=", 2);
                if (pair[0].equals(parameter)) actual = pair.length == 2
                        ? java.net.URLDecoder.decode(pair[1], java.nio.charset.StandardCharsets.UTF_8) : "";
            }
            return actual.equals(text);
        }, () -> input.fill(text));
        if (response.status() != 200) throw new AssertionError("Mapping search returned " + response.status());
        response.finished();
    }
    public FaitaExpenseMappingPage unmatched(boolean checked) {
        Locator selected = page.locator("[data-slot='toggle-group-item']")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile(
                        checked ? "^Неспівставлені$" : "^Співставлені$")));
        selected.click();
        assertThat(selected).hasAttribute("data-state", "on");
        Locator other = page.locator("[data-slot='toggle-group-item']")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile(
                        checked ? "^Співставлені$" : "^Неспівставлені$")));
        assertThat(other).hasAttribute("data-state", "off");
        return this;
    }
    public Locator rows() { return page.getByRole(AriaRole.TABLE).locator("tbody tr:has(td)"); }
    public Locator dataRows() { return rows().filter(new Locator.FilterOptions().setHas(page.locator("td:nth-child(2)"))); }
    public void expectRows(List<Row> expected) {
        assertThat(dataRows()).hasCount(expected.size());
        if (expected.isEmpty()) return;
        assertThat(dataRows().locator("td:nth-child(1)")).hasText(expected.stream().map(Row::name).toArray(String[]::new));
        for (int i = 0; i < expected.size(); i++) {
            if (expected.get(i).resourceId() != null)
                assertThat(dataRows().nth(i).locator("td").nth(1)).hasText(expected.get(i).resourceName());
            else assertThat(dataRows().nth(i).locator("td").nth(1)).not().containsText("ERP");
        }
    }
    public void expectHeaders(Kind kind) {
        Locator headers = page.locator("table thead th");
        assertThat(headers.nth(0)).hasText("Назва Файти");
        assertThat(headers.nth(1)).hasText("Ресурс");
        if (kind == Kind.AMMO) assertThat(headers.nth(2)).hasText("Множник");
        else assertThat(headers.filter(new Locator.FilterOptions().setHasText("Множник"))).hasCount(0);
    }
    public void expectUnmatchedHeaders(Kind kind) {
        Locator headers = page.locator("table thead th");
        assertThat(headers.nth(0)).hasText("Назва Файти");
        if (kind == Kind.AMMO) assertThat(headers.nth(1)).hasText("Виробник");
        else assertThat(headers.filter(new Locator.FilterOptions().setHasText("Виробник"))).hasCount(0);
        assertThat(headers.nth(kind == Kind.AMMO ? 2 : 1)).hasText("Остання витрата");
        for (String excluded : List.of("Ресурс", "Множник"))
            assertThat(headers.filter(new Locator.FilterOptions().setHasText(excluded))).hasCount(0);
    }
    @Step("Переключити сортування колонки {column} та перевірити обидва ключі")
    public void sortAndExpect(String column, List<Row> expected) {
        AssertionError last = null;
        // Supports both an initial unsorted state and an explicit default ASC state.
        for (int attempt = 0; attempt < 3; attempt++) {
            Locator header = page.locator("table thead th")
                    .filter(new Locator.FilterOptions().setHasText(column));
            // Deployed DataTable binds sorting to th; the current source nests a button.
            Locator button = header.getByRole(AriaRole.BUTTON);
            if (button.count() > 0) button.click(); else header.click();
            try {
                assertThat(dataRows().locator("td:nth-child(1)"))
                        .hasText(expected.stream().map(Row::name).toArray(String[]::new),
                                new LocatorAssertions.HasTextOptions().setTimeout(2000));
                assertThat(dataRows().locator("td:nth-child(2)"))
                        .hasText(expected.stream().map(Row::resourceName).toArray(String[]::new),
                                new LocatorAssertions.HasTextOptions().setTimeout(2000));
                return;
            } catch (AssertionError error) { last = error; }
        }
        throw last;
    }
    public void openEdit(int rowIndex) {
        Locator edit = dataRows().nth(rowIndex).getByRole(AriaRole.BUTTON)
                .filter(new Locator.FilterOptions().setHas(page.locator("svg.lucide-pencil, svg.lucide-pencil-line")));
        assertThat(edit).hasCount(1);
        edit.click();
        assertThat(page.getByRole(AriaRole.DIALOG)).isVisible();
    }
    public void expectEditTarget(Row expected, Kind kind) {
        Locator dialog = page.getByRole(AriaRole.DIALOG);
        assertThat(dialog.getByText(expected.name(), new Locator.GetByTextOptions().setExact(true))).isVisible();
        assertThat(dialog.getByRole(AriaRole.COMBOBOX)).containsText(expected.resourceName());
        if (kind == Kind.AMMO) assertThat(dialog.locator("#faita-alias-multiplier"))
                .hasValue(expected.multiplier().stripTrailingZeros().toPlainString());
        else assertThat(dialog.locator("#faita-alias-multiplier")).hasCount(0);
    }
    public Locator sourceRow(long sourceId) {
        return dataRows().filter(new Locator.FilterOptions().setHas(page.locator("td:first-child")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("\\(" + sourceId + "\\)\\s*$")))));
    }
    public void openCreate(long sourceId, String name, Kind kind) {
        Locator row = sourceRow(sourceId);
        assertThat(row).hasCount(1);
        row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Співставити").setExact(true)).click();
        Locator dialog = page.getByRole(AriaRole.DIALOG);
        assertThat(dialog).containsText(name);
        assertThat(dialog).containsText("(" + sourceId + ")");
        assertThat(dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Зберегти").setExact(true))).isDisabled();
        if (kind == Kind.AMMO) assertThat(dialog.locator("#faita-alias-multiplier")).hasValue("1");
        else assertThat(dialog.locator("#faita-alias-multiplier")).hasCount(0);
    }
    public void fillCreationResource(String resourceName, Kind kind) {
        Locator dialog = page.getByRole(AriaRole.DIALOG,
                new Page.GetByRoleOptions().setName("Співставлення з ресурсом").setExact(true));
        // The autocomplete popup also exposes a combobox input while its closing animation runs.
        Locator selectedResource = dialog.locator("button[role='combobox']");
        selectedResource.click();
        page.getByPlaceholder("Пошук...", new Page.GetByPlaceholderOptions().setExact(true)).fill(resourceName);
        Locator option = page.getByRole(AriaRole.OPTION).filter(new Locator.FilterOptions().setHasText(resourceName));
        assertThat(option).hasCount(1);
        option.click();
        assertThat(selectedResource).containsText(resourceName);
        if (kind == Kind.AMMO) dialog.locator("#faita-alias-multiplier").fill("1.75");
        assertThat(dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Зберегти").setExact(true))).isEnabled();
    }
    public void cancelCreation() {
        page.getByRole(AriaRole.DIALOG).getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Скасувати").setExact(true)).click();
        assertThat(page.getByRole(AriaRole.DIALOG)).hasCount(0);
    }
    public void saveCreation() {
        page.getByRole(AriaRole.DIALOG).getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Зберегти").setExact(true)).click();
    }
    public void openDelete(long sourceId, String name, String resourceName) {
        Locator button = sourceRow(sourceId).getByRole(AriaRole.BUTTON)
                .filter(new Locator.FilterOptions().setHas(page.locator("svg.lucide-trash-2, svg.lucide-trash")));
        assertThat(button).hasCount(1);
        button.click();
        Locator dialog = page.getByRole(AriaRole.ALERTDIALOG);
        assertThat(dialog).containsText("Видалити співставлення?");
        assertThat(dialog).containsText(name);
        assertThat(dialog).containsText(resourceName);
    }
    public void deleteDecision(boolean confirm) {
        page.getByRole(AriaRole.ALERTDIALOG).getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(confirm ? "Видалити" : "Скасувати").setExact(true)).click();
        assertThat(page.getByRole(AriaRole.ALERTDIALOG)).hasCount(0);
    }
}
