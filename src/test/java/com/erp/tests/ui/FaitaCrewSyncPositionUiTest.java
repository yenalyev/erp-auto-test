package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Read-only check that the raw journal position has its own write-off column. */
@Epic("Integration")
@Feature("FAITA crew sync and write-off")
public class FaitaCrewSyncPositionUiTest extends BaseUITest {

    @TestCaseId("TC-FAITA-SYNC-POS-UI-001")
    @Description("The write-off journal displays a distinct Позиція з ЖБД column")
    @Severity(SeverityLevel.CRITICAL)
    @Test
    public void writeOffJournalShowsJournalPositionColumn() {
        injectRoleSession(UserRole.ADMIN, ConfigProvider.getOwner1StorageId());
        page.navigate(ConfigProvider.getBaseUrl() + "/inventory-write-off");
        page.waitForTimeout(1000);
        assertThat(page.url()).as("write-off journal route must remain open")
                .contains("/inventory-write-off");
        page.getByRole(AriaRole.COLUMNHEADER,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Джерело"))
                .waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(5000));
        assertThat(page.getByRole(AriaRole.COLUMNHEADER,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Позиція з ЖБД"))
                .isVisible()).as("separate source position column").isTrue();
    }

    private void injectRoleSession(UserRole role, long selectedStorageId) {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(role.getUsername(), role.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        injectWorkspaceView(role, selectedStorageId);
    }
}
