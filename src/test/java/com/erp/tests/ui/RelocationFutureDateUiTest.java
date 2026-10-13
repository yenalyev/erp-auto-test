package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Relocation")
@Feature("Issue date")
@Story("Future dates are unavailable on issue forms")
public class RelocationFutureDateUiTest extends BaseUITest {

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
        injectWorkspaceView(UserRole.ADMIN, ConfigProvider.getOwner1StorageId());
    }

    @Test
    @TestCaseId("TC-UI-REL-DATE-001")
    @Severity(SeverityLevel.NORMAL)
    @Description("У київську північ сьогоднішня дата доступна, а завтрашня заблокована на формах видачі.")
    public void issueFormsUseKyivCalendarDay() {
        // 21:30 UTC is already 00:30 in Kyiv on 12 October.
        page.clock().install();
        page.clock().setFixedTime("2026-10-11T21:30:00Z");

        for (String path : new String[] {
                "/relocation/create-output",
                "/relocation/create-output-crew",
                "/relocation/create-output-fly-point"
        }) {
            page.navigate(ConfigProvider.getBaseUrl() + path);
            Locator date = page.locator("input[type='date']").first();
            date.waitFor();
            assertThat(date.getAttribute("max"))
                    .as("Поточний день Києва у формі %s", path)
                    .isEqualTo("2026-10-12");
            assertThat((Boolean) date.evaluate("element => { element.value = '2026-10-13'; return element.validity.rangeOverflow; }"))
                    .as("Майбутня дата недоступна у формі %s", path)
                    .isTrue();
        }
    }
}
