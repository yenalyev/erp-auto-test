package com.erp.pages.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.regex.Pattern;

/**
 * Shared «Етапи виробництва» section used by project-production create/edit forms
 * and the project-production-template form (same DOM: stage cards + «Додати етап» after them).
 */
public class ProjectProductionStagesSection {

    public static final String SECTION_TITLE = "Етапи виробництва";
    public static final String ADD_STAGE_BUTTON = "Додати етап";
    public static final String NEW_STAGE_TITLE = "Новий етап";
    public static final String AMOUNT_PLACEHOLDER = "К-сть";
    public static final String STAGE_NAME_PLACEHOLDER = "Назва етапу...";
    public static final String PERCENT_PLACEHOLDER = "0";

    private final Page page;
    private final int timeoutMs;

    public ProjectProductionStagesSection(Page page, int timeoutMs) {
        this.page = page;
        this.timeoutMs = timeoutMs;
    }

    /** Card that contains the section title and the «Додати етап» control. */
    public Locator root() {
        return page.locator("form div")
                .filter(new Locator.FilterOptions().setHasText(SECTION_TITLE))
                .filter(new Locator.FilterOptions().setHas(
                        page.getByRole(AriaRole.BUTTON,
                                new Page.GetByRoleOptions().setName(ADD_STAGE_BUTTON))))
                .first();
    }

    public Locator addStageButton() {
        return root().getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(ADD_STAGE_BUTTON));
    }

    /**
     * True when «Додати етап» follows every stage title («Етап N» / «Новий етап») in document order
     * inside this section (create / edit / template share the same layout after CPMA-646).
     * <p>
     * Pre-CPMA-646 UI kept the button in the card header (before stage cards) — returns false there.
     */
    public boolean isAddStageButtonAfterAllStages() {
        Locator section = root();
        section.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(timeoutMs));
        addStageButton().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(timeoutMs));

        Object result = section.evaluate("""
                (section) => {
                  const addBtn = [...section.querySelectorAll('button')]
                    .find(b => (b.textContent || '').replace(/\\s+/g, ' ').includes('Додати етап'));
                  if (!addBtn) {
                    return { ok: false, reason: 'button-not-found' };
                  }

                  const titles = [...section.querySelectorAll('span.font-semibold')]
                    .filter(el => {
                      const t = (el.textContent || '').trim();
                      return /^Етап \\d+/.test(t) || t === 'Новий етап';
                    });
                  if (titles.length === 0) {
                    return { ok: true, reason: 'no-stage-titles', titleCount: 0 };
                  }
                  const last = titles[titles.length - 1];
                  const pos = last.compareDocumentPosition(addBtn);
                  const following = Boolean(pos & Node.DOCUMENT_POSITION_FOLLOWING);
                  const preceding = Boolean(pos & Node.DOCUMENT_POSITION_PRECEDING);
                  return {
                    ok: following,
                    reason: following ? 'after-stages' : (preceding ? 'before-stages' : 'other'),
                    titleCount: titles.length,
                    pos: pos
                  };
                }
                """);

        if (result instanceof java.util.Map<?, ?> map) {
            Object ok = map.get("ok");
            return Boolean.TRUE.equals(ok);
        }
        return Boolean.TRUE.equals(result);
    }

    /** Diagnostic detail for assertions when placement check fails. */
    public String describeAddStageButtonPlacement() {
        Locator section = root();
        if (section.count() == 0) {
            return "stages section not found";
        }
        Object result = section.evaluate("""
                (section) => {
                  const addBtn = [...section.querySelectorAll('button')]
                    .find(b => (b.textContent || '').replace(/\\s+/g, ' ').includes('Додати етап'));
                  if (!addBtn) return 'button-not-found';
                  const header = section.querySelector('[class*="border-b"]')
                    || section.querySelector('h3, [data-slot=\"card-header\"]');
                  const btnInHeader = header ? header.contains(addBtn) : false;
                  const titles = [...section.querySelectorAll('span.font-semibold')]
                    .filter(el => /^Етап \\d+/.test((el.textContent || '').trim()));
                  if (titles.length === 0) {
                    return btnInHeader ? 'button-in-header, no-stage-titles' : 'button-present, no-stage-titles';
                  }
                  const last = titles[titles.length - 1];
                  const pos = last.compareDocumentPosition(addBtn);
                  if (pos & Node.DOCUMENT_POSITION_FOLLOWING) return 'after-stages (expected)';
                  if (pos & Node.DOCUMENT_POSITION_PRECEDING) {
                    return btnInHeader
                      ? 'before-stages (legacy header placement)'
                      : 'before-stages';
                  }
                  return 'unexpected-position pos=' + pos;
                }
                """);
        return String.valueOf(result);
    }

    public int stageCardCount() {
        Locator section = root();
        if (section.count() == 0) {
            return 0;
        }
        return (int) section.evaluate("""
                (section) => [...section.querySelectorAll('span.font-semibold')]
                  .filter(el => /^Етап \\d+/.test((el.textContent || '').trim())).length
                """);
    }

    public void clickAddStage() {
        Locator button = addStageButton();
        button.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(timeoutMs));
        button.click();
    }

    /** Stage card root for 1-based order (title starts with «Етап N»). */
    public Locator stageCard(int order) {
        Pattern title = Pattern.compile("^Етап " + order + "( —|$)");
        return root().locator("div.border")
                .filter(new Locator.FilterOptions().setHas(
                        page.locator("span.font-semibold")
                                .filter(new Locator.FilterOptions().setHasText(title))))
                .first();
    }

    public Locator newStageForm() {
        return root().locator("div")
                .filter(new Locator.FilterOptions().setHasText(NEW_STAGE_TITLE))
                .filter(new Locator.FilterOptions().setHas(
                        page.getByPlaceholder(STAGE_NAME_PLACEHOLDER)))
                .first();
    }

    public void ensureStageExpanded(int order) {
        Locator card = stageCard(order);
        card.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(timeoutMs));
        Locator nameField = card.getByPlaceholder(STAGE_NAME_PLACEHOLDER);
        if (nameField.count() == 0 || !nameField.first().isVisible()) {
            card.locator("span.font-semibold").first().click();
            nameField.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(timeoutMs));
        }
    }

    public void fillExecutionPercentage(int order, int executionPercentage) {
        ensureStageExpanded(order);
        Locator percent = stageCard(order).getByPlaceholder(PERCENT_PLACEHOLDER);
        percent.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(timeoutMs));
        percent.fill(String.valueOf(executionPercentage));
    }

    /** Removes the first usage row inside the stage card (not the stage-delete control in the header). */
    public void removeFirstUsageRow(int order) {
        ensureStageExpanded(order);
        Locator card = stageCard(order);
        Locator amount = card.getByPlaceholder(AMOUNT_PLACEHOLDER);
        if (amount.count() == 0) {
            return;
        }
        Locator usageRow = card.locator("div.flex").filter(
                new Locator.FilterOptions().setHas(page.getByPlaceholder(AMOUNT_PLACEHOLDER))).first();
        Locator trash = usageRow.locator("button").filter(
                new Locator.FilterOptions().setHas(page.locator("svg.lucide-trash-2")));
        if (trash.count() > 0) {
            trash.first().click();
        }
    }

    public Locator amountInput(int order) {
        return stageCard(order).getByPlaceholder(AMOUNT_PLACEHOLDER).first();
    }

    public Locator resourceCombobox(int order) {
        return stageCard(order).getByRole(AriaRole.COMBOBOX)
                .filter(new Locator.FilterOptions().setHasText("Оберіть ресурс..."))
                .first();
    }
}
