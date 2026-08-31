package com.erp.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

/**
 * Detail dialog for a single equipment unit on {@code /equipment}:
 * sections «Історія» and «Історія закріплень».
 */
@Slf4j
public class EquipmentDetailDialog extends BasePage {

    public static final String HISTORY_HEADING = "Історія";
    public static final String ASSIGNMENT_HISTORY_HEADING = "Історія закріплень";
    public static final String EMPTY_HISTORY = "Історії ще немає";

    public static final String OP_ADDED = "Додано";
    public static final String OP_RECEIVED = "Отримано";
    public static final String OP_SENT = "Відправлено";
    public static final String OP_ASSIGNED = "Закріплено";
    public static final String OP_RETURNED = "Повернено";

    private static final String RETURN_ACTION = "Повернути";
    private static final String RETURN_DIALOG_TITLE = "Повернути обладнання";
    private static final String CURRENT_ASSIGNMENT_BADGE = "Поточний";

    public EquipmentDetailDialog(Page page) {
        super(page);
    }

    public EquipmentDetailDialog waitForOpen() {
        try {
            dialog().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(uiTimeoutMs()));
        } catch (Exception e) {
            log.warn("No role=dialog yet, waiting for history heading: {}", e.getMessage());
        }
        historyLabel().first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isOpen() {
        return dialog().count() > 0 && dialog().isVisible();
    }

    public boolean isHistorySectionVisible() {
        Locator label = historyLabel();
        return label.count() > 0 && label.first().isVisible();
    }

    public boolean isAssignmentHistorySectionVisible() {
        Locator heading = dialog().getByText(ASSIGNMENT_HISTORY_HEADING, new Locator.GetByTextOptions().setExact(true));
        return heading.count() > 0 && heading.first().isVisible();
    }

    public boolean hasOperation(String operationLabel) {
        return operationCount(operationLabel) > 0;
    }

    public int operationCount(String operationLabel) {
        return historySection()
                .getByText(operationLabel, new Locator.GetByTextOptions().setExact(true))
                .count();
    }

    public boolean assignmentHistoryContains(String text) {
        return assignmentHistorySection().getByText(text).count() > 0;
    }

    public boolean historyContains(String text) {
        return dialog().getByText(text).count() > 0
                || dialog().innerText().contains(text);
    }

    public boolean historyLooksEmpty() {
        return dialog().getByText(EMPTY_HISTORY).count() > 0;
    }

    public EquipmentDetailDialog assignTo(String callSign) {
        dialog().getByRole(AriaRole.BUTTON,
                        new Locator.GetByRoleOptions().setName("Закріпити за користувачем"))
                .click();
        Locator assignDialog = page.getByRole(AriaRole.DIALOG)
                .filter(new Locator.FilterOptions().setHas(
                        page.getByRole(AriaRole.HEADING,
                                new Page.GetByRoleOptions().setName("Закріпити обладнання"))));
        assignDialog.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        Locator picker = assignDialog.locator("[data-slot='input-group-control']")
                .or(assignDialog.getByPlaceholder("Оберіть співробітника..."))
                .or(page.getByPlaceholder("Оберіть співробітника..."))
                .first();
        picker.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        picker.click();
        picker.fill(callSign);
        waitForComboboxOptionsSettled();
        Locator option = page.locator("[data-slot='combobox-item']")
                .filter(new Locator.FilterOptions().setHasText(callSign));
        if (option.count() > 0) {
            option.first().click(new Locator.ClickOptions().setForce(true));
        } else {
            page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(callSign))
                    .first()
                    .click(new Locator.ClickOptions().setForce(true));
        }
        Locator confirm = assignDialog.getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Закріпити").setExact(true));
        page.waitForCondition(confirm::isEnabled, new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        var response = page.waitForResponse(
                r -> r.url().contains("/assignments")
                        && "POST".equals(r.request().method()),
                new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                () -> confirm.click(new Locator.ClickOptions().setForce(true)));
        if (response.status() < 200 || response.status() >= 300) {
            attachScreenshot("POST assign failed — status " + response.status());
            throw new IllegalStateException(
                    "POST /equipment/{id}/assignments failed with status " + response.status());
        }
        // Radix hides the parent dialog from the a11y tree while the assign dialog is open, so
        // getByRole(DIALOG) resolves to the overlay and the history cards look absent. Wait for the
        // overlay to detach before reading «Історія закріплень».
        assignDialog.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(uiTimeoutMs()));
        waitForConditionTolerant(() -> assignmentHistoryContains(callSign),
                "assignment row for " + callSign);
        return this;
    }

    /** Returns the unit from its current assignee via «Повернути» in the unit dialog. */
    public EquipmentDetailDialog returnFromAssignee(String note) {
        dialog().getByRole(AriaRole.BUTTON,
                        new Locator.GetByRoleOptions().setName(RETURN_ACTION).setExact(true))
                .click();
        Locator returnDialog = page.getByRole(AriaRole.DIALOG)
                .filter(new Locator.FilterOptions().setHas(
                        page.getByRole(AriaRole.HEADING,
                                new Page.GetByRoleOptions().setName(RETURN_DIALOG_TITLE))));
        returnDialog.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        returnDialog.getByPlaceholder("Примітка", new Locator.GetByPlaceholderOptions().setExact(false))
                .first()
                .fill(note);
        Locator confirm = returnDialog.getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(RETURN_ACTION).setExact(true));
        var response = page.waitForResponse(
                r -> r.url().contains("/assignments/return")
                        && "PUT".equals(r.request().method()),
                new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                () -> confirm.click(new Locator.ClickOptions().setForce(true)));
        if (response.status() < 200 || response.status() >= 300) {
            attachScreenshot("PUT return failed — status " + response.status());
            throw new IllegalStateException(
                    "PUT /equipment/{id}/assignments/return failed with status " + response.status());
        }
        returnDialog.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(uiTimeoutMs()));
        waitForConditionTolerant(() -> hasOperation(OP_RETURNED), "«Повернено» row in unit history");
        return this;
    }

    /** «Повернено» date cell is filled only once the assignment is closed. */
    public boolean assignmentHistoryHasOpenAssignment() {
        return assignmentHistorySection().getByText(CURRENT_ASSIGNMENT_BADGE).count() > 0;
    }

    /**
     * The unit dialog renders nested dialogs («Закріпити обладнання», «Повернути», image zoom) as
     * siblings in the portal, so {@code getByRole(DIALOG).first()} can resolve to a child dialog and
     * hide the history cards. Prefer the one that actually owns «Історія закріплень».
     */
    private Locator dialog() {
        Locator dialogs = page.getByRole(AriaRole.DIALOG);
        if (dialogs.count() == 0) {
            return page.locator("body");
        }
        Locator detail = dialogs.filter(new Locator.FilterOptions().setHas(
                page.getByText(ASSIGNMENT_HISTORY_HEADING, new Page.GetByTextOptions().setExact(true))));
        if (detail.count() > 0) {
            return detail.first();
        }
        return dialogs.first();
    }

    private Locator historyLabel() {
        Locator heading = dialog().getByRole(AriaRole.HEADING,
                new Locator.GetByRoleOptions().setName(HISTORY_HEADING).setExact(true));
        if (heading.count() > 0) {
            return heading;
        }
        return dialog().getByText(HISTORY_HEADING, new Locator.GetByTextOptions().setExact(true));
    }

    private Locator historySection() {
        return cardAround(historyLabel());
    }

    private Locator assignmentHistorySection() {
        return cardAround(dialog().getByText(ASSIGNMENT_HISTORY_HEADING,
                new Locator.GetByTextOptions().setExact(true)));
    }

    /**
     * Both sections are shadcn cards. Scoping to the owning card matters: «Повернено» is both an
     * operation label in «Історія» and a column header in «Історія закріплень», so a dialog-wide
     * text search would conflate them.
     */
    private Locator cardAround(Locator heading) {
        if (heading.count() == 0) {
            return dialog();
        }
        Locator card = heading.first().locator(
                "xpath=ancestor::*[@data-slot='card'][1] | ancestor::section[1]");
        return card.count() > 0 ? card.first() : dialog();
    }
}
