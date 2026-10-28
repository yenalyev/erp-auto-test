package com.erp.pages.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** Two independent single-date calendars commit their draft only on «Застосувати». */
public final class AppliedDateRangePickerComponent {
    private final Page page;
    private final int timeoutMs;
    private final DateRangePickerComponent picker;

    public AppliedDateRangePickerComponent(Page page, int timeoutMs) {
        this.page = page;
        this.timeoutMs = timeoutMs;
        picker = new DateRangePickerComponent(page, timeoutMs);
    }

    public AppliedDateRangePickerComponent setRange(LocalDate from, LocalDate to) {
        picker.open();
        var calendars = page.locator("[data-radix-popper-content-wrapper] [data-slot='calendar']");
        assertThat(calendars).hasCount(2);
        select(calendars.nth(0), from);
        select(calendars.nth(1), to);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Застосувати").setExact(true)).click();
        return waitUntilRange(from, to);
    }

    private void select(Locator calendar, LocalDate date) {
        String day = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        Locator button = calendar.locator("button[data-day='" + day + "'], button[data-day='"
                + date.getDayOfMonth() + "." + date.getMonthValue() + "." + date.getYear() + "']").first();
        if (button.count() == 0) {
            var selects = calendar.locator("select");
            assertThat(selects).hasCount(2);
            // Calendar uses react-day-picker native dropdowns, month values 0–11.
            selects.nth(1).selectOption(String.valueOf(date.getYear()));
            selects.nth(0).selectOption(String.valueOf(date.getMonthValue() - 1));
        }
        assertThat(button).isVisible();
        if (!"true".equals(button.getAttribute("data-selected-single"))) button.click();
        assertThat(button).hasAttribute("data-selected-single", "true");
    }

    public AppliedDateRangePickerComponent waitUntilRange(LocalDate from, LocalDate to) {
        page.waitForCondition(() -> from.toString().equals(picker.getFromIso())
                        && to.toString().equals(picker.getToIso()),
                new Page.WaitForConditionOptions().setTimeout(timeoutMs));
        return this;
    }

    public AppliedDateRangePickerComponent clear() {
        picker.clear();
        return waitUntilCleared();
    }

    public AppliedDateRangePickerComponent waitUntilCleared() {
        page.waitForCondition(picker::isCleared, new Page.WaitForConditionOptions().setTimeout(timeoutMs));
        return this;
    }
}
