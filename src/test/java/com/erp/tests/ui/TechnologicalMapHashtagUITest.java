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
import com.erp.utils.helpers.TechnologicalMapTagAssertions;
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
        assertThat(listPage.getHighlightedTagsForRow(rowIndex))
                .as("Підсвічений тег у колонці приміток")
                .contains(tag);

        listPage.attachScreenshot("TC-UI-TM-TAG-001 — notes saved");
        log.info("TC-UI-TM-TAG-001 PASSED — techMap={}, tag={}", techMapName, tag);
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-TM-TAG-002")
    @Story("Tag filter badge on tech map list")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            API: tech-map з #тегом → UI: badge у toolbar, клік фільтрує список.
            Cross-check через GET /technological-maps?tags=.
            """)
    public void tagFilterBadgeFiltersList() {
        String tag = HashtagTestData.uniqueTag("tm-filter");
        TechnologicalMapFixture.IsolatedTechMapContext isolated =
                techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, storageId);
        techMapFixture.updateNotes(
                UserRole.ADMIN, isolated.getTechMap().getId(), storageId, tag + " UI filter");
        String techMapName = isolated.getTechMap().getName();
        String productName = isolated.getProduct().getName();
        Allure.parameter("tag", tag);
        Allure.parameter("techMapId", isolated.getTechMap().getId());

        TechnologicalMapsListPage listPage = new TechnologicalMapsListPage(page).openForStorage(storageId);
        listPage.filterByProduct(productName);
        listPage.refreshTagStatistics(productName);

        assertThat(listPage.isTagBadgeVisible(tag))
                .as("Badge тегу %s має бути видимим після фільтра продукту", tag)
                .isTrue();

        listPage.clickTagFilterBadge(tag);
        assertThat(listPage.isTagBadgeSelected(tag))
                .as("Badge %s має бути вибраним", tag)
                .isTrue();
        assertThat(listPage.rowWithTechMapNameIsVisible(techMapName))
                .as("Техкарта %s має лишатися у відфільтрованому списку", techMapName)
                .isTrue();

        TechnologicalMapTagAssertions.assertFilteredByTag(
                techMapFixture, storageId, tag, isolated.getTechMap().getId());

        listPage.attachScreenshot("TC-UI-TM-TAG-002 — tag filter");
        log.info("TC-UI-TM-TAG-002 PASSED — techMap={}, tag={}", techMapName, tag);
    }

    @Test(priority = 30)
    @TestCaseId("TC-UI-TM-TAG-003")
    @Story("Space inside tag on UI")
    @Severity(SeverityLevel.NORMAL)
    @Description("UI: «#два слова» — підсвічується лише #два.")
    public void spaceInsideTagShowsFirstTokenOnly() {
        TechnologicalMapFixture.IsolatedTechMapContext isolated =
                techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, storageId);
        String techMapName = isolated.getTechMap().getName();

        TechnologicalMapsListPage listPage = new TechnologicalMapsListPage(page).openForStorage(storageId);
        listPage.filterByProduct(isolated.getProduct().getName());
        listPage.openNotesEditorForTechMapName(techMapName)
                .fillNotesDialog("#два слова")
                .saveNotesDialog();

        int rowIndex = listPage.findRowIndexByTechMapName(techMapName);
        assertThat(listPage.getHighlightedTagsForRow(rowIndex)).containsExactly("#два");
        assertThat(listPage.getNotesTextForRow(rowIndex)).contains("слова");

        TechnologicalMapTagAssertions.assertFilteredByTag(
                techMapFixture, storageId, "#два", isolated.getTechMap().getId());
        TechnologicalMapTagAssertions.assertNotFilteredByTag(
                techMapFixture, storageId, "#два слова", isolated.getTechMap().getId());

        listPage.attachScreenshot("TC-UI-TM-TAG-003 — space tag");
        log.info("TC-UI-TM-TAG-003 PASSED — techMap={}", techMapName);
    }
}
