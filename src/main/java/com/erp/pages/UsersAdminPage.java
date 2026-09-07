package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Page Object for admin user management: /users, /users/create, /users/:id.
 */
@Slf4j
public class UsersAdminPage extends BasePage {

    public static final String LIST_PATH = "/users";
    public static final String CREATE_PATH = "/users/create";
    public static final String PAGE_TITLE = "Довідники: Користувачі та ролі";
    public static final String CREATE_PAGE_TITLE = "Новий користувач";
    public static final String USERS_TAB = "Користувачі";
    public static final String ROLES_TAB = "Ролі";
    public static final String NEW_USER_BUTTON = "Новий користувач";
    public static final String SEARCH_PLACEHOLDER = "Пошук за логіном";
    public static final String STORAGE_FILTER_PLACEHOLDER = "Всі локації...";
    public static final String CLEAR_FILTERS_BUTTON = "Очистити";
    public static final String CREATE_SUBMIT_BUTTON = "Створити";
    public static final String SAVE_BUTTON = "Зберегти";
    public static final String DONE_BUTTON = "Готово";
    public static final String CREDENTIALS_DIALOG_TITLE = "Користувача створено";
    public static final String LOADING_TEXT = "Завантаження...";
    public static final String ADMINISTRATOR_ROLE = "Administrator-ROLE";

    private static final List<String> USER_TABLE_HEADERS = List.of("Логін", "Ім'я", "Прізвище", "Локації");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern ROLES_LABEL = Pattern.compile("^\\s*Ролі\\s*$");
    private static final Pattern ROLES_PLACEHOLDER = Pattern.compile("рол", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final String ROLE_OVERLAP_JS = """
            el => {
              const r = el.getBoundingClientRect();
              if (r.width < 1 || r.height < 1) {
                return 'zero-size';
              }
              const overlayNodes = [
                ...document.querySelectorAll(
                    "[data-sidebar='sidebar'], [data-slot='sidebar'], header, footer, [data-slot='header']")
              ];
              document.querySelectorAll('button').forEach(btn => {
                const t = (btn.innerText || '').trim();
                if (t === 'Зберегти' || t.startsWith('Зберегти')) {
                  overlayNodes.push(btn);
                  const parent = btn.parentElement;
                  if (parent) {
                    const ps = getComputedStyle(parent);
                    if (ps.position === 'fixed' || ps.position === 'sticky') {
                      overlayNodes.push(parent);
                    }
                  }
                }
              });
              for (const ov of overlayNodes) {
                if (!ov || ov === el || el.contains(ov) || ov.contains(el)) {
                  continue;
                }
                const s = getComputedStyle(ov);
                if (s.display === 'none' || s.visibility === 'hidden' || Number(s.opacity) === 0) {
                  continue;
                }
                const o = ov.getBoundingClientRect();
                if (o.width < 1 || o.height < 1) {
                  continue;
                }
                const hit = r.left < o.right && r.right > o.left && r.top < o.bottom && r.bottom > o.top;
                if (hit) {
                  const label = ov.getAttribute('data-sidebar')
                      || ov.getAttribute('data-slot')
                      || ((ov.innerText || ov.tagName) + '').trim().slice(0, 48);
                  return 'overlap ' + label;
                }
              }
              const clipsOverflow = (s) => {
                const vals = [s.overflow, s.overflowX, s.overflowY];
                return vals.some(v => v === 'hidden' || v === 'clip' || v === 'auto' || v === 'scroll');
              };
              let p = el.parentElement;
              while (p && p !== document.body) {
                const s = getComputedStyle(p);
                const pr = p.getBoundingClientRect();
                const overflows = p.scrollHeight > p.clientHeight + 1
                    || p.scrollWidth > p.clientWidth + 1;
                const clipped = r.bottom > pr.bottom + 0.5 || r.top < pr.top - 0.5
                    || r.right > pr.right + 0.5 || r.left < pr.left - 0.5;
                const isFieldSized = p.clientHeight > 0 && p.clientHeight < 240;
                if (clipsOverflow(s) && isFieldSized && (clipped || overflows)) {
                  if (s.overflowY === 'auto' || s.overflowY === 'scroll') {
                    return 'needs-list-scroll';
                  }
                  return 'clipped-overflow-hidden';
                }
                p = p.parentElement;
              }
              const samples = [
                [r.left + r.width / 2, r.top + r.height / 2],
                [r.left + r.width / 2, r.bottom - 1],
                [r.left + 4, r.bottom - 1],
                [r.right - 4, r.bottom - 1]
              ];
              for (const [x, y] of samples) {
                if (x < 0 || y < 0 || x > window.innerWidth || y > window.innerHeight) {
                  return 'off-viewport';
                }
                const top = document.elementFromPoint(x, y);
                if (!top) {
                  return 'clipped-or-overlapped-at-bottom';
                }
                const chip = el.closest('[data-slot="badge"], button, [class*="badge"]') || el.parentElement || el;
                if (chip.contains(top) || top.contains(chip) || el.contains(top) || top.contains(el)) {
                  continue;
                }
                  let anc = top;
                  let sticky = false;
                  while (anc && anc !== document.body) {
                    const acs = getComputedStyle(anc);
                    if ((acs.position === 'fixed' || acs.position === 'sticky') && !anc.contains(el)) {
                      sticky = true;
                      break;
                    }
                    anc = anc.parentElement;
                  }
                  if (sticky || y >= r.bottom - 2) {
                    return (sticky ? 'hit-test-sticky ' : 'clipped-or-overlapped-at-bottom ')
                        + ((top.innerText || top.tagName) + '').trim().slice(0, 48);
                  }
              }
              return null;
            }
            """;

    public UsersAdminPage(Page page) {
        super(page);
    }

    public UsersAdminPage open() {
        String url = ConfigProvider.getBaseUrl() + LIST_PATH;
        log.info("Opening users admin page: {}", url);
        navigateTo(url, "Користувачі та ролі (/users)");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        return waitForListLoaded();
    }

    public UsersAdminPage openCreate() {
        String url = ConfigProvider.getBaseUrl() + CREATE_PATH;
        navigateTo(url, "Новий користувач (/users/create)");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        return waitForCreateLoaded();
    }

    public UsersAdminPage waitForListLoaded() {
        waitForPageReady();
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(PAGE_TITLE))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        waitForLoadingFinished();
        return this;
    }

    public UsersAdminPage waitForCreateLoaded() {
        waitForPageReady();
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(CREATE_PAGE_TITLE))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public UsersAdminPage waitForUserDetailLoaded(String username) {
        waitForPageReady();
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(username))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isListPageLoaded() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(PAGE_TITLE)).isVisible();
    }

    public boolean isUsersTabVisible() {
        return page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(USERS_TAB)).isVisible();
    }

    public boolean isNewUserButtonVisible() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEW_USER_BUTTON)).isVisible()
                || page.locator("button").filter(new Locator.FilterOptions().setHasText(NEW_USER_BUTTON)).isVisible();
    }

    public boolean areUserTableHeadersVisible() {
        Locator thead = page.locator("table thead");
        if (thead.count() == 0) {
            return false;
        }
        for (String header : USER_TABLE_HEADERS) {
            if (!thead.getByText(header, new Locator.GetByTextOptions().setExact(true)).isVisible()) {
                return false;
            }
        }
        return true;
    }

    public UsersAdminPage searchByUsername(String text) {
        Locator input = page.getByPlaceholder(SEARCH_PLACEHOLDER);
        input.click();
        input.fill(text);
        page.waitForTimeout(350);
        waitForLoadingFinished();
        return this;
    }

    public UsersAdminPage selectFirstStorageFilter() {
        page.getByPlaceholder(STORAGE_FILTER_PLACEHOLDER).click();
        waitForComboboxOptionsSettled();
        Locator item = page.locator("[data-slot='combobox-item']").first();
        if (item.count() == 0) {
            item = page.getByRole(AriaRole.OPTION).first();
        }
        item.click();
        waitForLoadingFinished();
        return this;
    }

    public boolean isUsernameVisibleInTable(String username) {
        Locator link = page.locator("table tbody").getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(username));
        if (link.count() > 0 && link.first().isVisible()) {
            return true;
        }
        return page.locator("table tbody").getByText(username, new Locator.GetByTextOptions().setExact(true)).isVisible();
    }

    public UsersAdminPage clickNewUser() {
        Locator button = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEW_USER_BUTTON));
        if (button.count() == 0) {
            button = page.locator("button").filter(new Locator.FilterOptions().setHasText(NEW_USER_BUTTON));
        }
        button.first().click();
        return waitForCreateLoaded();
    }

    public UsersAdminPage fillCreateForm(String username, String firstName, String lastName) {
        Locator inputs = formTextInputs();
        inputs.nth(0).fill(username);
        inputs.nth(1).fill(firstName);
        inputs.nth(2).fill(lastName);
        return this;
    }

    public UsersAdminPage submitCreate() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CREATE_SUBMIT_BUTTON)).click();
        return this;
    }

    public UsersAdminPage assertCredentialsDialogVisible() {
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(CREDENTIALS_DIALOG_TITLE))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean credentialsDialogShowsUsername(String username) {
        return page.getByText("Логін:").locator("xpath=..").getByText(username).isVisible();
    }

    public UsersAdminPage dismissCredentialsDialog() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(DONE_BUTTON)).click();
        return waitForListLoaded();
    }

    public UsersAdminPage openRolesTab() {
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(ROLES_TAB)).click();
        waitForLoadingFinished();
        return this;
    }

    public UsersAdminPage clickRoleName(String roleName) {
        page.locator("table tbody button").filter(new Locator.FilterOptions().setHasText(roleName)).first().click();
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Дозволи ролі «" + roleName + "»"))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        waitForRolePermissionsLoaded();
        return this;
    }

    public List<String> getVisibleRolePermissions() {
        List<String> permissions = new ArrayList<>();
        Locator items = page.locator("li");
        for (int i = 0; i < items.count(); i++) {
            String text = normalize(items.nth(i).innerText());
            if (text.startsWith("perm_")) {
                permissions.add(text);
            }
        }
        return permissions;
    }

    public UsersAdminPage clickUsernameLink(String username) {
        page.locator("table tbody").getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(username)).click();
        return waitForUserDetailLoaded(username);
    }

    public boolean isFirstNameFieldEditable() {
        Locator inputs = formTextInputs();
        return inputs.count() > 1 && inputs.nth(1).isVisible() && inputs.nth(1).isEnabled();
    }

    public String getFirstNameFieldValue() {
        Locator inputs = formTextInputs();
        if (inputs.count() > 1 && inputs.nth(1).isVisible()) {
            return inputs.nth(1).inputValue();
        }
        Locator readOnly = page.locator("label").filter(new Locator.FilterOptions().setHasText("Ім'я"))
                .locator("xpath=following-sibling::div[1]");
        return readOnly.count() > 0 ? normalize(readOnly.first().innerText()) : "";
    }

    public UsersAdminPage updateFirstName(String firstName) {
        Locator input = formTextInputs().nth(1);
        input.click();
        input.fill(firstName);
        return this;
    }

    public UsersAdminPage saveUser() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SAVE_BUTTON)).click();
        return waitForListLoaded();
    }

    /**
     * True when Locations ({@code MultiStorageSelector}) has selected chips.
     * Empty state keeps placeholder «Виберіть підрозділи»; with selection the placeholder is cleared.
     */
    public boolean hasSelectedLocationChips() {
        Locator placeholder = page.locator("form").getByPlaceholder("Виберіть підрозділи");
        if (placeholder.count() == 0) {
            return true;
        }
        Locator first = placeholder.first();
        if (!first.isVisible()) {
            return true;
        }
        String attr = first.getAttribute("placeholder");
        return attr == null || attr.isBlank();
    }

    public boolean isOnUsersListPath() {
        return currentUrl().contains(LIST_PATH) && !currentUrl().contains("/create");
    }

    /**
     * Waits for the roles field on /users/{id}. Opens the roles combobox when assigned names
     * are not yet in the DOM (collapsed chips / +N).
     */
    public UsersAdminPage waitForRolesSection(List<String> assignedRoleNames) {
        if (assignedRoleNames == null || assignedRoleNames.isEmpty()) {
            throw new IllegalArgumentException("assignedRoleNames must not be empty");
        }
        waitForLoadingFinished();
        page.waitForCondition(() -> page.locator("form").count() > 0 && page.locator("form").first().isVisible(),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        String first = assignedRoleNames.getFirst();
        String last = assignedRoleNames.getLast();
        if (roleLocator(first).count() == 0 || roleLocator(last).count() == 0) {
            openRolesSelector();
        }
        page.waitForCondition(
                () -> roleLocator(first).count() > 0 && roleLocator(last).count() > 0,
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean rolesSectionVisible() {
        Locator label = page.locator("form label")
                .filter(new Locator.FilterOptions().setHasText(ROLES_LABEL));
        if (label.count() > 0 && label.first().isVisible()) {
            return true;
        }
        Locator placeholder = page.locator("form").getByPlaceholder(ROLES_PLACEHOLDER);
        if (placeholder.count() > 0 && placeholder.first().isVisible()) {
            return true;
        }
        Locator chips = page.locator("form").getByText(Pattern.compile("-ROLE"));
        if (chips.count() > 0 && chips.first().isVisible()) {
            return true;
        }
        return rolesDropdownOpen();
    }

    public UsersAdminPage openRolesSelector() {
        Locator trigger = userRolesTrigger();
        if (trigger.count() == 0) {
            return this;
        }
        trigger.first().click();
        waitForComboboxOptionsSettled();
        return this;
    }

    /**
     * @return {@code null} if the role is fully visible and not covered by chrome UI;
     * {@code "needs-list-scroll"} when the item sits in a scrollable roles list;
     * otherwise a short reason (overlap / clipped / missing).
     */
    public String roleObstructionReason(String roleName) {
        Locator loc = roleLocator(roleName);
        if (loc.count() == 0) {
            return "not in DOM";
        }
        try {
            Object result = loc.first().evaluate(ROLE_OVERLAP_JS);
            return result == null ? null : String.valueOf(result);
        } catch (Exception e) {
            log.warn("Overlap evaluate failed for role {}: {}", roleName, e.getMessage());
            return "evaluate failed: " + e.getMessage();
        }
    }

    /**
     * Assigned role with the smallest top edge (first in the visible list).
     */
    public String visuallyFirstRole(List<String> roleNames) {
        return extremeRole(roleNames, true);
    }

    /**
     * Assigned role with the largest bottom edge (last in the visible list).
     */
    public String visuallyLastRole(List<String> roleNames) {
        return extremeRole(roleNames, false);
    }

    private String extremeRole(List<String> roleNames, boolean first) {
        String pick = null;
        double edge = first ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        for (String name : roleNames) {
            Locator loc = roleLocator(name);
            if (loc.count() == 0) {
                continue;
            }
            String js = first
                    ? "el => el.getBoundingClientRect().top"
                    : "el => el.getBoundingClientRect().bottom";
            Object raw = loc.first().evaluate(js);
            if (!(raw instanceof Number n)) {
                continue;
            }
            double value = n.doubleValue();
            if (first && value < edge) {
                edge = value;
                pick = name;
            } else if (!first && value > edge) {
                edge = value;
                pick = name;
            }
        }
        return pick;
    }

    /**
     * Roles whose chips overflow a field-sized ancestor (the bordered «Ролі» control).
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> rolesClippedByField(List<String> roleNames) {
        Object raw = page.locator("form").first().evaluate("""
                (form, names) => {
                  const issues = {};
                  for (const name of names) {
                    const matches = [...form.querySelectorAll('*')].filter(n => {
                      const t = (n.textContent || '').replace(/\\s+/g, ' ').trim();
                      return t === name || (t.startsWith(name) && t.length <= name.length + 12);
                    });
                    matches.sort((a, b) => (a.textContent || '').length - (b.textContent || '').length);
                    const el = matches[0];
                    if (!el) {
                      issues[name] = 'not in field';
                      continue;
                    }
                    const chip = el.closest('button, [data-slot="badge"], [class*="badge"]') || el;
                    const r = chip.getBoundingClientRect();
                    let p = chip.parentElement;
                    while (p && p !== form) {
                      const s = getComputedStyle(p);
                      const borderBottom = parseFloat(s.borderBottomWidth || 0);
                      const hasBox = borderBottom >= 1
                          || s.boxShadow !== 'none'
                          || s.outlineStyle !== 'none';
                      const pr = p.getBoundingClientRect();
                      const innerBottom = pr.bottom - borderBottom - parseFloat(s.paddingBottom || 0);
                      const fieldSized = p.clientHeight > 32 && p.clientHeight < 260;
                      if (fieldSized && hasBox && r.bottom > innerBottom + 0.5) {
                        issues[name] = 'clipped-by-field ' + (r.bottom - innerBottom).toFixed(1) + 'px';
                        break;
                      }
                      p = p.parentElement;
                    }
                  }
                  return issues;
                }
                """, roleNames);
        if (raw instanceof Map<?, ?> map) {
            Map<String, String> issues = new LinkedHashMap<>();
            map.forEach((k, v) -> issues.put(String.valueOf(k), String.valueOf(v)));
            return issues;
        }
        return Map.of();
    }

    /**
     * True when the «Ролі» control cannot show all chips (scrollHeight &gt; clientHeight).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> rolesFieldOverflow(List<String> roleNames) {
        Object raw = page.locator("form").first().evaluate("""
                (form, names) => {
                  const label = [...form.querySelectorAll('label')]
                      .find(l => (l.textContent || '').trim() === 'Ролі');
                  if (!label) {
                    return { clipped: true, error: 'no-roles-label' };
                  }
                  const root = label.parentElement || form;
                  const nodes = [root, ...root.querySelectorAll('*')];
                  for (const n of nodes) {
                    const text = n.textContent || '';
                    const roleHits = names.filter(nm => text.includes(nm)).length;
                    if (roleHits < 2) {
                      continue;
                    }
                    const s = getComputedStyle(n);
                    const extra = n.scrollHeight - n.clientHeight;
                    if (extra > 8 && n.clientHeight >= 40 && n.clientHeight < 260) {
                      return {
                        clipped: true,
                        scrollHeight: n.scrollHeight,
                        clientHeight: n.clientHeight,
                        extraPx: extra,
                        overflow: s.overflowY || s.overflow,
                        roleHits: roleHits
                      };
                    }
                  }
                  return { clipped: false };
                }
                """, roleNames);
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return Map.of("clipped", true, "error", String.valueOf(raw));
    }

    public UsersAdminPage scrollRolesListTo(String roleName) {
        Locator loc = roleLocator(roleName);
        if (loc.count() == 0) {
            return this;
        }
        loc.first().evaluate("""
                el => {
                  let p = el.parentElement;
                  while (p && p !== document.body) {
                    const s = getComputedStyle(p);
                    if ((s.overflowY === 'auto' || s.overflowY === 'scroll')
                            && p.scrollHeight > p.clientHeight + 2) {
                      const top = el.getBoundingClientRect().top - p.getBoundingClientRect().top + p.scrollTop;
                      p.scrollTop = top;
                      return;
                    }
                    p = p.parentElement;
                  }
                  el.scrollIntoView({block: 'nearest', inline: 'nearest'});
                }
                """);
        return this;
    }

    private Locator roleLocator(String roleName) {
        if (rolesDropdownOpen()) {
            Locator item = page.locator("[data-slot='combobox-item']")
                    .filter(new Locator.FilterOptions().setHasText(roleName));
            if (item.count() > 0) {
                return item.first();
            }
            Locator option = page.getByRole(AriaRole.OPTION,
                    new Page.GetByRoleOptions().setName(roleName).setExact(true));
            if (option.count() > 0) {
                return option.first();
            }
        }
        Locator inRolesField = rolesField().getByText(roleName);
        if (inRolesField.count() > 0) {
            return inRolesField.last();
        }
        return page.locator("form").getByText(roleName).last();
    }

    private boolean rolesDropdownOpen() {
        return isAnyVisible(page.locator("[data-slot='combobox-item']"))
                || isAnyVisible(page.getByRole(AriaRole.OPTION));
    }

    private Locator rolesField() {
        Locator label = page.locator("form label")
                .filter(new Locator.FilterOptions().setHasText(ROLES_LABEL));
        if (label.count() > 0) {
            Locator sibling = label.first().locator("xpath=following-sibling::*[1]");
            Locator control = sibling.locator(
                    "[role='combobox'], button, [data-slot='combobox-trigger'], [data-slot='popover-trigger']");
            if (control.count() > 0) {
                return control.first();
            }
            if (sibling.count() > 0) {
                return sibling;
            }
        }
        Locator placeholder = page.locator("form").getByPlaceholder(ROLES_PLACEHOLDER);
        if (placeholder.count() > 0) {
            return placeholder.first().locator("xpath=ancestor::*[self::div or self::button][1]");
        }
        return page.locator("form");
    }

    private Locator userRolesTrigger() {
        Locator form = page.locator("form");
        Locator byPlaceholder = form.getByPlaceholder(ROLES_PLACEHOLDER);
        if (byPlaceholder.count() > 0) {
            return byPlaceholder.first();
        }
        Locator labelled = form.getByLabel(ROLES_LABEL);
        if (labelled.count() > 0) {
            return labelled.first();
        }
        Locator roleLabel = form.locator("label").filter(new Locator.FilterOptions().setHasText(ROLES_LABEL));
        if (roleLabel.count() > 0) {
            Locator following = roleLabel.first().locator("xpath=following-sibling::*[1]")
                    .locator("input, button, [role='combobox'], [data-slot='combobox-trigger']");
            if (following.count() > 0) {
                return following.first();
            }
            return roleLabel.first().locator("xpath=following-sibling::*[1]");
        }
        Locator comboboxes = form.getByRole(AriaRole.COMBOBOX);
        for (int i = 0; i < comboboxes.count(); i++) {
            String placeholder = comboboxes.nth(i).getAttribute("placeholder");
            if (placeholder != null && placeholder.toLowerCase().contains("рол")) {
                return comboboxes.nth(i);
            }
        }
        for (int i = 0; i < comboboxes.count(); i++) {
            String placeholder = comboboxes.nth(i).getAttribute("placeholder");
            if (placeholder != null && placeholder.contains("підрозділ")) {
                continue;
            }
            return comboboxes.nth(i);
        }
        return comboboxes;
    }

    private static boolean isAnyVisible(Locator locator) {
        int count = locator.count();
        for (int i = 0; i < count; i++) {
            if (locator.nth(i).isVisible()) {
                return true;
            }
        }
        return false;
    }

    private void waitForRolePermissionsLoaded() {
        Locator loading = page.locator("div.flex.justify-center").filter(new Locator.FilterOptions().setHasText(LOADING_TEXT));
        if (loading.count() > 0 && loading.first().isVisible()) {
            loading.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(uiTimeoutMs()));
        }
        page.waitForCondition(() -> {
            Locator items = page.locator("li");
            for (int i = 0; i < items.count(); i++) {
                if (items.nth(i).innerText().startsWith("perm_")) {
                    return true;
                }
            }
            Locator empty = page.getByText("Дозволів немає");
            return empty.count() > 0 && empty.isVisible();
        }, new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
    }

    private Locator formTextInputs() {
        return page.locator("form input:not([type='hidden'])");
    }

    private void waitForPageReady() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(uiTimeoutMs()));
        } catch (Exception e) {
            log.debug("NETWORKIDLE not reached: {}", e.getMessage());
        }
    }

    private void waitForLoadingFinished() {
        Locator loading = page.getByText(LOADING_TEXT);
        if (loading.count() > 0 && loading.first().isVisible()) {
            loading.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(uiTimeoutMs()));
        }
    }

    private static String normalize(String value) {
        return value != null ? WHITESPACE.matcher(value.trim()).replaceAll(" ") : "";
    }
}
