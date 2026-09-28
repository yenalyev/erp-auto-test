package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.LocationFeature;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.IsolatedRestrictedOwnerScope;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.StorageRegionResponse;
import com.erp.models.response.StorageResponse;
import com.erp.pages.RelocationPage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Set;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

@Epic("Relocation")
@Feature("Region alias requires recipient RELOCATIONS")
public class RelocationRegionAliasFeatureUiTest extends BaseUITest {
    private static final String LOCATION_INPUT = "input[placeholder='Оберіть склад...']";
    private static final String OPTION = "[data-slot='combobox-item']";

    private StorageFixture storages;
    private StorageRegionFixture regions;
    private RelocationFixture relocations;
    private IsolatedRestrictedOwnerScope ownerScope;
    private Long ownerStorageId;
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
        resourceId = resources.createUniqueResource("rel-alias-feature-resource-").getId();

        ownerScope = new IsolatedRestrictedOwnerScope(
                storages, new UserFixture(testContext, apiExecutor), apiExecutor,
                getPlaywrightSessionProvider());
        ownerStorageId = ownerScope.acquire();
        var owner = ownerScope.boundOwner(UserRole.OWNER_2);
        injectSessionCookies(getPlaywrightSessionProvider().getSession(
                owner.username(), owner.password()), sessionCookieDomain());
        browserContext.addInitScript("localStorage.setItem('selectedStorageId', '" + ownerStorageId + "');");
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupRegionsAndLocations() {
        if (regions != null && storages != null) {
            TestArtifactCleanup.cleanupRegionsAndStorages(regions, storages);
        }
    }

    @AfterClass(alwaysRun = true)
    public void releaseOwner() {
        if (ownerScope != null) {
            ownerScope.release();
        }
    }

    @Test
    @TestCaseId("TC-UI-REL-LOC-005")
    @Description("Аліас REGIONS з recipientStorage без RELOCATIONS прихований у створенні видачі.")
    public void createSendHidesAliasWithoutRelocations() {
        AliasPair pair = createAliasPair();
        new RelocationPage(page).open().clickSend();
        assertFilteredAlias(pair);
    }

    @Test
    @TestCaseId("TC-UI-REL-LOC-006")
    @Description("Аліас REGIONS з recipientStorage без RELOCATIONS прихований у редагуванні видачі.")
    public void editSendHidesAliasWithoutRelocations() {
        AliasPair pair = createAliasPair();
        relocations.ensureStock(ownerStorageId, resourceId, 2.0);
        String marker = "rel-alias-edit-" + System.nanoTime();
        RelocationResponse sent = relocations.createSendWithDescription(
                UserRole.ADMIN, ownerStorageId, pair.allowedAnchor().getId(), resourceId, 1.0, marker);
        assertThat(sent.getId()).isNotNull();

        RelocationPage journal = new RelocationPage(page).open().openInTransitTab();
        journal.selectPageSize(500);
        journal.clickEditSendInRow(marker);
        assertFilteredAlias(pair);
    }

    private AliasPair createAliasPair() {
        long parentId = storages.resolveParentUnit().getId();
        StorageResponse blockedAnchor = storages.createStorage(
                StorageDataFactory.childStorage(parentId, "rel-alias-feature-off-")
                        .features(Set.of(LocationFeature.EQUIPMENT)).build());
        StorageResponse allowedAnchor = storages.createStorage(
                StorageDataFactory.childStorage(parentId, "rel-alias-feature-on-")
                        .features(Set.of(LocationFeature.RELOCATIONS, LocationFeature.EQUIPMENT)).build());

        StorageRegionResponse blockedAlias = regions.createRegion(
                blockedAnchor, StorageAccessMode.REGIONS, "rel-alias-feature-off-region-");
        StorageRegionResponse allowedAlias = regions.createRegion(
                allowedAnchor, StorageAccessMode.REGIONS, "rel-alias-feature-on-region-");
        regions.addRegionLocations(blockedAlias.getId(), blockedAnchor.getId());
        regions.addRegionLocations(allowedAlias.getId(), allowedAnchor.getId());
        regions.addRegionMembers(blockedAlias.getId(), ownerStorageId);
        regions.addRegionMembers(allowedAlias.getId(), ownerStorageId);
        return new AliasPair(blockedAlias, allowedAlias, allowedAnchor);
    }

    private void assertFilteredAlias(AliasPair pair) {
        Locator input = page.locator(LOCATION_INPUT);
        assertThat(input).hasCount(1);
        input.click();
        Locator blocked = option(pair.blockedAlias().getName());
        Locator allowed = option(pair.allowedAlias().getName());
        allowed.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        assertThat(blocked).hasCount(0);

        input.fill(pair.blockedAlias().getName());
        assertThat(page.getByText("Не знайдено")).isVisible();
        assertThat(blocked).hasCount(0);
        input.fill(partialName(pair.blockedAlias().getName()));
        assertThat(page.getByText("Не знайдено")).isVisible();
        assertThat(blocked).hasCount(0);

        input.fill(pair.allowedAlias().getName());
        allowed.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        allowed.click();
        assertThat(input).hasValue(pair.allowedAlias().getName());
    }

    private Locator option(String name) {
        return page.locator(OPTION).filter(new Locator.FilterOptions().setHasText(name));
    }

    private static String partialName(String name) {
        int suffix = name.indexOf(StorageDataFactory.UNIQUE_NAME_INFIX + "_");
        return suffix > 0 ? name.substring(0, suffix) : name.substring(0, Math.min(12, name.length()));
    }

    private record AliasPair(StorageRegionResponse blockedAlias,
                             StorageRegionResponse allowedAlias,
                             StorageResponse allowedAnchor) {
    }
}
