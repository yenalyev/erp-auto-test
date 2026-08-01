package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageRegionResponse;
import com.erp.models.response.StorageResponse;
import com.erp.pages.InventoryEditPage;
import com.erp.utils.config.ConfigProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.LoadState;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.erp.fixtures.StorageRegionFixture.SYSTEM_ALL_RESOURCES_REGION_NAME;
import static org.assertj.core.api.Assertions.assertThat;

@Epic("Master Data")
@Feature("Storage regions")
@Story("System ALL RESOURCES region")
public class SystemAllResourcesRegionUiTest extends BaseUITest {

    private static final String SCENARIO_PREFIX = "ui-sys-all-";
    private static final ObjectMapper JSON = new ObjectMapper();

    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;
    private ResourceFixture resourceFixture;
    private InventoryFixture inventoryFixture;
    private final Set<Long> systemRegionMembers = new LinkedHashSet<>();
    private Long inventoryStorageId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        regionFixture = new StorageRegionFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);

        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupScenario() {
        if (inventoryStorageId != null) {
            try {
                inventoryFixture.ensureClosed(inventoryStorageId);
            } finally {
                inventoryStorageId = null;
            }
        }
        for (Long storageId : Set.copyOf(systemRegionMembers)) {
            try {
                regionFixture.detachMemberFromSystemAllResourcesRegion(storageId);
            } finally {
                systemRegionMembers.remove(storageId);
            }
        }
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupClassArtifacts() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @Test
    @TestCaseId("TC-UI-STR-RES-019")
    @Severity(SeverityLevel.NORMAL)
    @Description("Системний регіон видно у списку; картка показує режим, вкладки та sentinel «Всі ресурси».")
    public void systemRegionIsVisibleInListAndDetails() {
        StorageRegionResponse systemRegion = regionFixture.findSystemAllResourcesRegion();
        injectRoleSession(UserRole.ADMIN, null);

        page.navigate(ConfigProvider.getBaseUrl() + "/storage-regions?name="
                + java.net.URLEncoder.encode(SYSTEM_ALL_RESOURCES_REGION_NAME,
                java.nio.charset.StandardCharsets.UTF_8));
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator regionLink = page.getByText(SYSTEM_ALL_RESOURCES_REGION_NAME, new com.microsoft.playwright.Page.GetByTextOptions()
                .setExact(true));
        regionLink.waitFor();
        assertThat(regionLink.count()).isGreaterThanOrEqualTo(1);
        assertThat(page.getByText("📦 Ресурси", new com.microsoft.playwright.Page.GetByTextOptions()
                .setExact(true)).count()).isGreaterThanOrEqualTo(1);

        regionLink.first().click();
        page.waitForURL("**/storage-regions/" + systemRegion.getId());
        page.waitForLoadState(LoadState.NETWORKIDLE);

        assertThat(page.locator("h1").textContent()).isEqualTo(SYSTEM_ALL_RESOURCES_REGION_NAME);
        assertThat(page.getByText("Учасники", new com.microsoft.playwright.Page.GetByTextOptions()
                .setExact(false)).count()).isGreaterThanOrEqualTo(1);
        assertThat(page.getByText("Ресурси", new com.microsoft.playwright.Page.GetByTextOptions()
                .setExact(false)).count()).isGreaterThanOrEqualTo(1);
        Locator sentinel = page.getByText("Всі ресурси", new com.microsoft.playwright.Page.GetByTextOptions()
                .setExact(true));
        sentinel.waitFor();
        assertThat(sentinel.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @TestCaseId("TC-UI-STR-RES-024")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            UI-аналог TC-STR-RES-024: inventory «Оберіть ресурс» викликає /resources/autocomplete.
            Unit — member системного регіону + кастомної RESOURCES з granted.
            Перевірка по тілу autocomplete (DOM дедупить через React key=id):
            granted і outside присутні; frequency(grantedId) == 1.
            Відомий дефект: JOIN після expand resource_id=0 може дублювати concrete grant.
            """)
    public void inventoryAutocompleteHasNoDuplicatesForSystemAndCustomUnion() {
        String runPrefix = SCENARIO_PREFIX + "u" + System.currentTimeMillis() + "-";
        ResourceResponse granted = resourceFixture.createUniqueResource(runPrefix + "a-");
        ResourceResponse outside = resourceFixture.createUniqueResource(runPrefix + "b-");

        StorageResponse parent = storageFixture.resolveParentUnit();
        StorageResponse unit = storageFixture.createStorage(
                StorageDataFactory.restrictedUnitStorage(parent.getId(), SCENARIO_PREFIX + "union-").build());

        StorageRegionResponse customRegion =
                regionFixture.createRegion(unit, StorageAccessMode.RESOURCES, SCENARIO_PREFIX + "custom-");
        regionFixture.addRegionMembers(customRegion.getId(), unit.getId());
        regionFixture.addRegionResources(customRegion.getId(), granted.getId());
        regionFixture.attachMemberToSystemAllResourcesRegion(unit.getId());
        systemRegionMembers.add(unit.getId());

        inventoryStorageId = unit.getId();
        inventoryFixture.ensureClosed(unit.getId());
        inventoryFixture.openSession(unit.getId());

        injectRoleSession(UserRole.ADMIN, unit.getId());
        InventoryEditPage edit = new InventoryEditPage(page).open(unit.getId());

        Allure.step("UI: /resources/autocomplete body — без дублів granted, outside присутній", () -> {
            // Open first (may fire empty autocomplete); assert on the response for our unique prefix.
            page.locator("button[role='combobox']")
                    .filter(new Locator.FilterOptions().setHasText("Оберіть ресурс"))
                    .click();
            Locator search = page.getByPlaceholder("Пошук...").last();

            Response autocompleteResponse = page.waitForResponse(
                    response -> {
                        if (!response.url().contains("/resources/autocomplete")
                                || !"GET".equals(response.request().method())
                                || response.status() != 200) {
                            return false;
                        }
                        String decoded = java.net.URLDecoder.decode(
                                response.url(), java.nio.charset.StandardCharsets.UTF_8);
                        return decoded.contains(runPrefix);
                    },
                    () -> {
                        search.click();
                        search.fill("");
                        search.fill(runPrefix);
                    });

            List<Map<String, Object>> payload;
            try {
                payload = JSON.readValue(
                        autocompleteResponse.text(),
                        new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse autocomplete JSON: "
                        + autocompleteResponse.text(), e);
            }
            List<Long> ids = payload.stream()
                    .map(row -> ((Number) row.get("id")).longValue())
                    .toList();

            long grantedHits = ids.stream().filter(id -> id.equals(granted.getId())).count();
            long outsideHits = ids.stream().filter(id -> id.equals(outside.getId())).count();

            try {
                edit.closeAddResourceAutocomplete();
            } catch (Exception ignored) {
                // popover may already be closed
            }
            edit.attachScreenshot("TC-UI-STR-RES-024 - autocomplete union");

            assertThat(outsideHits)
                    .as("outside має бути у autocomplete через wildcard системного регіону; body=%s", ids)
                    .isGreaterThanOrEqualTo(1);
            assertThat(grantedHits)
                    .as("granted має з'являтися рівно 1 раз у autocomplete body (без дубля wildcard+grant); body=%s",
                            ids)
                    .isEqualTo(1);
        });
    }

    private void injectRoleSession(UserRole role, Long selectedStorageId) {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(role.getUsername(), role.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        if (selectedStorageId != null) {
            browserContext.addInitScript(
                    "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
        }
    }
}
