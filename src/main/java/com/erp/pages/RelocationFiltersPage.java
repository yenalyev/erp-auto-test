package com.erp.pages;

import com.erp.pages.components.DateRangePickerComponent;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Shared filter bar deployed on all four relocation tabs. No intercepted or mocked responses. */
public class RelocationFiltersPage extends BasePage {
    private final Map<String, String> expected = new LinkedHashMap<>();
    private List<String> expectedStates = List.of();

    public RelocationFiltersPage(Page page) { super(page); }

    public RelocationFiltersPage open(String tab) {
        page.navigate(ConfigProvider.getBaseUrl() + "/relocations");
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("В дорозі")).waitFor();
        // Initial authentication/workspace requests can remount the journal and
        // discard an early tab click. Finish that bootstrap before choosing it.
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        String label = switch (tab) {
            case "active" -> "В дорозі";
            case "sent" -> "Видано";
            case "received" -> "Отримано";
            case "lost" -> "Втрачено";
            default -> throw new IllegalArgumentException(tab);
        };
        Locator selectedTab = page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(label).setExact(true));
        selectedTab.click();
        page.waitForCondition(() -> "true".equals(selectedTab.getAttribute("aria-selected")));
        page.getByRole(AriaRole.TABPANEL).waitFor();
        expected.clear();
        expected.put("type", "RESOURCE");
        expectedStates = switch (tab) {
            case "active" -> List.of("CREATED", "CANCELLED");
            case "lost" -> List.of("LOST");
            default -> List.of("FINISHED", "AUTO_FINISHED", "RETURNED");
        };
        field("Від кого").waitFor();
        return this;
    }

    public boolean hasField(String label) { return field(label).count() == 1 && field(label).isVisible(); }

    /** Search the real location tree and unfold matching branches, including older UI builds. */
    public List<String> searchLocationOptions(String label, String query) {
        Locator trigger = field(label).getByRole(AriaRole.COMBOBOX);
        if (!"true".equals(trigger.getAttribute("aria-expanded"))) trigger.click();
        Locator tree = popover();
        tree.locator("input").fill(query);
        Locator collapsed = tree.locator("span[role='button']")
                .filter(new Locator.FilterOptions().setHas(page.locator("svg:not(.rotate-90)")));
        for (int depth = 0; depth < 30 && collapsed.count() > 0; depth++) collapsed.first().click();
        // Dev also uses a flat checkbox list for counterparties; both controls expose
        // the option name as its own line, but only the tree uses button rows.
        return tree.innerText().lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
    }

    public void waitForLocationOption(String name) {
        popover().getByText(name, new Locator.GetByTextOptions().setExact(true)).waitFor();
    }

    public void closeLocationOptions() { page.keyboard().press("Escape"); }

    public void assertDefaultResourceMode() {
        assertThat(field("Тип").locator("[data-slot='toggle-group-item']")
                .filter(new Locator.FilterOptions().setHasText("Ресурси")).getAttribute("data-state"))
                .isEqualTo("on");
        assertThat(hasField("Категорія")).isTrue();
        assertThat(hasField("Виріб")).isTrue();
        assertThat(hasField("Дата відправки")).isTrue();
        assertThat(hasField("Від кого")).isTrue();
        assertThat(hasField("Кому")).isTrue();
    }

    public void location(String label, String name, Long id) {
        field(label).locator("button").first().click();
        Locator popover = popover();
        popover.locator("input").fill(name);
        Locator option = popover.getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(name).setExact(true));
        // The deployed tree keeps ancestor branches folded during search. Expand their
        // disclosure controls as a user does; clicking the row itself would select a parent.
        for (int depth = 0; depth < 12 && !option.isVisible(); depth++) {
            Locator disclosure = popover.locator("span[role='button']")
                    .filter(new Locator.FilterOptions().setHas(page.locator("svg:not(.rotate-90)"))).first();
            disclosure.waitFor();
            disclosure.click();
        }
        expected.put(label.equals("Від кого") ? "fromIds" : "toIds", id.toString());
        listAction(option::click);
        page.keyboard().press("Escape");
    }

    public void category(String name, Long id) {
        field("Категорія").locator("button").first().click();
        expected.put("category", id.toString());
        listAction(() -> popover().getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(name).setExact(true)).click());
    }

    public void clearCategory() {
        field("Категорія").locator("button").first().click();
        expected.remove("category");
        listAction(() -> popover().getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Усі категорії").setExact(true)).click());
    }

    public void product(String name, Long id) { autocomplete("Виріб", name, id, "productIds"); }
    public void equipment(String name, Long id) { autocomplete("Обладнання", name, id, "equipmentModelIds"); }

    public void historyStatus(String label) {
        expectedStates = switch (label) {
            case "Завершено" -> List.of("FINISHED", "AUTO_FINISHED");
            case "Повернено" -> List.of("RETURNED");
            case "Усі" -> List.of("FINISHED", "AUTO_FINISHED", "RETURNED");
            default -> throw new IllegalArgumentException(label);
        };
        field("Статус").getByRole(AriaRole.COMBOBOX).click();
        listAction(() -> page.getByRole(AriaRole.OPTION,
                new Page.GetByRoleOptions().setName(label).setExact(true)).click());
    }

    private void autocomplete(String label, String name, Long id, String parameter) {
        field(label).locator("button").first().click();
        popover().getByRole(AriaRole.COMBOBOX).fill(name);
        Locator option = popover().getByRole(AriaRole.OPTION,
                new Locator.GetByRoleOptions().setName(name).setExact(true));
        option.waitFor();
        expected.put(parameter, id.toString());
        listAction(option::click);
        page.keyboard().press("Escape");
    }

    public void type(String type) {
        expected.put("type", type);
        if (type.equals("EQUIPMENT")) {
            expected.remove("category");
            expected.remove("productIds");
        } else expected.remove("equipmentModelIds");
        listAction(() -> field("Тип").locator("[data-slot='toggle-group-item']")
                .filter(new Locator.FilterOptions().setHasText(type.equals("RESOURCE") ? "Ресурси" : "Обладнання"))
                .click());
    }

    public void dates(LocalDate from, LocalDate to) {
        expected.put("startDate", from.toString());
        expected.put("endDate", to.toString());
        DateRangePickerComponent picker = new DateRangePickerComponent(page, field("Дата відправки"), 30_000);
        listAction(() -> {
            // The current picker applies its two calendars explicitly; its preset also
            // selects and applies both bounds, including a same-day range.
            if (from.equals(to) && from.equals(picker.browserToday())) {
                picker.selectPreset(DateRangePickerComponent.PRESET_1_DAY);
            } else if (to.equals(picker.browserToday()) && from.equals(to.minusDays(7))) {
                picker.selectPreset(DateRangePickerComponent.PRESET_7_DAYS);
            } else picker.setRange(from, to);
        });
    }

    public void assertCargo(String marker, Map<String, Integer> names, int relocationCount) {
        Locator panel = page.getByRole(AriaRole.TABPANEL);
        try {
            page.waitForCondition(() -> panel.locator("tbody tr").filter(new Locator.FilterOptions()
                    .setHasText(marker)).count() == relocationCount && names.entrySet().stream()
                    .allMatch(entry -> cargoRows(panel, entry.getKey()).count() == entry.getValue()));
        } catch (TimeoutError error) {
            throw new AssertionError("Rendered cargo did not match " + names + "; expected relocations="
                    + relocationCount + "; rows=" + panel.locator("tbody tr").allInnerTexts(), error);
        }
        assertThat(panel.locator("tbody tr").filter(new Locator.FilterOptions().setHasText(marker)).count())
                .isEqualTo(relocationCount);
        names.forEach((name, count) -> assertThat(cargoRows(panel, name).count())
                .as("Rendered cargo %s", name).isEqualTo(count));
    }

    private Locator cargoRows(Locator panel, String name) {
        // Resource labels end in ':'; equipment labels append '(serial number)'.
        // Each fixture cargo appears once per relocation, so inspect its complete row text.
        return panel.locator("tbody tr").filter(new Locator.FilterOptions().setHasText(name));
    }

    public byte[] export() {
        final Response[] observed = new Response[1];
        Download download = page.waitForDownload(() -> observed[0] = page.waitForResponse(
                response -> URI.create(response.url()).getPath().endsWith("/relocations/export"),
                () -> page.getByRole(AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Експорт в Excel").setExact(true)).click()));
        assertThat(observed[0].status()).isEqualTo(200);
        Map<String, List<String>> params = parameters(observed[0].url());
        assertThat(params.get("states")).as("Active tab states in Excel")
                .containsExactlyInAnyOrderElementsOf(expectedStates);
        expected.forEach((key, value) -> assertThat(params.get(key)).as("XLSX active filter %s", key).contains(value));
        if (expected.get("type").equals("EQUIPMENT")) assertThat(params).doesNotContainKeys("category", "productIds");
        else assertThat(params).doesNotContainKey("equipmentModelIds");
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(download.path());
            io.qameta.allure.Allure.addAttachment("UI relocation Excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    new java.io.ByteArrayInputStream(bytes), "xlsx");
            return bytes;
        }
        catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }

    public Locator field(String label) {
        return page.locator("label").filter(new Locator.FilterOptions()
                .setHasText(Pattern.compile("^" + label + "\\s*$"))).locator("..");
    }

    private Locator popover() {
        return page.locator("[data-radix-popper-content-wrapper] [data-state='open']").last();
    }

    private void listAction(Runnable action) {
        Response response = page.waitForResponse(result -> {
            if (!URI.create(result.url()).getPath().endsWith("/api/v1/relocations")) return false;
            Map<String, List<String>> query = parameters(result.url());
            return query.getOrDefault("states", List.of()).containsAll(expectedStates)
                    && expected.entrySet().stream().allMatch(e ->
                    query.getOrDefault(e.getKey(), List.of()).contains(e.getValue()));
        }, action);
        assertThat(response.status()).as("Filtered list response").isEqualTo(200);
        response.finished();
    }

    private static Map<String, List<String>> parameters(String url) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        String query = URI.create(url).getRawQuery();
        if (query != null) for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8).replace("[]", "");
            String value = URLDecoder.decode(pair.length == 2 ? pair[1] : "", StandardCharsets.UTF_8);
            result.computeIfAbsent(key, ignored -> new ArrayList<>()).addAll(List.of(value.split(",")));
        }
        return result;
    }
}
