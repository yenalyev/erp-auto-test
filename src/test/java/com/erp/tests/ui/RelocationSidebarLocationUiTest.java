package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.RelocationSidebarFixture;
import com.erp.models.response.StorageResponse;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.RelocationSidebarPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import org.testng.annotations.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Relocation")
@Feature("REQ-REL-SIDEBAR — локація адміна у Від кого / Кому")
@Story("Видано та Отримано")
@Severity(SeverityLevel.CRITICAL)
public class RelocationSidebarLocationUiTest extends BaseUITest {
    private RelocationSidebarFixture data;

    @BeforeClass(alwaysRun = true)
    @Override public void baseTestClassSetup() {
        super.baseTestClassSetup();
        data = new RelocationSidebarFixture(testContext, apiExecutor);
        data.prepare();
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
    }

    @AfterClass(alwaysRun = true)
    public void cleanupSidebarData() { if (data != null) data.cleanup(); }

    @DataProvider(name = "historySides")
    public Object[][] historySides() {
        return new Object[][] {{"Видано", "Від кого"}, {"Отримано", "Кому"}};
    }

    @Test(dataProvider = "historySides")
    @TestCaseId("TC-REL-SIDEBAR-UI-001")
    @Description("ADMIN обирає A у справжньому сайдбарі до переходу в журнал: Видано → fromIds=A, "
            + "Отримано → toIds=A; протилежне поле Усі. Перевіряються записи циклу A→B→C→A.")
    public void selectedWorkspacePrefillsHistorySide(String tab, String side) {
        AppSidebarPage sidebar = start();
        sidebar.selectWorkspaceByName(data.locations.getFirst().getName());
        sidebar.openGroup("Видати/Отримати");
        try (var journal = new RelocationSidebarPage(page)) {
            journal.tab(tab);
            verify(journal, "001", side, data.locations.getFirst());
        }
    }

    @Test(dataProvider = "historySides")
    @TestCaseId("TC-REL-SIDEBAR-UI-002")
    @Description("ADMIN у відкритій вкладці змінює сайдбар A→B: цільове поле й запит замінюють A на B, "
            + "контрольні записи A/C відсутні. Початковий A задано через UI для незалежності від initial prefill.")
    public void workspaceChangeReplacesPreviousLocation(String tab, String side) {
        AppSidebarPage sidebar = start();
        sidebar.selectWorkspaceByName(data.locations.getFirst().getName());
        sidebar.openGroup("Видати/Отримати");
        try (var journal = new RelocationSidebarPage(page)) {
            journal.tab(tab);
            ensureFilter(journal, side, data.locations.getFirst());
            sidebar.selectWorkspaceByName(data.locations.get(1).getName());
            verify(journal, "002", side, data.locations.get(1));
        }
    }

    @Test(dataProvider = "historySides")
    @TestCaseId("TC-REL-SIDEBAR-UI-003")
    @Description("ADMIN переходить A→Всі локації: перенесений фільтр очищений, fromIds/toIds відсутні; "
            + "повертаються всі три власні завершені передачі.")
    public void allLocationsClearsTransferredScope(String tab, String side) {
        AppSidebarPage sidebar = start();
        sidebar.selectWorkspaceByName(data.locations.getFirst().getName());
        sidebar.openGroup("Видати/Отримати");
        try (var journal = new RelocationSidebarPage(page)) {
            journal.tab(tab);
            ensureFilter(journal, side, data.locations.getFirst());
            sidebar.selectAllLocations();
            verify(journal, "003", side, null);
        }
    }

    @Test(dataProvider = "historySides")
    @TestCaseId("TC-REL-SIDEBAR-UI-004")
    @Description("Всі локації до відкриття журналу: обидва поля Усі, жодної прихованої прив'язки "
            + "senderIds/receiverIds; у таблиці всі три власні передачі.")
    public void globalWorkspaceStartsWithoutLocationFilter(String tab, String side) {
        start().selectAllLocations().openGroup("Видати/Отримати");
        try (var journal = new RelocationSidebarPage(page)) {
            journal.tab(tab);
            verify(journal, "004", side, null);
        }
    }

    @Test(dataProvider = "historySides")
    @TestCaseId("TC-REL-SIDEBAR-UI-005")
    @Description("Після вибору робочої A адміністратор вручну задає B у цільовому полі; "
            + "reload зберігає B та записи B, сайдбар лишається A, прихованої прив'язки до A немає.")
    public void explicitFilterSurvivesReloadWithoutChangingWorkspace(String tab, String side) {
        AppSidebarPage sidebar = start();
        sidebar.selectWorkspaceByName(data.locations.getFirst().getName());
        sidebar.openGroup("Видати/Отримати");
        try (var journal = new RelocationSidebarPage(page)) {
            journal.tab(tab);
            StorageResponse alternative = data.locations.get(1);
            journal.clearFilter(side);
            journal.selectFilter(side, alternative.getName());
            verify(journal, "005", side, alternative);
            journal.reload();
            verify(journal, "005", side, alternative);
            assertThat(sidebar.getSelectedLocationName()).isEqualTo(data.locations.getFirst().getName());
        }
    }

    private AppSidebarPage start() {
        page.navigate(ConfigProvider.getBaseUrl());
        page.evaluate("localStorage.clear()");
        // Only initialise once on this Page; do not use addInitScript, which would undo real switches/reload.
        page.evaluate("([u,s]) => {localStorage.setItem('selectedStorageId',s);"
                        + "localStorage.setItem('selectedStorageId:'+u,s)}", List.of(UserRole.ADMIN.getUsername(), "all"));
        page.navigate(ConfigProvider.getBaseUrl() + "/inventory");
        return new AppSidebarPage(page).waitForSidebarLoaded();
    }

    private void ensureFilter(RelocationSidebarPage journal, String side, StorageResponse location) {
        if (!journal.filterName(side).equals(location.getName())) journal.selectFilter(side, location.getName());
    }

    private void verify(RelocationSidebarPage journal, String suffix, String side, StorageResponse location) {
        Long id = location == null ? null : location.getId();
        journal.assertScope("TC-REL-SIDEBAR-UI-" + suffix, side, location == null ? null : location.getName(),
                id, data.marker, data.expectedMarkers(side, id));
    }
}
