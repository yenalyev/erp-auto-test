package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.LocationFeature;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.UnitType;
import com.erp.enums.StorageRelation;
import com.erp.enums.UserRole;
import com.erp.fixtures.IsolatedRestrictedOwnerScope;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.StorageResponse;
import com.erp.pages.RelocationPage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Set;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

@Epic("Relocation")
@Feature("Location selectors require RELOCATIONS")
public class RelocationLocationSelectorsUiTest extends BaseUITest {
    private static final String LOCATION_INPUT = "input[placeholder='Оберіть склад...']";
    private static final String OPTION = "[data-slot='combobox-item']";

    private StorageFixture storages;
    private StorageRegionFixture regions;
    private RelocationFixture relocations;
    private IsolatedRestrictedOwnerScope ownerScope;
    private Long workspaceId;
    private StorageResponse sendAllowed;
    private StorageResponse sendBlocked;
    private StorageResponse receiveAllowed;
    private StorageResponse receiveBlocked;
    private Long resourceId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storages = new StorageFixture(testContext, apiExecutor);
        regions = new StorageRegionFixture(testContext, apiExecutor);
        relocations = new RelocationFixture(testContext, apiExecutor);
        ResourceFixture resources = new ResourceFixture(testContext, apiExecutor);
        relocations.fetchSharedUnit(3);
        relocations.fetchSharedResourceCategory();
        resourceId = resources.createUniqueResource("rel-select-resource-").getId();

        ownerScope = new IsolatedRestrictedOwnerScope(
                storages, new UserFixture(testContext, apiExecutor), apiExecutor,
                getPlaywrightSessionProvider());
        workspaceId = ownerScope.acquire();
        long parentId = storages.resolveParentUnit().getId();
        sendAllowed = storages.createStorage(StorageDataFactory.childStorage(parentId, "rel-select-send-on-").build());
        sendBlocked = storages.createStorage(StorageDataFactory.childStorage(parentId, "rel-select-send-off-")
                .features(Set.of(LocationFeature.EQUIPMENT)).build());
        receiveAllowed = createExternalUnit(parentId, "rel-select-receive-on-", true);
        receiveBlocked = createExternalUnit(parentId, "rel-select-receive-off-", false);
        exposeToOwner(sendAllowed);
        exposeToOwner(sendBlocked);
        exposeToOwner(receiveAllowed);
        exposeToOwner(receiveBlocked);

        var owner = ownerScope.boundOwner(UserRole.OWNER_2);
        injectSessionCookies(getPlaywrightSessionProvider().getSession(
                owner.username(), owner.password()), sessionCookieDomain());
        browserContext.addInitScript("localStorage.setItem('selectedStorageId', '" + workspaceId + "');"
                + "localStorage.setItem('selectedStorageId:" + owner.username() + "', '" + workspaceId + "');");
    }

    @AfterClass(alwaysRun = true)
    public void cleanupLocations() {
        if (regions != null && storages != null) {
            TestArtifactCleanup.cleanupRegionsAndStorages(regions, storages);
        }
        if (ownerScope != null) {
            ownerScope.release();
        }
    }

    @Test
    @TestCaseId("TC-UI-REL-LOC-001")
    @Description("Створення видачі: лише локації з RELOCATIONS у списку та пошуку отримувачів.")
    public void createSendFiltersRecipientLocations() {
        new RelocationPage(page).open().clickSend();
        assertFiltered(sendBlocked, sendAllowed);
    }

    @Test
    @TestCaseId("TC-UI-REL-LOC-002")
    @Description("Створення отримання: зовнішнє джерело без RELOCATIONS відсутнє в селекторі.")
    public void createReceiveFiltersSourceLocations() {
        new RelocationPage(page).open().clickReceive();
        assertFiltered(receiveBlocked, receiveAllowed);
    }

    @Test
    @TestCaseId("TC-UI-REL-LOC-003")
    @Description("Редагування видачі: отримувач без RELOCATIONS відсутній у списку та пошуку.")
    public void editSendFiltersRecipientLocations() {
        relocations.ensureStock(workspaceId, resourceId, 2.0);
        String marker = "rel-select-edit-send-" + System.nanoTime();
        RelocationResponse sent = relocations.createSendWithDescription(
                UserRole.ADMIN, workspaceId, sendAllowed.getId(), resourceId, 1.0, marker);
        assertThat(sent.getId()).isNotNull();

        RelocationPage journal = new RelocationPage(page).open().openInTransitTab();
        journal.selectPageSize(500);
        journal.clickEditSendInRow(marker);
        assertFiltered(sendBlocked, sendAllowed);
    }

    @Test
    @TestCaseId("TC-UI-REL-LOC-004")
    @Description("Редагування отримання: джерело без RELOCATIONS відсутнє у списку та пошуку.")
    public void editReceiveFiltersSourceLocations() {
        String marker = "rel-select-edit-receive-" + System.nanoTime();
        Response response = apiExecutor.executeRelocationReceive(
                RelocationDataFactory.buildReceiveRequest(
                        receiveAllowed.getId(), workspaceId, resourceId, 1.0,
                        "REL-SELECT-" + System.nanoTime()).toBuilder().description(marker).build(),
                UserRole.ADMIN);
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);

        RelocationPage journal = new RelocationPage(page).open().openReceivedTab();
        journal.selectPageSize(500);
        journal.clickEditInRow(marker);
        assertFiltered(receiveBlocked, receiveAllowed);
    }

    private StorageResponse createExternalUnit(long parentId, String prefix, boolean relocationsEnabled) {
        return storages.createStorage(StorageDataFactory.childStorage(
                        parentId, prefix, UnitType.UNIT, StorageRelation.EXTERNAL)
                .features(relocationsEnabled
                        ? Set.of(LocationFeature.RELOCATIONS, LocationFeature.EQUIPMENT)
                        : Set.of(LocationFeature.EQUIPMENT))
                .build());
    }

    private void exposeToOwner(StorageResponse candidate) {
        var region = regions.createRegion(candidate, StorageAccessMode.FULL_ACCESS,
                "rel-select-visible-");
        regions.addRegionLocations(region.getId(), candidate.getId());
        regions.addRegionMembers(region.getId(), workspaceId);
    }

    private void assertFiltered(StorageResponse blocked, StorageResponse allowed) {
        Locator input = page.locator(LOCATION_INPUT);
        assertThat(input).hasCount(1);
        input.click();
        Locator blockedOption = option(blocked.getName());
        Locator allowedOption = option(allowed.getName());
        allowedOption.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        assertThat(blockedOption).hasCount(0);

        input.fill(blocked.getName());
        assertThat(page.getByText("Не знайдено")).isVisible();
        assertThat(blockedOption).hasCount(0);
        input.fill(partialName(blocked.getName()));
        assertThat(page.getByText("Не знайдено")).isVisible();
        assertThat(blockedOption).hasCount(0);

        input.fill(allowed.getName());
        allowedOption.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        allowedOption.click();
        assertThat(input).hasValue(allowed.getName());
    }

    private Locator option(String name) {
        return page.locator(OPTION).filter(new Locator.FilterOptions().setHasText(name));
    }

    private static String partialName(String name) {
        int suffix = name.indexOf(StorageDataFactory.UNIQUE_NAME_INFIX + "_");
        return suffix > 0 ? name.substring(0, suffix) : name.substring(0, Math.min(12, name.length()));
    }
}
