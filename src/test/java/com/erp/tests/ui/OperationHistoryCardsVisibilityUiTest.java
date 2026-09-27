package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.BusinessRole;
import com.erp.enums.LocationProfile;
import com.erp.enums.UserRole;
import com.erp.fixtures.AccessFixture;
import com.erp.fixtures.LocationProfileFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.access.GrantScopeKind;
import com.erp.models.response.StorageResponse;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.OperationHistoryPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI RBAC: summary cards on «Історія операцій» follow effective permissions
 * ({@code defect::view}, {@code production::view}, {@code relocation::view}, {@code inventory::view}).
 * Sidebar links also depend on the selected location's features.
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
    private static final String CARD_EQUIPMENT_PRODUCED = OperationHistoryPage.EQUIPMENT_PRODUCED_CARD;
    private static final String CARD_INV_ADDED = "Додано (Інвентаризація)";
    private static final String CARD_INV_REMOVED = "Видалено (Інвентаризація)";
    private static final String CARD_DEFECT_ADDED = "Виявлено брак";
    private static final String CARD_DEFECT_REMOVED = "Списано брак";

    @Test
    @TestCaseId("TC-UI-HIST-CARD-001")
    @Story("Owner sees all permission-gated summary cards")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            OWNER_1 (alkatras) має sidebar «Виробництво», «Брак», «Видати/Отримати»,
            «Залишки». На «Історія операцій» (/history) видимі картки:
            «Отримано», «Видано», «Вироблено», «Використано», «Обладнання (виготовлено)»,
            «Додано (Інвентаризація)», «Видалено (Інвентаризація)»,
            «Виявлено брак», «Списано брак».
            non-series-production поза scope — картки виробництва залежать лише від production::view.
            """)
    public void ownerSeesDefectAndProductionCards() {
        assertCardsVisibilityForRole(
                UserRole.OWNER_1,
                ConfigProvider.getOwner1StorageId(),
                true,
                true,
                true,
                true);
    }

    @Test
    @TestCaseId("TC-UI-HIST-CARD-002")
    @Story("Battalion location head with crew read sees permitted summary cards")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Ізольований керівник дочірньої локації батальйону має додаткову роль
            «Екіпажі: перегляд» на батальйоні. Вкладка «Виробництво» прихована,
            бо дочірня локація не має функції PRODUCE. Роль «Керівник локації» має
            права production::view та defect::view, тому на «Історія операцій» (/history)
            картки виробництва, обладнання, браку, переміщень та інвентаризації видимі.
            non-series-production поза scope.
            """)
    public void crewReaderLocationOwnerSeesPermittedCards() {
        LocationProfileFixture locations = new LocationProfileFixture(testContext, apiExecutor);
        UserFixture users = new UserFixture(testContext, apiExecutor);
        try {
            LocationProfileFixture.LocationSet locationSet = locations.create(
                    LocationProfile.BATTALION_WARENHAUSE_UNIT, 1);
            StorageResponse battalion = locationSet.parent();
            StorageResponse warehouse = locationSet.locations().getFirst();
            UserFixture.BusinessActor actor = users.createBusinessActor(
                    getPlaywrightSessionProvider(), BusinessRole.BUSINESS_UNIT_OWNER, List.of(warehouse));
            new AccessFixture(testContext, apiExecutor).ensureGrants(
                    actor.userId(), List.of(UserFixture.CREW_READ_ROLE_NAME), List.of(),
                    GrantScopeKind.LOCATION, battalion.getId());

            assertCardsVisibilityForUser(actor.username(), actor.password(), warehouse.getId(),
                    true, false, true, true, true);
        } finally {
            users.deactivateTrackedUsers();
            locations.cleanup();
        }
    }

    private void assertCardsVisibilityForRole(
            UserRole role,
            long storageId,
            boolean expectDefectAccess,
            boolean expectProductionAccess,
            boolean expectRelocationAccess,
            boolean expectInventoryAccess) {
        assertCardsVisibilityForUser(role.getUsername(), role.getPassword(), storageId,
                expectDefectAccess, expectProductionAccess, expectProductionAccess, expectRelocationAccess,
                expectInventoryAccess);
    }

    private void assertCardsVisibilityForUser(
            String username,
            String password,
            long storageId,
            boolean expectDefectAccess,
            boolean expectProductionNav,
            boolean expectProductionCards,
            boolean expectRelocationAccess,
            boolean expectInventoryAccess) {
        Allure.parameter("User", username);
        Allure.parameter("storageId", storageId);
        Allure.parameter("expectDefectAccess", expectDefectAccess);
        Allure.parameter("expectProductionNav", expectProductionNav);
        Allure.parameter("expectProductionCards", expectProductionCards);
        Allure.parameter("expectRelocationAccess", expectRelocationAccess);
        Allure.parameter("expectInventoryAccess", expectInventoryAccess);

        injectUserSession(username, password, storageId);
        page.close();
        page = browserContext.newPage();

        OperationHistoryPage history = Allure.step("Відкрити «Історія операцій»", () -> {
            OperationHistoryPage pageObj = new OperationHistoryPage(page).open();
            assertThat(pageObj.isLoaded())
                    .as("Сторінка «Історія операцій» має завантажитись для %s", username)
                    .isTrue();
            return pageObj;
        });

        AppSidebarPage sidebar = new AppSidebarPage(page);

        Allure.step("Перевірити sidebar-вкладки (передумова ролі в Keycloak)", () -> {
            assertNav(sidebar, NAV_PRODUCTION, expectProductionNav, username);
            assertNav(sidebar, NAV_RELOCATION, expectRelocationAccess, username);
            assertNav(sidebar, NAV_INVENTORY, expectInventoryAccess, username);
            assertDefectNav(sidebar, expectDefectAccess, username);
        });

        OperationHistoryPage historyAfterNav = Allure.step("Повернутись на «Історія операцій» після перевірки навігації", () -> {
            OperationHistoryPage pageObj = new OperationHistoryPage(page).open();
            assertThat(pageObj.isLoaded()).isTrue();
            return pageObj;
        });

        Allure.step("Перевірити видимість summary-карток", () -> {
            assertCard(historyAfterNav, CARD_RECEIVED, expectRelocationAccess);
            assertCard(historyAfterNav, CARD_ISSUED, expectRelocationAccess);
            assertCard(historyAfterNav, CARD_PRODUCED, expectProductionCards);
            assertCard(historyAfterNav, CARD_USED, expectProductionCards);
            assertEquipmentCard(historyAfterNav, CARD_EQUIPMENT_PRODUCED, expectProductionCards);
            assertCard(historyAfterNav, CARD_INV_ADDED, expectInventoryAccess);
            assertCard(historyAfterNav, CARD_INV_REMOVED, expectInventoryAccess);
            assertCard(historyAfterNav, CARD_DEFECT_ADDED, expectDefectAccess);
            assertCard(historyAfterNav, CARD_DEFECT_REMOVED, expectDefectAccess);
        });

        historyAfterNav.attachScreenshot(username + " — history cards visibility");
    }

    private static void assertNav(AppSidebarPage sidebar, String label, boolean expected, String username) {
        assertThat(sidebar.isNavItemVisible(label))
                .as("Sidebar «%s» для %s", label, username)
                .isEqualTo(expected);
    }

    /** «Брак» is a direct sidebar link in the current navigation. */
    private static void assertDefectNav(AppSidebarPage sidebar, boolean expected, String username) {
        assertThat(sidebar.isNavItemVisible(NAV_DEFECT))
                .as("Sidebar «Брак» для %s", username)
                .isEqualTo(expected);
    }

    private static void assertCard(OperationHistoryPage history, String cardTitle, boolean expected) {
        assertThat(history.isSummaryCardVisible(cardTitle))
                .as("Картка «%s»", cardTitle)
                .isEqualTo(expected);
    }

    private static void assertEquipmentCard(OperationHistoryPage history, String cardTitle, boolean expected) {
        assertThat(history.isEquipmentSummaryCardVisible(cardTitle))
                .as("Equipment-картка «%s»", cardTitle)
                .isEqualTo(expected);
    }

    private void injectUserSession(String username, String password, long selectedStorageId) {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(username, password);
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');"
                        + "localStorage.setItem('selectedStorageId:" + username
                        + "', '" + selectedStorageId + "');");
    }
}
