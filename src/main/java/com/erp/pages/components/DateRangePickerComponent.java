package com.erp.pages.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
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
    public static final String PLACEHOLDER = "Оберіть період";
    public static final String CLEAR_BUTTON = "Скинути";
    public static final String DEFAULT_PRESET_LABEL = "По-замовчуванню";
    public static final String PRESET_1_DAY = "1 день";
    public static final String PRESET_7_DAYS = "7 днів";
    public static final String PRESET_30_DAYS = "30 днів";
    public static final String PRESET_MONTH = "Місяць";
    public static final String PRESET_YEAR = "Рік";
    public static final String PRESET_3_MONTHS = "3 місяці";
    public static final String PRESET_HALF_YEAR = "Півроку";
    public static final List<String> PRESET_LABELS = List.of(
            PRESET_1_DAY, PRESET_7_DAYS, PRESET_30_DAYS, PRESET_MONTH, PRESET_YEAR);
    /** tk-ui {@code planPresets()} on /analytics/plan. */
    public static final List<String> PLAN_PRESET_LABELS = List.of(
            PRESET_MONTH, PRESET_3_MONTHS, PRESET_HALF_YEAR, PRESET_YEAR);
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
        waitForPopoverClosed(true);
        return this;
    }

    public DateRangePickerComponent setFromOnly(LocalDate from) {
        open();
        clickDay(from);
        page.keyboard().press("Escape");
        waitForPopoverClosed(true);
        return this;
    }

    public String getTriggerText() {
        return peekTriggerText();
    }

    public boolean isPopoverOpen() {
        return popover().count() > 0 && popover().first().isVisible();
    }

    public List<String> visiblePresetLabels() {
        return visiblePresetLabels(PRESET_LABELS);
    }

    public List<String> visiblePresetLabels(List<String> labels) {
        open();
        List<String> found = new ArrayList<>();
        for (String label : labels) {
            Locator button = presetButton(label);
            if (button.count() > 0 && button.first().isVisible()) {
                found.add(label);
            }
        }
        return found;
    }

    /**
     * Clicks a preset chip and does not force the popover closed — callers assert whether
     * the trigger range actually changed.
     */
    public DateRangePickerComponent clickPreset(String presetLabel) {
        open();
        Locator button = presetButton(presetLabel).first();
        button.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(timeoutMs));
        button.click();
        return this;
    }

    public DateRangePickerComponent selectPreset(String presetLabel) {
        clickPreset(presetLabel);
        waitForPopoverClosed(true);
        return this;
    }

    /** Browser-local calendar date ({@code yyyy-MM-dd}), matching tk-ui {@code getPresets()}. */
    public LocalDate browserToday() {
        String iso = (String) page.evaluate("""
                () => {
                  const d = new Date();
                  const month = String(d.getMonth() + 1).padStart(2, '0');
                  const day = String(d.getDate()).padStart(2, '0');
                  return `${d.getFullYear()}-${month}-${day}`;
                }
                """);
        return LocalDate.parse(iso);
    }

    /**
     * Expected trigger range for a preset chip (date-fns: today / {@code subDays(7)} /
     * {@code subMonths(1)} / {@code startOfMonth} / {@code startOfYear}).
     */
    public static DisplayedRange expectedPresetRange(String presetLabel, LocalDate today) {
        return switch (presetLabel) {
            case PRESET_1_DAY -> new DisplayedRange(today, today);
            case PRESET_7_DAYS -> new DisplayedRange(today.minusDays(7), today);
            case PRESET_30_DAYS -> new DisplayedRange(today.minusMonths(1), today);
            case PRESET_MONTH -> new DisplayedRange(today.withDayOfMonth(1), today);
            case PRESET_YEAR -> new DisplayedRange(today.withDayOfYear(1), today);
            default -> throw new IllegalArgumentException("Unknown DateRangePicker preset: " + presetLabel);
        };
    }

    public static String presetId(String presetLabel) {
        return switch (presetLabel) {
            case PRESET_1_DAY -> "1d";
            case PRESET_7_DAYS -> "7d";
            case PRESET_30_DAYS -> "30d";
            case PRESET_MONTH -> "month";
            case PRESET_YEAR -> "year";
            default -> throw new IllegalArgumentException("Unknown DateRangePicker preset: " + presetLabel);
        };
    }

    public static String defaultPresetStorageKey(String storageKey) {
        return "date-range-picker:default-preset:" + storageKey;
    }

    public DateRangePickerComponent waitUntilRange(LocalDate from, LocalDate to) {
        String fromIso = from != null ? from.toString() : "";
        String toIso = to != null ? to.toString() : "";
        try {
            page.waitForCondition(
                    () -> fromIso.equals(getFromIso()) && toIso.equals(getToIso()),
                    new Page.WaitForConditionOptions().setTimeout(timeoutMs));
        } catch (TimeoutError e) {
            log.warn("DateRangePicker still {} – {} (expected {} – {})",
                    getFromIso(), getToIso(), fromIso, toIso);
        }
        return this;
    }

    public boolean isCleared() {
        String text = normalize(readTriggerTextFromDom());
        if (text.isBlank()) {
            text = peekTriggerText();
        }
        return PLACEHOLDER.equals(text);
    }

    public DateRangePickerComponent waitUntilCleared() {
        try {
            page.waitForCondition(
                    this::isCleared,
                    new Page.WaitForConditionOptions().setTimeout(timeoutMs));
        } catch (TimeoutError e) {
            log.warn("DateRangePicker still showing '{}'", peekTriggerText());
        }
        return this;
    }

    public String readTriggerTextFromDom() {
        Object value = page.evaluate("""
                () => {
                  const labels = [...document.querySelectorAll('[data-slot="label"]')];
                  const period = labels.find(l => (l.textContent || '').replace(/\\s+/g, ' ').trim() === 'Період');
                  if (!period) {
                    return '';
                  }
                  const root = period.parentElement;
                  const btn = root ? root.querySelector('button') : null;
                  if (!btn) {
                    return '';
                  }
                  return (btn.innerText || btn.textContent || '').replace(/\\s+/g, ' ').trim();
                }
                """);
        return value == null ? "" : value.toString();
    }

    public boolean isResetButtonVisible() {
        open();
        Locator clear = popover().getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(CLEAR_BUTTON).setExact(true));
        return clear.count() > 0 && clear.first().isVisible();
    }

    public boolean isDefaultPresetRowVisible() {
        open();
        Locator label = popover().getByText(DEFAULT_PRESET_LABEL, new Locator.GetByTextOptions().setExact(true));
        return label.count() > 0 && label.first().isVisible();
    }

    public String getDefaultPresetTriggerText() {
        open();
        Locator trigger = defaultPresetSelectTrigger();
        if (trigger.count() == 0) {
            return "";
        }
        return normalize(trigger.first().innerText());
    }

    /**
     * Pins a preset via the «По-замовчуванню» Select. Does not apply the range
     * (tk-ui only writes localStorage until the next load).
     */
    public DateRangePickerComponent setDefaultPreset(String presetLabel) {
        open();
        Locator selectTrigger = defaultPresetSelectTrigger();
        selectTrigger.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(timeoutMs));
        selectTrigger.click();
        Locator option = page.getByRole(AriaRole.OPTION,
                new Page.GetByRoleOptions().setName(presetLabel).setExact(true));
        try {
            option.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(Math.min(timeoutMs, 5_000)));
        } catch (TimeoutError e) {
            log.debug("Default-preset options not visible — reopening picker: {}", e.getMessage());
            open();
            defaultPresetSelectTrigger().click();
            option.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(timeoutMs));
        }
        option.first().click();
        return this;
    }

    public String readPinnedDefaultPresetId(String storageKey) {
        Object value = page.evaluate("key => localStorage.getItem(key)",
                defaultPresetStorageKey(storageKey));
        return value == null ? "" : value.toString();
    }

    public void clearPinnedDefaultPreset(String storageKey) {
        page.evaluate("key => localStorage.removeItem(key)", defaultPresetStorageKey(storageKey));
    }

    public DateRangePickerComponent clear() {
        open();
        Locator clear = popover().getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(CLEAR_BUTTON).setExact(true));
        if (clear.count() == 0 || !clear.first().isVisible()) {
            throw new IllegalStateException("DateRangePicker «Скинути» is not visible");
        }
        clear.first().click();
        waitForPopoverClosed(false);
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

    private Locator presetButton(String presetLabel) {
        return popover().getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(presetLabel).setExact(true));
    }

    private Locator defaultPresetSelectTrigger() {
        Locator labeled = popover().locator("div").filter(
                new Locator.FilterOptions().setHasText(DEFAULT_PRESET_LABEL))
                .locator("[data-slot='select-trigger']");
        if (labeled.count() > 0) {
            return labeled.first();
        }
        return popover().locator("[data-slot='select-trigger']").last();
    }

    /**
     * Visible picker trigger. After «Скинути» the accessible name may be empty
     * (icon-only a11y tree) even though the button still shows «Оберіть період».
     */
    private Locator trigger() {
        Locator byPlaceholder = firstVisible(root.getByRole(AriaRole.BUTTON)
                .filter(new Locator.FilterOptions().setHasText(PLACEHOLDER)));
        if (byPlaceholder != null) {
            return byPlaceholder;
        }
        // Prefer the value button (placeholder / dd.MM.yyyy). On /analytics/production the
        // first button after «Період» is the prev-period chevron, not the picker.
        Locator named = root.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(Pattern.compile(
                        Pattern.quote(PLACEHOLDER)
                                + "|\\d{2}\\.\\d{2}\\.\\d{4}|від\\s+\\d{2}\\.\\d{2}\\.\\d{4}")));
        Locator visibleNamed = firstVisible(named);
        if (visibleNamed != null) {
            return visibleNamed;
        }
        Locator byLabel = root.locator("[data-slot='label']")
                .filter(new Locator.FilterOptions().setHasText(PERIOD_LABEL))
                .locator("xpath=following::button[1]");
        Locator visibleLabel = firstVisible(byLabel);
        if (visibleLabel != null) {
            return visibleLabel;
        }
        return named;
    }

    private static Locator firstVisible(Locator loc) {
        int n = loc.count();
        for (int i = 0; i < n; i++) {
            Locator candidate = loc.nth(i);
            if (candidate.isVisible()) {
                return candidate;
            }
        }
        return null;
    }

    private String peekTriggerText() {
        Locator t = trigger();
        try {
            if (t.count() == 0) {
                return "";
            }
            String text = t.first().textContent();
            return normalize(text);
        } catch (Exception e) {
            log.debug("Cannot read DateRangePicker trigger: {}", e.getMessage());
            return "";
        }
    }

    private Locator popover() {
        return page.locator("[data-radix-popper-content-wrapper]")
                .filter(new Locator.FilterOptions().setHas(page.locator("[data-slot='calendar']")));
    }

    private void waitForPopoverClosed(boolean pressEscapeOnTimeout) {
        try {
            page.waitForCondition(
                    () -> popover().count() == 0 || !popover().first().isVisible(),
                    new Page.WaitForConditionOptions().setTimeout(Math.min(timeoutMs, 5_000)));
        } catch (Exception e) {
            if (pressEscapeOnTimeout) {
                log.debug("Popover still open — pressing Escape: {}", e.getMessage());
                page.keyboard().press("Escape");
            } else {
                log.debug("Popover still open after reset: {}", e.getMessage());
            }
        }
    }

    private Optional<DisplayedRange> parseDisplayed() {
        String text = peekTriggerText();
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
