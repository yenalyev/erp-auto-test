package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import java.util.regex.Pattern;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** Shared production-order and group-request assignment surface. */
public class ProductionGroupPlanningPage {
    private final Page page;
    private int assignedLevel;
    public ProductionGroupPlanningPage(Page page) { this.page = page; }
    public Locator button(String name) { return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true)); }
    public Locator tab(String name) { return page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(name).setExact(true)); }
    public void openOrder(long id) {
        page.navigate(ConfigProvider.getBaseUrl() + "/production-orders/" + id + "/edit");
        assertThat(tab("2. Хто буде виробляти?")).isEnabled();
        tab("2. Хто буде виробляти?").click();
    }
    public void step4() {
        assertThat(tab("4. Завдання на локації")).isEnabled();
        tab("4. Завдання на локації").click();
    }
    public Locator item(String resource) {
        return page.locator("div.flex.flex-col.gap-2.px-4.py-3")
                .filter(new Locator.FilterOptions().setHas(page.getByText(resource, new Page.GetByTextOptions().setExact(true))));
    }
    public Locator openAssignment(String resource) {
        page.waitForCondition(() -> {
            expandLevels();
            return item(resource).isVisible();
        });
        String levelText = item(resource).locator("xpath=../..").locator(":scope > button").innerText();
        java.util.regex.Matcher level = Pattern.compile("Рівень\\s+(\\d+)").matcher(levelText);
        if (!level.find()) throw new AssertionError("Cannot identify assignment level: " + levelText);
        assignedLevel = Integer.parseInt(level.group(1));
        item(resource).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(Pattern.compile("^(Призначити|Змінити)$"))).click();
        Locator dialog = page.getByRole(AriaRole.DIALOG);
        assertThat(dialog).isVisible(); return dialog;
    }
    public Locator assignmentRow(int index) {
        return page.getByRole(AriaRole.DIALOG).locator("div.flex.flex-col.sm\\:flex-row.gap-3").nth(index);
    }
    public void selectLocation(int index, String name) {
        assignmentRow(index).getByRole(AriaRole.COMBOBOX).first().click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(Pattern.compile("^" + regexLiteral(name) + "(?:\\s+\\(.*\\))?$"))).click();
    }
    public void amount(int index, int amount) { assignmentRow(index).locator("input[type=number]").fill(String.valueOf(amount)); }
    public void assign(String resource, String location, int amount) {
        openAssignment(resource); selectLocation(0, location); amount(0, amount); saveAssignment();
    }
    public void saveAssignment() {
        page.getByRole(AriaRole.DIALOG).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Зберегти").setExact(true)).click();
        assertThat(page.getByRole(AriaRole.DIALOG)).hasCount(0);
        // Each fixture level has one resource. Wait for its completed state, not a fixed delay.
        assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^Рівень\\s+" + assignedLevel + "\\s*Готово")))).isVisible();
    }
    public Response clickApi(String name, String method, String path) {
        Response result = page.waitForResponse(r -> r.request().method().equals(method) && r.url().endsWith(path),
                () -> button(name).click());
        org.assertj.core.api.Assertions.assertThat(result.status()).as("UI %s %s", method, path).isEqualTo(200);
        return result;
    }
    public void assertGenerationBlocked() { assertThat(button("Згенерувати")).hasCount(0); }
    public void expandLevels() {
        Locator closed = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("^Рівень ")))
                .and(page.locator("[data-state=closed]"));
        while (closed.count() > 0) closed.first().click();
    }
    /** Playwright transports regex to JavaScript, which does not support Java's \\Q...\\E. */
    public static String regexLiteral(String text) {
        StringBuilder escaped = new StringBuilder();
        for (char c : text.toCharArray()) {
            if ("\\^$.|?*+()[]{}".indexOf(c) >= 0) escaped.append('\\');
            escaped.append(c);
        }
        return escaped.toString();
    }
}
