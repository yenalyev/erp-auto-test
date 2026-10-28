package com.erp.pages;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Real combobox interaction; checks supplier serialization in both table and totals requests. */
public class RelocationSupplierFiltersPage extends RelocationFiltersPage {
    private final List<String> selected = new ArrayList<>();
    private boolean equipmentMode;

    public RelocationSupplierFiltersPage(Page page) { super(page); }

    @Override
    public RelocationSupplierFiltersPage open(String tab) {
        super.open(tab);
        selected.clear();
        equipmentMode = false;
        return this;
    }

    public List<String> searchSuppliers(String search) {
        Locator input = field("Постачальник").getByRole(AriaRole.COMBOBOX);
        if (!"true".equals(input.getAttribute("aria-expanded"))) input.click();
        page.waitForCondition(() -> "true".equals(input.getAttribute("aria-expanded")));
        // Filling an already empty Base UI input sends Delete and can remove the last chip.
        if (!input.inputValue().equals(search)) input.fill(search);
        // Base UI keeps the listbox mounted but hidden for an empty search result.
        Locator list = page.locator("[role='listbox']").last();
        list.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
        page.waitForCondition(() -> search.isBlank() ? list.getByRole(AriaRole.OPTION).count() > 0
                : list.getAttribute("data-empty") != null || list.getByRole(AriaRole.OPTION).allInnerTexts()
                .stream().allMatch(value -> value.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))));
        return list.getByRole(AriaRole.OPTION).allInnerTexts()
                .stream().map(String::trim).toList();
    }

    public void closeSuppliers() {
        Locator input = field("Постачальник").getByRole(AriaRole.COMBOBOX);
        if ("true".equals(input.getAttribute("aria-expanded"))) {
            page.keyboard().press("Escape");
            page.waitForCondition(() -> !"true".equals(input.getAttribute("aria-expanded")));
        }
        field("Дата відправки").locator("label").click();
    }

    public void selectSupplier(String supplier) {
        searchSuppliers("");
        selected.add(supplier);
        supplierAction(selected, () -> page.getByRole(AriaRole.OPTION,
                new Page.GetByRoleOptions().setName(supplier).setExact(true)).click());
        page.waitForCondition(() -> new HashSet<>(chips()).equals(new HashSet<>(selected)));
        closeSuppliers();
        assertThat(chips()).containsExactlyInAnyOrderElementsOf(selected);
    }

    public List<String> chips() {
        return field("Постачальник").locator("[data-slot='combobox-chip']").allInnerTexts()
                .stream().map(String::trim).toList();
    }

    public void assertVisibleTotal(String unit, int amount) {
        Locator title = page.getByRole(AriaRole.TABPANEL).getByText("Всього:",
                new Locator.GetByTextOptions().setExact(true));
        if (amount == 0) {
            page.waitForCondition(() -> title.count() == 0);
        } else {
            Locator chip = title.locator("..").locator("span[title]")
                    .filter(new Locator.FilterOptions().setHas(page.getByText(unit,
                            new Page.GetByTextOptions().setExact(true))));
            page.waitForCondition(() -> chip.count() == 1
                    && chip.locator("span").first().innerText().trim().equals(String.valueOf(amount)));
        }
    }

    public void removeSupplier(String supplier) {
        Locator chip = field("Постачальник").locator("[data-slot='combobox-chip']")
                .filter(new Locator.FilterOptions().setHasText(supplier));
        selected.remove(supplier);
        supplierAction(selected, () -> chip.locator("[data-slot='combobox-chip-remove']").click());
        closeSuppliers();
        assertThat(chips()).containsExactlyInAnyOrderElementsOf(selected);
    }

    @Override
    public void type(String type) {
        equipmentMode = type.equals("EQUIPMENT");
        supplierAction(equipmentMode ? List.of() : selected, () -> super.type(type));
    }

    @Override
    public byte[] export() {
        final byte[][] bytes = new byte[1][];
        Response response = page.waitForResponse(result -> path(result).endsWith("/relocations/export"),
                () -> bytes[0] = super.export());
        assertSuppliers(response, equipmentMode ? List.of() : selected);
        return bytes[0];
    }

    private void supplierAction(List<String> suppliers, Runnable action) {
        final Response[] totals = new Response[1];
        Response list = page.waitForResponse(response -> matches(response, "/api/v1/relocations", suppliers),
                () -> totals[0] = page.waitForResponse(response ->
                        matches(response, "/api/v1/relocations/totals", suppliers), action));
        for (Response response : List.of(list, totals[0])) {
            assertThat(response.status()).as("Supplier request %s", path(response)).isEqualTo(200);
            response.finished();
            assertSuppliers(response, suppliers);
        }
    }

    private boolean matches(Response response, String endpoint, List<String> suppliers) {
        return path(response).endsWith(endpoint) && new HashSet<>(suppliers(response.url())).equals(new HashSet<>(suppliers));
    }

    private void assertSuppliers(Response response, List<String> expected) {
        assertThat(suppliers(response.url())).as("Exact supplier query: %s", response.url())
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    private String path(Response response) { return URI.create(response.url()).getPath(); }

    private List<String> suppliers(String url) {
        List<String> values = new ArrayList<>();
        String query = URI.create(url).getRawQuery();
        if (query != null) for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8).replace("[]", "");
            if (key.equals("suppliers")) values.add(URLDecoder.decode(parts.length > 1 ? parts[1] : "",
                    StandardCharsets.UTF_8));
        }
        return values;
    }
}
