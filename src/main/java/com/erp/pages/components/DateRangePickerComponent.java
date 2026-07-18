package com.erp.pages.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Playwright helper for tk-ui {@code DateRangePicker} (CPMA-167).
 * Trigger is an outline button next to label «Період»; values live in React state only
 * (no {@code input[type=date]}).
 */
@Slf4j
public class DateRangePickerComponent {

    private static final String PERIOD_LABEL = "Період";
    private static final String PLACEHOLDER = "Оберіть період";
    private static final String CLEAR_BUTTON = "Скинути";
    private static final Locale UK = Locale.forLanguageTag("uk");
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    /** Matches {@code toLocaleDateString('uk')} used for {@code data-day} in CalendarDayButton. */
    private static final DateTimeFormatter DATA_DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final Pattern RANGE_PATTERN = Pattern.compile(
            "(\\d{2}\\.\\d{2}\\.\\d{4})\\s*[–-]\\s*(\\d{2}\\.\\d{2}\\.\\d{4})");
    private static final Pattern FROM_ONLY_PATTERN = Pattern.compile(
            "від\\s+(\\d{2}\\.\\d{2}\\.\\d{4})");

    private final Page page;
    private final Locator root;
    private final int timeoutMs;

    public DateRangePickerComponent(Page page, int timeoutMs) {
        this(page, page.locator("body"), timeoutMs);
    }

    /**
     * @param root scope that contains the «Період» label and the picker trigger
     *             (use a page-local container when several pickers exist)
     */
    public DateRangePickerComponent(Page page, Locator root, int timeoutMs) {
        this.page = page;
        this.root = root;
        this.timeoutMs = timeoutMs;
    }

    public boolean isVisible() {
        Locator trigger = trigger();
        return trigger.count() > 0 && trigger.first().isVisible();
    }

    /** ISO {@code yyyy-MM-dd} start date from the trigger label, or empty when unset. */
    public String getFromIso() {
        return parseDisplayed().map(DisplayedRange::from)
                .map(LocalDate::toString)
                .orElse("");
    }

    /** ISO {@code yyyy-MM-dd} end date from the trigger label, or empty when unset. */
    public String getToIso() {
        return parseDisplayed().flatMap(r -> Optional.ofNullable(r.to()))
                .map(LocalDate::toString)
                .orElse("");
    }

    public Optional<DisplayedRange> getDisplayedRange() {
        return parseDisplayed();
    }

    public DateRangePickerComponent setRange(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return clear();
        }
        if (from != null && to == null) {
            return setFromOnly(from);
        }
        if (from == null) {
            // DateRangePicker cannot set end without start — use same-day range.
            return setRange(to, to);
        }
        open();
        clickDay(from);
        if (!from.equals(to)) {
            clickDay(to);
        } else {
            // Same-day range: second click on the same day completes the selection.
            clickDay(to);
        }
        waitForPopoverClosed();
        return this;
    }

    public DateRangePickerComponent setFromOnly(LocalDate from) {
        open();
        clickDay(from);
        page.keyboard().press("Escape");
        waitForPopoverClosed();
        return this;
    }

    public DateRangePickerComponent selectPreset(String presetLabel) {
        open();
        popover().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(presetLabel))
                .first()
                .click();
        waitForPopoverClosed();
        return this;
    }

    public DateRangePickerComponent clear() {
        open();
        Locator clear = popover().getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(CLEAR_BUTTON));
        if (clear.count() > 0 && clear.first().isVisible()) {
            clear.first().click();
        } else {
            page.keyboard().press("Escape");
        }
        waitForPopoverClosed();
        return this;
    }

    public DateRangePickerComponent open() {
        Locator trigger = trigger().first();
        trigger.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(timeoutMs));
        if (popover().count() == 0 || !popover().first().isVisible()) {
            trigger.click();
            popover().first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(timeoutMs));
        }
        return this;
    }

    private void clickDay(LocalDate date) {
        ensureMonthVisible(date);
        Locator dayButton = dayButton(date);
        dayButton.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(timeoutMs));
        dayButton.click();
    }

    private Locator dayButton(LocalDate date) {
        String padded = date.format(DATA_DAY);
        String unpadded = date.getDayOfMonth() + "." + date.getMonthValue() + "." + date.getYear();
        return popover().locator(
                "button[data-day='" + padded + "'], button[data-day='" + unpadded + "']").first();
    }

    /**
     * Prefer month/year {@code <select>} (captionLayout=dropdown); fall back to next/prev.
     */
    private void ensureMonthVisible(LocalDate date) {
        if (dayButton(date).count() > 0) {
            return;
        }
        if (selectMonthYearViaDropdowns(date) && dayButton(date).count() > 0) {
            return;
        }
        for (int i = 0; i < 24; i++) {
            if (dayButton(date).count() > 0) {
                return;
            }
            LocalDate visibleAnchor = firstVisibleDayInPopover().orElse(LocalDate.now());
            if (!date.isBefore(visibleAnchor.withDayOfMonth(1))) {
                clickNavNext();
            } else {
                clickNavPrevious();
            }
        }
        throw new IllegalStateException("Cannot navigate calendar to " + date);
    }

    private void clickNavNext() {
        Locator nav = popover().locator(
                "button[name='next-month'], .rdp-button_next, button[aria-label*='Next']").first();
        if (nav.count() > 0) {
            nav.click();
        }
    }

    private void clickNavPrevious() {
        Locator nav = popover().locator(
                "button[name='previous-month'], .rdp-button_previous, button[aria-label*='Previous']")
                .first();
        if (nav.count() > 0) {
            nav.click();
        }
    }

    /** @return true when native selects were found and updated */
    private boolean selectMonthYearViaDropdowns(LocalDate date) {
        Locator selects = popover().locator("select");
        if (selects.count() < 2) {
            return false;
        }
        // Prefer numeric month value (0-11 or 1-12) over localized labels.
        String monthValue = String.valueOf(date.getMonthValue() - 1);
        try {
            selects.nth(0).selectOption(monthValue);
        } catch (Exception e) {
            String monthLabel = Month.of(date.getMonthValue()).getDisplayName(TextStyle.SHORT, UK);
            try {
                selects.nth(0).selectOption(new com.microsoft.playwright.options.SelectOption()
                        .setLabel(monthLabel));
            } catch (Exception e2) {
                selects.nth(0).selectOption(String.valueOf(date.getMonthValue()));
            }
        }
        selects.nth(1).selectOption(String.valueOf(date.getYear()));
        return true;
    }

    private Optional<LocalDate> firstVisibleDayInPopover() {
        Locator days = popover().locator("button[data-day]");
        if (days.count() == 0) {
            return Optional.empty();
        }
        String value = days.first().getAttribute("data-day");
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value, DATA_DAY));
        } catch (Exception e) {
            log.debug("Cannot parse data-day '{}': {}", value, e.getMessage());
            return Optional.empty();
        }
    }

    private Locator trigger() {
        Locator byLabel = root.locator("label")
                .filter(new Locator.FilterOptions().setHasText(PERIOD_LABEL))
                .locator("xpath=following::button[1]");
        if (byLabel.count() > 0) {
            return byLabel;
        }
        return root.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(Pattern.compile(
                        Pattern.quote(PLACEHOLDER)
                                + "|\\d{2}\\.\\d{2}\\.\\d{4}|від\\s+\\d{2}\\.\\d{2}\\.\\d{4}")));
    }

    private Locator popover() {
        return page.locator("[data-radix-popper-content-wrapper]")
                .filter(new Locator.FilterOptions().setHas(page.locator("[data-slot='calendar']")));
    }

    private void waitForPopoverClosed() {
        try {
            page.waitForCondition(
                    () -> popover().count() == 0 || !popover().first().isVisible(),
                    new Page.WaitForConditionOptions().setTimeout(Math.min(timeoutMs, 5_000)));
        } catch (Exception e) {
            log.debug("Popover still open — pressing Escape: {}", e.getMessage());
            page.keyboard().press("Escape");
        }
    }

    private Optional<DisplayedRange> parseDisplayed() {
        String text = normalize(trigger().first().innerText());
        if (text.isBlank() || PLACEHOLDER.equals(text)) {
            return Optional.empty();
        }
        Matcher range = RANGE_PATTERN.matcher(text);
        if (range.find()) {
            return Optional.of(new DisplayedRange(
                    LocalDate.parse(range.group(1), DISPLAY),
                    LocalDate.parse(range.group(2), DISPLAY)));
        }
        Matcher fromOnly = FROM_ONLY_PATTERN.matcher(text);
        if (fromOnly.find()) {
            return Optional.of(new DisplayedRange(
                    LocalDate.parse(fromOnly.group(1), DISPLAY),
                    null));
        }
        return Optional.empty();
    }

    private static String normalize(String value) {
        return value != null ? value.trim().replaceAll("\\s+", " ") : "";
    }

    public record DisplayedRange(LocalDate from, LocalDate to) {}
}
