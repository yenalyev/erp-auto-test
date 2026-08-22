package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.StorageFixture;
import com.erp.models.response.StorageResponse;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.UnitAnalyticsPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI: REQ-UA-001 Обороти — вкладки на /unit-analytics для користувача з perm unit-analytics.
 */
@Slf4j
@Epic("Аналітика підрозділів")
@Feature("Обороти")
@Story("Вкладки оборотів для юзера з permission")
public class UnitAnalyticsUiTest extends BaseUITest {

    private StorageFixture storageFixture;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storageFixture = new StorageFixture(testContext, apiExecutor);
    }

    @Test(priority = 1)
    @TestCaseId("TC-UI-UA-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-UA-001 / AC-02.
            Вкладки «За період», «По місяцях», «По ресурсах» завантажуються без помилки API:
            таблиця або порожній стан, немає toast/банера «Не вдалося завантажити…».

            Передумови: 3bat (UserRole.UNIT_ANALYST) має unit-analytics::read (dev/staging).

            1) Залогінитися як 3bat і відкрити /unit-analytics
               → URL містить /unit-analytics; у сайдбарі є «Аналітика Підрозділів»;
                 видимі вкладки «Обороти», «За період», «По місяцях», «По ресурсах»
            2) «За період» → заголовок «Використано та отримано»; таблиця або порожній стан
            3) «По місяцях» → таблиця «Рік/Місяць» або порожній стан
            4) «По ресурсах» → пошук «Пошук за назвою ресурсу»; таблиця без банера помилки

            Відомий дефект продукту (PeriodTab): у рядках таблиці «За період»
            клітинки usedAmount/receivedAmount поміняні місцями відносно заголовків
            «Отримано» / «Використано»; footer «Разом» мапиться правильно.
            Цей кейс не перевіряє числову відповідність колонок.
            """)
    public void unitAnalyticsTurnoverTabsLoadForPermittedUser() {
        loginAsUnitAnalyst();

        UnitAnalyticsPage analytics = new UnitAnalyticsPage(page).open();

        assertThat(page.url())
                .as("Має відкритися /unit-analytics")
                .contains("/unit-analytics");
        assertThat(analytics.isSidebarLinkVisible())
                .as("Сайдбар: «Аналітика Підрозділів» для юзера з perm unit-analytics")
                .isTrue();
        assertThat(analytics.isTurnoverTabVisible()).as("Вкладка «Обороти»").isTrue();
        assertThat(analytics.isPeriodTabVisible()).as("Вкладка «За період»").isTrue();
        assertThat(analytics.isByMonthTabVisible()).as("Вкладка «По місяцях»").isTrue();
        assertThat(analytics.isByResourceTabVisible()).as("Вкладка «По ресурсах»").isTrue();
        analytics.attachScreenshot("TC-UI-UA-001 — tabs visible");

        analytics.openPeriodTab();
        assertThat(analytics.hasLoadError())
                .as("«За період» не повинна показувати помилку завантаження")
                .isFalse();
        assertThat(analytics.isPeriodContentReady())
                .as("«За період»: таблиця або порожній стан під заголовком «Використано та отримано»")
                .isTrue();
        analytics.attachScreenshot("TC-UI-UA-001 — За період");

        analytics.openByMonthTab();
        assertThat(analytics.hasLoadError())
                .as("«По місяцях» не повинна показувати помилку завантаження")
                .isFalse();
        assertThat(analytics.isByMonthContentReady())
                .as("«По місяцях»: таблиця (Рік/Місяць) або порожній стан")
                .isTrue();
        analytics.attachScreenshot("TC-UI-UA-001 — По місяцях");

        analytics.openByResourceTab();
        assertThat(analytics.hasLoadError())
                .as("«По ресурсах» не повинна показувати помилку завантаження")
                .isFalse();
        assertThat(analytics.isByResourceContentReady())
                .as("«По ресурсах»: пошук ресурсу і таблиця без банера помилки")
                .isTrue();
        analytics.attachScreenshot("TC-UI-UA-001 — По ресурсах");
    }

    @Test(priority = 2)
    @TestCaseId("TC-UI-UA-002")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Скоуп дерева при «Всі локації»")
    @Description("""
            REQ-UA-001 / AC-03.
            Якщо в «Робочий простір» немає конкретної локації («Всі локації»),
            вкладка «По місяцях» показує лише батьківський підрозділ 3bat та його дерево.

            Відомий дефект: без parentStorageId GET /unit-analytics/by-month
            повертає всі BATALION системи (SQL strpos(path, '/') при null).
            Тест фіксує очікувану поведінку і буде червоним до фіксу в SUT.

            1) Логін 3bat → /unit-analytics → «Всі локації»
            2) Oracle скоупу: «За період» + лейбли дерева в сайдбарі
            3) «По місяцях»: усі «Підрозділ» ∈ дерево; сторонній підрозділ відсутній
            """)
    public void monthlyTabAllLocationsScopedToOwnerTree() {
        UnitAnalyticsPage analytics = openAllLocationsAnalytics();
        List<String> allowed = collectAllowedTree(analytics);
        String outsider = findOutsiderUnitName(allowed);

        analytics.openByMonthTab();
        assertThat(analytics.hasLoadError())
                .as("«По місяцях» не повинна показувати помилку завантаження")
                .isFalse();
        List<String> monthly = analytics.collectMonthlyUnitNames();
        analytics.attachScreenshot("TC-UI-UA-002 — По місяцях / Всі локації");

        assertTabScopedToTree("По місяцях", monthly, allowed, outsider);
    }

    @Test(priority = 3)
    @TestCaseId("TC-UI-UA-003")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Скоуп дерева при «Всі локації»")
    @Description("""
            REQ-UA-001 / AC-03.
            Якщо в «Робочий простір» немає конкретної локації («Всі локації»),
            вкладка «По ресурсах» показує лише батьківський підрозділ 3bat та його дерево.

            Відомий дефект: без parentStorageId GET /unit-analytics/by-resource
            повертає всі BATALION системи. Тест фіксує очікувану поведінку
            і буде червоним до фіксу в SUT.

            1) Логін 3bat → /unit-analytics → «Всі локації»
            2) Oracle скоупу: «За період» + лейбли дерева в сайдбарі
            3) «По ресурсах»: усі «Підрозділ» ∈ дерево; сторонній підрозділ відсутній
            """)
    public void byResourceTabAllLocationsScopedToOwnerTree() {
        UnitAnalyticsPage analytics = openAllLocationsAnalytics();
        List<String> allowed = collectAllowedTree(analytics);
        String outsider = findOutsiderUnitName(allowed);

        analytics.openByResourceTab();
        assertThat(analytics.hasLoadError())
                .as("«По ресурсах» не повинна показувати помилку завантаження")
                .isFalse();
        List<String> byResource = analytics.collectByResourceUnitNames();
        analytics.attachScreenshot("TC-UI-UA-003 — По ресурсах / Всі локації");

        assertTabScopedToTree("По ресурсах", byResource, allowed, outsider);
    }

    private UnitAnalyticsPage openAllLocationsAnalytics() {
        loginAsUnitAnalyst();
        UnitAnalyticsPage analytics = new UnitAnalyticsPage(page).open();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        assertThat(sidebar.isWorkspaceSelectorVisible())
                .as("Селектор «Робочий простір» має бути видимим (3bat має дочірні локації)")
                .isTrue();
        sidebar.selectAllLocations();
        analytics.waitForLoaded();
        String storedId = (String) page.evaluate("() => localStorage.getItem('selectedStorageId')");
        assertThat(storedId)
                .as("Режим без конкретної локації: localStorage.selectedStorageId=all («Всі локації»)")
                .isEqualTo("all");
        return analytics;
    }

    private List<String> collectAllowedTree(UnitAnalyticsPage analytics) {
        analytics.openPeriodTab();
        Set<String> allowed = new LinkedHashSet<>(analytics.collectPeriodUnitNames());
        List<String> workspace = new AppSidebarPage(page).collectWorkspaceLocationLabels();
        allowed.addAll(workspace);
        assertThat(workspace)
                .as("У дерева 3bat мають бути дочірні локації в «Робочий простір»")
                .isNotEmpty();
        return new ArrayList<>(allowed);
    }

    private String findOutsiderUnitName(List<String> allowed) {
        List<StorageResponse> adminNames = storageFixture.getNames(UserRole.ADMIN, true, null);
        return adminNames.stream()
                .map(StorageResponse::getName)
                .filter(name -> name != null && !name.isBlank())
                .filter(name -> !belongsToTree(name, allowed))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Не знайдено сторонній підрозділ поза деревом 3bat у GET /storages/names"));
    }

    private void assertTabScopedToTree(String tab,
                                       List<String> actual,
                                       List<String> allowed,
                                       String outsider) {
        List<String> outside = actual.stream()
                .filter(name -> !belongsToTree(name, allowed))
                .toList();
        assertThat(outside)
                .as("%s у «Всі локації»: підрозділи мають належати дереву 3bat, зайві=%s", tab, outside)
                .isEmpty();
        assertThat(actual)
                .as("%s не повинна показувати сторонній підрозділ «%s»", tab, outsider)
                .noneMatch(name -> namesMatch(name, outsider));
    }

    private static boolean belongsToTree(String unitName, List<String> allowed) {
        return allowed.stream().anyMatch(label -> namesMatch(unitName, label));
    }

    private static boolean namesMatch(String left, String right) {
        String a = normalizeName(left);
        String b = normalizeName(right);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        return (a.length() >= 3 && b.contains(a)) || (b.length() >= 3 && a.contains(b));
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private void loginAsUnitAnalyst() {
        browserContext.clearCookies();
        var cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.UNIT_ANALYST.getUsername(), UserRole.UNIT_ANALYST.getPassword());
        String domain = ConfigProvider.getBaseUrl().replaceFirst("https?://", "").split("/")[0];
        injectSessionCookies(cookies, domain);
        if (page != null) {
            page.close();
        }
        page = browserContext.newPage();
        int timeoutMs = ConfigProvider.getUiTimeoutSeconds() * 1000;
        page.setDefaultTimeout(timeoutMs);
        page.setDefaultNavigationTimeout(timeoutMs);
    }
}
