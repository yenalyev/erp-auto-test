package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProductionFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.query.ProductionJournalQuery;
import com.erp.pages.ProductionPage;
import com.erp.pages.TechnologicalMapsListPage;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.HashtagTestData;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Technological Maps")
@Feature("Hashtags / Notes UI")
public class TechnologicalMapHashtagUITest extends BaseUITest {

    private ProductionFixture productionFixture;
    private TechnologicalMapFixture techMapFixture;
    private long storageId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        productionFixture = new ProductionFixture(testContext, apiExecutor);
        techMapFixture = productionFixture.getTechMapFixture();
        techMapFixture.prepareContext();
        storageId = ConfigProvider.getOwner1StorageId();

        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.OWNER_1.getUsername(), UserRole.OWNER_1.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + storageId + "');"
                        + "localStorage.setItem('" + ProductionPage.pageSizeStorageKey() + "', '"
                        + ProductionJournalQuery.DEFAULT_UI_PAGE_SIZE + "');");
    }

    @Test(priority = 10)
    @TestCaseId("TC-UI-TM-TAG-001")
    @Story("Edit tech map notes on list UI")
    @Severity(SeverityLevel.CRITICAL)
    @Description("UI: редагування приміток у таблиці /technological-maps з #tm-tag.")
    public void editTechMapNotesOnListUi() {
        String tag = HashtagTestData.uniqueTag("tm-ui");
        TechnologicalMapFixture.IsolatedTechMapContext isolated =
                techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, storageId);
        String techMapName = isolated.getTechMap().getName();

        TechnologicalMapsListPage listPage = Allure.step(
                "Відкрити список техкарт для локації",
                () -> new TechnologicalMapsListPage(page).openForStorage(storageId));

        assertThat(listPage.isNotesColumnVisible())
                .as("Колонка «Примітки» має бути видима при вибраній локації")
                .isTrue();

        listPage.filterByProduct(isolated.getProduct().getName());
        listPage.openNotesEditorForTechMapName(techMapName)
                .fillNotesDialog(tag + " з UI")
                .saveNotesDialog();

        int rowIndex = listPage.findRowIndexByTechMapName(techMapName);
        assertThat(listPage.getNotesTextForRow(rowIndex))
                .as("Примітки техкарти після збереження")
                .contains(tag);

        listPage.attachScreenshot("TC-UI-TM-TAG-001 — notes saved");
        log.info("TC-UI-TM-TAG-001 PASSED — techMap={}, tag={}", techMapName, tag);
    }
}
