package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.pages.EquipmentCreatePage;
import com.erp.pages.EquipmentKindHintSection;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static com.erp.pages.EquipmentKindHintSection.*;

@Epic("Equipment")
@Feature("Equipment classification advice")
public class EquipmentKindHintUiTest extends BaseUITest {
    private EquipmentKindHintSection open(UserRole role) {
        injectSessionCookies(cachedSessionCookies(role), sessionCookieDomain());
        injectWorkspaceView(role, ConfigProvider.getOwner1StorageId());
        page.close();
        page = browserContext.newPage();
        page.setDefaultTimeout(ConfigProvider.getUiTimeoutSeconds() * 1000);
        new EquipmentCreatePage(page).openForStorage(null);
        return new EquipmentKindHintSection(page, 0);
    }

    private void expectCollapsed(EquipmentKindHintSection hint) {
        assertThat(hint.expandButton()).isVisible();
        assertThat(hint.expandButton()).hasText("Більше...");
        assertThat(hint.expandButton()).hasAttribute("aria-expanded", "false");
        assertThat(hint.text(INTRO)).isVisible();
        assertThat(hint.text(GLASSES)).hasCount(0);
        assertThat(hint.text(LAPTOP)).hasCount(0);
        assertThat(hint.text(CONTACT)).hasCount(0);
    }

    private void expectExpanded(EquipmentKindHintSection hint) {
        assertThat(hint.collapseButton()).isVisible();
        assertThat(hint.collapseButton()).hasAttribute("aria-expanded", "true");
        for (String text : List.of(INTRO, GLASSES, LAPTOP, CONTACT)) {
            assertThat(hint.text(text)).isVisible();
        }
    }

    /** Only requests made by the user action under test, after authentication/navigation. */
    private List<String> recordWrites() {
        List<String> writes = new ArrayList<>();
        page.onRequest(request -> {
            if (List.of("POST", "PUT", "PATCH", "DELETE").contains(request.method())
                    && request.url().matches(".*/server/api/v1/(equipment(?:/.*)?|relocations(?:/.*)?|resources(?:/.*)?)(?:\\?.*)?")) {
                writes.add(request.method() + " " + request.url());
            }
        });
        return writes;
    }

    @Test @TestCaseId("TC-EQ-HINT-UI-001")
    public void ownerSeesAdviceBeforeEnteringEquipment() {
        expectCollapsed(open(UserRole.OWNER_1));
        attachScreenshot("TC-EQ-HINT-UI-001 — відповідальний, початковий стан");
    }

    @Test @TestCaseId("TC-EQ-HINT-UI-002")
    public void adminCanReadExamplesAndContact() {
        var hint = open(UserRole.ADMIN);
        expectCollapsed(hint);
        hint.expand();
        expectExpanded(hint);
        attachScreenshot("TC-EQ-HINT-UI-002 — повний текст");
    }

    @Test @TestCaseId("TC-EQ-HINT-UI-003")
    public void ownerReadsApprovedClassificationText() {
        var hint = open(UserRole.OWNER_1);
        hint.expand();
        expectExpanded(hint);
        Allure.addAttachment("Фактичний текст підказки", hint.expandedContainer().innerText());
        // Current wording explicitly approved by the user on 2026-10-19.
        assertThat(hint.expandedContainer().locator("li")).hasText(new String[]{INTRO, GLASSES, LAPTOP});
        assertThat(hint.expandedContainer().locator("p")).hasText(new String[]{"Приклад:", CONTACT});
        assertThat(hint.collapseButton()).hasText("Згорнути");
        attachScreenshot("TC-EQ-HINT-UI-003 — погоджений текст");
    }

    @Test @TestCaseId("TC-EQ-HINT-UI-004")
    public void repeatedTogglingPreservesInputAndDoesNotSubmit() {
        var hint = open(UserRole.OWNER_1);
        // The label is rendered separately from its autocomplete input.
        Locator name = hint.card().locator("label").filter(new Locator.FilterOptions().setHasText("Назва"))
                .locator("xpath=following::input[1]").first();
        Locator inventory = hint.card().getByPlaceholder("Згенерується автоматично");
        name.fill("Ноутбук HP ZBook Power G7 — перевірка без збереження");
        inventory.fill("HINT-UNSAVED-001");
        hint.card().getByLabel("Без серійного номера").check();
        List<String> writes = recordWrites();
        for (int i = 0; i < 3; i++) {
            hint.expand();
            expectExpanded(hint);
            hint.collapse();
            expectCollapsed(hint);
        }
        assertThat(name).hasValue("Ноутбук HP ZBook Power G7 — перевірка без збереження");
        assertThat(inventory).hasValue("HINT-UNSAVED-001");
        assertThat(hint.card().getByLabel("Без серійного номера")).isChecked();
        org.assertj.core.api.Assertions.assertThat(new EquipmentCreatePage(page).positionCount()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(writes).as("Hint controls must not write to ERP").isEmpty();
        assertThat(page).hasURL(Pattern.compile(".*/equipment/create.*"));
    }

    @Test @TestCaseId("TC-EQ-HINT-UI-005")
    public void batchItemsHaveIndependentAdvice() {
        var first = open(UserRole.OWNER_1);
        first.expand();
        new EquipmentCreatePage(page).addAnotherItem();
        var second = new EquipmentKindHintSection(page, 1);
        expectCollapsed(second);
        // Adding an item collapses the previous card; reopen it before checking both hints.
        if (!first.collapseButton().isVisible() && !first.expandButton().isVisible()) {
            first.card().locator("[data-slot='card-header']").click();
        }
        // Per-card visibility can reset when its content unmounts. Expand explicitly.
        if (first.expandButton().isVisible()) first.expand();
        second.expand();
        expectExpanded(first);
        expectExpanded(second);
        first.collapse();
        expectCollapsed(first);
        expectExpanded(second);
        org.assertj.core.api.Assertions.assertThat(new EquipmentCreatePage(page).positionCount()).isEqualTo(2);
        attachScreenshot("TC-EQ-HINT-UI-005 — дві незалежні підказки");
    }

    @Test @TestCaseId("TC-EQ-HINT-UI-006")
    public void keyboardActivatesBothAdviceControls() {
        var hint = open(UserRole.OWNER_1);
        List<String> writes = recordWrites();
        hint.expandButton().focus();
        assertThat(hint.expandButton()).isFocused();
        page.keyboard().press("Enter");
        expectExpanded(hint);
        hint.collapseButton().focus();
        page.keyboard().press("Space");
        expectCollapsed(hint);
        org.assertj.core.api.Assertions.assertThat(writes).isEmpty();
    }

    @Test @TestCaseId("TC-EQ-HINT-UI-007")
    public void expandedAdviceIsReadableAtDesktopAndNarrowViewport() {
        var hint = open(UserRole.OWNER_1);
        for (int width : List.of(1280, 390)) {
            page.setViewportSize(width, 844);
            expectCollapsed(hint);
            hint.expand();
            expectExpanded(hint);
            var box = hint.expandedContainer().boundingBox();
            org.assertj.core.api.Assertions.assertThat(box).isNotNull();
            org.assertj.core.api.Assertions.assertThat(box.x).isGreaterThanOrEqualTo(0);
            org.assertj.core.api.Assertions.assertThat(box.x + box.width).isLessThanOrEqualTo(width + 1.0);
            for (String text : List.of(INTRO, GLASSES, LAPTOP, CONTACT)) {
                boolean fits = (boolean) hint.text(text).evaluate("e => e.scrollWidth <= e.clientWidth + 1");
                org.assertj.core.api.Assertions.assertThat(fits).as("Readable at %s px: %s", width, text).isTrue();
            }
            attachScreenshot("TC-EQ-HINT-UI-007 — " + width + "px");
            hint.collapse();
        }
    }

    @Test @TestCaseId("TC-EQ-HINT-UI-008")
    public void cancellationDoesNotCreateEquipmentAndReopenRestoresAdvice() {
        var hint = open(UserRole.OWNER_1);
        List<String> writes = recordWrites();
        hint.expand();
        expectExpanded(hint);
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Скасувати").setExact(true)).click();
        assertThat(page).hasURL(Pattern.compile(".*/equipment(?:\\?.*)?$"));
        org.assertj.core.api.Assertions.assertThat(writes).isEmpty();
        new EquipmentCreatePage(page).openForStorage(null);
        expectCollapsed(new EquipmentKindHintSection(page, 0));
    }
}
