package com.erp.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import io.qameta.allure.Allure;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/** Observe real list responses while exercising the sidebar and the visible filter controls. */
public class RelocationSidebarPage extends BasePage implements AutoCloseable {
    private final List<Response> responses = new ArrayList<>();
    private final Consumer<Response> observer = response -> {
        if (URI.create(response.url()).getPath().endsWith("/api/v1/relocations")
                && response.request().method().equals("GET")) responses.add(response);
    };
    private final RelocationFiltersPage filters;

    public RelocationSidebarPage(Page page) {
        super(page);
        filters = new RelocationFiltersPage(page);
        page.onResponse(observer);
    }

    public void tab(String label) {
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(label).setExact(true)).click();
        assertThat(page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(label).setExact(true)))
                .hasAttribute("aria-selected", "true");
        filters.field("Від кого").waitFor();
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    public String filterName(String side) {
        return filters.field(side).getByRole(AriaRole.COMBOBOX).innerText().trim();
    }

    public void selectFilter(String side, String name) {
        Locator trigger = filters.field(side).getByRole(AriaRole.COMBOBOX);
        if (!"true".equals(trigger.getAttribute("aria-expanded"))) trigger.click();
        Locator tree = page.locator("[data-radix-popper-content-wrapper]");
        tree.locator("input").fill(name);
        Locator option = tree
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name).setExact(true));
        // Search auto-expands the current tree asynchronously. Wait before using the older-build
        // disclosure fallback, otherwise a click can collapse a branch that has just auto-expanded.
        for (int depth = 0; depth < 12; depth++) {
            try {
                option.waitFor(new Locator.WaitForOptions().setTimeout(1_000));
                break;
            } catch (TimeoutError error) {
                Locator collapsed = tree.locator("span[role='button']")
                        .filter(new Locator.FilterOptions().setHas(page.locator("svg:not(.rotate-90)")));
                if (collapsed.count() == 0) throw error;
                collapsed.first().click();
            }
        }
        option.click();
        filters.closeLocationOptions();
    }

    public void clearFilter(String side) {
        filters.field(side).getByRole(AriaRole.COMBOBOX).click();
        page.locator("[data-radix-popper-content-wrapper]").getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Скинути").setExact(true)).click();
        filters.closeLocationOptions();
    }

    public void assertScope(String caseId, String side, String name, Long id,
                            String marker, List<String> expectedMarkers) {
        attachScreenshot(caseId + " — " + side + " = " + (name == null ? "Усі" : name));
        Locator trigger = filters.field(side).getByRole(AriaRole.COMBOBOX);
        assertThat(trigger).hasText(name == null ? "Усі" : name);
        String otherSide = side.equals("Від кого") ? "Кому" : "Від кого";
        assertThat(filters.field(otherSide).getByRole(AriaRole.COMBOBOX)).hasText("Усі");
        String parameter = side.equals("Від кого") ? "fromIds" : "toIds";
        String otherParameter = side.equals("Від кого") ? "toIds" : "fromIds";
        page.waitForCondition(() -> responses.stream().anyMatch(response -> matchesScope(response, parameter, id)));
        Response latest = responses.stream().filter(response -> matchesScope(response, parameter, id))
                .reduce((previous, next) -> next).orElseThrow();
        latest.finished();
        assertThat(latest.status()).as("Real journal response").isEqualTo(200);
        Map<String, List<String>> query = parameters(latest.url());
        Allure.addAttachment(caseId + " — journal request", latest.url());
        if (id == null) assertThat(query).doesNotContainKey(parameter);
        else assertThat(query.get(parameter)).as("Transferred location ID").containsExactly(id.toString());
        assertThat(query).doesNotContainKeys(otherParameter, "senderIds", "receiverIds");
        assertThat(query.get("states")).containsExactlyInAnyOrder("FINISHED", "AUTO_FINISHED", "RETURNED");
        Locator rows = page.getByRole(AriaRole.TABPANEL).locator("tbody tr");
        page.waitForCondition(() -> rows.filter(new Locator.FilterOptions().setHasText(marker + "-transfer-")).count()
                == expectedMarkers.size());
        for (int i = 0; i < 3; i++) {
            String transfer = marker + "-transfer-" + i;
            assertThat(rows.filter(new Locator.FilterOptions().setHasText(transfer)))
                    .hasCount(expectedMarkers.contains(transfer) ? 1 : 0);
        }
        attachScreenshot(caseId + " — filtered control rows");
    }

    public void reload() { responses.clear(); page.reload(); }

    private static boolean matchesScope(Response response, String parameter, Long id) {
        Map<String, List<String>> query = parameters(response.url());
        return query.getOrDefault("states", List.of()).contains("FINISHED")
                && (id == null ? !query.containsKey(parameter)
                : query.getOrDefault(parameter, List.of()).equals(List.of(id.toString())));
    }

    private static Map<String, List<String>> parameters(String url) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        String query = URI.create(url).getRawQuery();
        if (query != null) for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8).replace("[]", "");
            String value = URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8);
            result.computeIfAbsent(key, ignored -> new ArrayList<>()).addAll(List.of(value.split(",")));
        }
        return result;
    }

    @Override public void close() { page.offResponse(observer); }
}
