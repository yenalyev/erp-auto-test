package com.erp.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.util.List;

/** Permission-gated notifications about outdated global-plan decomposition. */
public class GlobalPlanHintsPage extends BasePage {
    public GlobalPlanHintsPage(Page page) { super(page); }

    public Locator invalidDistributionHints() {
        // Copy is the contract; do not require a test id absent from the deployed frontend.
        return page.getByText(java.util.regex.Pattern.compile("^Розподіл у глобальних планах"))
                .locator("xpath=ancestor-or-self::div[contains(@class, 'border')][1]");
    }

    public List<String> hintTexts() {
        return invalidDistributionHints().allInnerTexts().stream()
                .map(text -> text.replaceAll("\\s+", " ").trim()).toList();
    }

    public List<String> hintPlanLabels() {
        return invalidDistributionHints().locator("a[href^='/global-plans/']").allInnerTexts().stream()
                .map(String::trim).toList();
    }

    public String hintHeaderText() {
        return page.getByText(java.util.regex.Pattern.compile("^Розподіл у глобальних планах"))
                .first().innerText().lines().findFirst().orElse("").replaceAll("\\s+", " ").trim();
    }

    public List<String> hintTechMapNames() {
        return invalidDistributionHints().locator("li").allInnerTexts().stream().map(String::trim).toList();
    }

    public boolean isRecoveryInstructionVisible() {
        return hintTexts().stream().anyMatch(text -> text.endsWith(
                "Відкрийте кожен план, призначте ресурс знову й повторно згенеруйте плани на локації."));
    }

    public Locator productionPlansEntry() {
        return page.locator("[data-sidebar='sidebar']")
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions()
                        .setName(java.util.regex.Pattern.compile("^Виробничі плани"))).first();
    }

    public Locator exclamationBadge() {
        return productionPlansEntry().locator("span, [data-sidebar='menu-badge']")
                .filter(new Locator.FilterOptions().setHasText(java.util.regex.Pattern.compile("^!$")));
    }

    public void waitForHint(String planDescription) {
        page.waitForCondition(() -> hintTexts().stream().anyMatch(text -> text.contains(planDescription)),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
    }

    public void expandTechMapList() {
        var expand = invalidDistributionHints().getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(java.util.regex.Pattern.compile("^\\+\\d+ ще$")));
        if (expand.count() > 0) expand.first().click();
    }

    public void waitForExclamation() {
        exclamationBadge().first().waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
    }

    public String badgeBackground() {
        return (String) exclamationBadge().first().evaluate("el => { const c = getComputedStyle(el).backgroundColor; "
                + colorToRgb() + " }");
    }

    public String hintBackground(String planDescription) {
        return (String) invalidDistributionHints().filter(new Locator.FilterOptions()
                .setHasText(planDescription)).first().evaluate("el => { "
                + "for (let p = el; p; p = p.parentElement) { const c = getComputedStyle(p).backgroundColor;"
                + "if (c !== 'rgba(0, 0, 0, 0)' && c !== 'transparent') { " + colorToRgb()
                + " } } return ''; }");
    }

    // Chrome preserves oklch() computed colors used by Tailwind; normalize in the browser.
    private static String colorToRgb() {
        return "const ctx = document.createElement('canvas').getContext('2d'); ctx.fillStyle = c; "
                + "ctx.fillRect(0, 0, 1, 1); const d = ctx.getImageData(0, 0, 1, 1).data; "
                + "return 'rgb(' + d[0] + ', ' + d[1] + ', ' + d[2] + ')';";
    }
}
