package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.OperationHistoryPage;
import com.erp.pages.RelocationPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI RBAC: summary cards on «Історія операцій» follow sidebar tab permissions
 * ({@code defect::view}, {@code production::view}, {@code relocation::view},
 * {@code inventory::view}, {@code incident::view}).
 */
@Slf4j
@Epic("Operation History")
@Feature("Summary cards visibility by role")
public class OperationHistoryCardsVisibilityUiTest extends BaseUITest {

    private static final String NAV_DEFECT = "Брак";
    private static final String NAV_PRODUCTION = "Виробництво";
    private static final String NAV_RELOCATION = "Видати/Отримати";
    private static final String NAV_INVENTORY = "Залишки";

    private static final String CARD_RECEIVED = "Отримано";
    private static final String CARD_ISSUED = "Видано";
    private static final String CARD_PRODUCED = "Вироблено";
    private static final String CARD_USED = "Використано";
    private static final String CARD_INV_ADDED = "Додано (Інвентаризація)";
    private static final String CARD_INV_REMOVED = "Видалено (Інвентаризація)";
    private static final String CARD_DEFECT_ADDED = "Виявлено брак";
    private static final String CARD_DEFECT_REMOVED = "Списано брак";
    private static final String CARD_INCIDENT = "Надзвичайні події";

    @Test
    @TestCaseId("TC-UI-HIST-CARD-001")
    @Story("Owner sees all permission-gated summary cards")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            OWNER_1 (alkatras) має sidebar «Виробництво» (і PageTab «Брак»), «Видати/Отримати»,
            «Залишки», вкладку «Втрачено» (incident::view). На «Історія операцій» (/history) видимі картки:
            «Отримано», «Видано», «Вироблено», «Використано»,
            «Додано (Інвентаризація)», «Видалено (Інвентаризація)»,
            «Виявлено брак», «Списано брак», «Надзвичайні події».
            non-series-production поза scope — картки виробництва залежать лише від production::view.
            """)
    public void ownerSeesDefectAndProductionCards() {
        assertCardsVisibilityForRole(
                UserRole.OWNER_1,
                ConfigProvider.getOwner1StorageId(),
                true,
                true,
                true,
                true,
                true);
    }

    @Test
    @TestCaseId("TC-UI-HIST-CARD-002")
    @Story("Crew-Manager summary cards follow sidebar permissions")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            CREW_MANAGER (argument) не має sidebar «Виробництво» / PageTab «Брак» / incident::view;
            має «Видати/Отримати» та «Залишки». На «Історія операцій» (/history):
            картки браку, виробництва та «Надзвичайні події» приховані;
            «Отримано», «Видано», «Додано (Інвентаризація)», «Видалено (Інвентаризація)» видимі.
            non-series-production поза scope.
            """)
    public void crewManagerHidesDefectAndProductionCards() {
        assertCardsVisibilityForRole(
                UserRole.CREW_MANAGER,
                ConfigProvider.getUnitStorageId(),
                false,
                false,
                true,
                true,
                false);
    }

    private void assertCardsVisibilityForRole(
            UserRole role,
            long storageId,
            boolean expectDefectAccess,
            boolean expectProductionAccess,
            boolean expectRelocationAccess,
            boolean expectInventoryAccess,
            boolean expectIncidentAccess) {
        Allure.parameter("User", role.getUsername());
        Allure.parameter("storageId", storageId);
        Allure.parameter("expectDefectAccess", expectDefectAccess);
        Allure.parameter("expectProductionAccess", expectProductionAccess);
        Allure.parameter("expectRelocationAccess", expectRelocationAccess);
        Allure.parameter("expectInventoryAccess", expectInventoryAccess);
        Allure.parameter("expectIncidentAccess", expectIncidentAccess);

        injectRoleSession(role, storageId);
        page = browserContext.newPage();

        OperationHistoryPage history = Allure.step("Відкрити «Історія операцій»", () -> {
            OperationHistoryPage pageObj = new OperationHistoryPage(page).open();
            assertThat(pageObj.isLoaded())
                    .as("Сторінка «Історія операцій» має завантажитись для %s", role.getUsername())
                    .isTrue();
            return pageObj;
        });

        AppSidebarPage sidebar = new AppSidebarPage(page);

        Allure.step("Перевірити sidebar-вкладки (передумова ролі в Keycloak)", () -> {
            assertNav(sidebar, NAV_PRODUCTION, expectProductionAccess, role);
            assertNav(sidebar, NAV_RELOCATION, expectRelocationAccess, role);
            assertNav(sidebar, NAV_INVENTORY, expectInventoryAccess, role);
            assertDefectNav(sidebar, expectDefectAccess, role);
            assertIncidentNav(expectRelocationAccess, expectIncidentAccess, role);
        });

        OperationHistoryPage historyAfterNav = Allure.step("Повернутись на «Історія операцій» після перевірки навігації", () -> {
            OperationHistoryPage pageObj = new OperationHistoryPage(page).open();
            assertThat(pageObj.isLoaded()).isTrue();
            return pageObj;
        });

        Allure.step("Перевірити видимість summary-карток", () -> {
            assertCard(historyAfterNav, CARD_RECEIVED, expectRelocationAccess);
            assertCard(historyAfterNav, CARD_ISSUED, expectRelocationAccess);
            assertCard(historyAfterNav, CARD_PRODUCED, expectProductionAccess);
            assertCard(historyAfterNav, CARD_USED, expectProductionAccess);
            assertCard(historyAfterNav, CARD_INV_ADDED, expectInventoryAccess);
            assertCard(historyAfterNav, CARD_INV_REMOVED, expectInventoryAccess);
            assertCard(historyAfterNav, CARD_DEFECT_ADDED, expectDefectAccess);
            assertCard(historyAfterNav, CARD_DEFECT_REMOVED, expectDefectAccess);
            assertCard(historyAfterNav, CARD_INCIDENT, expectIncidentAccess);
        });

        historyAfterNav.attachScreenshot(role.getUsername() + " — history cards visibility");
    }

    private static void assertNav(AppSidebarPage sidebar, String label, boolean expected, UserRole role) {
        assertThat(sidebar.isNavItemVisible(label))
                .as("Sidebar «%s» для %s (роль у Keycloak змінилась?)", label, role.getUsername())
                .isEqualTo(expected);
    }

    /** «Брак» is a PageTab under «Виробництво», not a direct sidebar link. */
    private static void assertDefectNav(AppSidebarPage sidebar, boolean expected, UserRole role) {
        if (expected) {
            assertThat(sidebar.isNavItemVisible(NAV_PRODUCTION))
                    .as("Sidebar «Виробництво» для %s (потрібен для PageTab «Брак»)", role.getUsername())
                    .isTrue();
            sidebar.openGroup(NAV_PRODUCTION);
            assertThat(sidebar.isPageTabVisible(NAV_DEFECT))
                    .as("PageTab «Брак» для %s", role.getUsername())
                    .isTrue();
        } else if (sidebar.isNavItemVisible(NAV_PRODUCTION)) {
            sidebar.openGroup(NAV_PRODUCTION);
            assertThat(sidebar.isPageTabVisible(NAV_DEFECT))
                    .as("PageTab «Брак» має бути прихований для %s", role.getUsername())
                    .isFalse();
        }
    }

    /** «Втрачено» is a journal tab on /relocations, gated by incident::view. */
    private void assertIncidentNav(boolean hasRelocationAccess, boolean expectIncidentAccess, UserRole role) {
        if (!hasRelocationAccess) {
            return;
        }
        RelocationPage relocationPage = new RelocationPage(page).open();
        assertThat(relocationPage.isLostTabVisible())
                .as("Вкладка «Втрачено» для %s (incident::view)", role.getUsername())
                .isEqualTo(expectIncidentAccess);
    }

    private static void assertCard(OperationHistoryPage history, String cardTitle, boolean expected) {
        assertThat(history.isSummaryCardVisible(cardTitle))
                .as("Картка «%s»", cardTitle)
                .isEqualTo(expected);
    }

    private void injectRoleSession(UserRole role, long selectedStorageId) {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(role.getUsername(), role.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
    }
}
