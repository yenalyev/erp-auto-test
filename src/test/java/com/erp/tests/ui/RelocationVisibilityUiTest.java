package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.relocation.RelocationStockSeeder;
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
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageRegionLocationResponse;
import com.erp.models.response.StorageRegionResponse;
import com.erp.models.response.StorageResponse;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.utils.helpers.RelocationStockAssertions;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.RelocationCreateOutputPage;
import com.erp.pages.RelocationPage;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.storage.StorageRegionsAllureDescriptions;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI: dropdown «Кому відправляю» для REGIONS-member — in-scope та outsider.
 */
@Slf4j
@Epic("Relocation")
@Feature("Relocation UI")
@Story("Relocation visibility regions")
public class RelocationVisibilityUiTest extends BaseUITest {

    private static final String SCENARIO_PREFIX = "ui-rel-vis-neg-";
    private static final String ALIAS_SEND_PREFIX = "ui-rel-vis-alias-";
    private static final String ALIAS_SEND_MARKER = "TC-UI-REL-VIS-003";
    private static final double SEND_AMOUNT = 5.0;

    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;
    private IsolatedRestrictedOwnerScope isolatedOwnerScope;

    private Long owner2StorageId;
    private String owner2StorageName;
    private Long resourceId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        regionFixture = new StorageRegionFixture(testContext, apiExecutor);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
        resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);
        isolatedOwnerScope = new IsolatedRestrictedOwnerScope(
                storageFixture,
                new UserFixture(testContext, apiExecutor),
                apiExecutor,
                getPlaywrightSessionProvider());
        owner2StorageId = isolatedOwnerScope.acquire();
        owner2StorageName = storageFixture.getById(UserRole.ADMIN, owner2StorageId).getName();
        regionFixture.purgeViewerVisibilityScope(UserRole.ADMIN, owner2StorageId, storageFixture);
        regionFixture.purgeRegionsByNamePrefixes(
                UserRole.ADMIN, SCENARIO_PREFIX, ALIAS_SEND_PREFIX, "ui-rel-vis-", "vis-", "rel-vis-");
    }

    @AfterClass(alwaysRun = true)
    public void releaseIsolatedOwner() {
        if (isolatedOwnerScope != null) {
            isolatedOwnerScope.release();
        }
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupVisibilityScenario() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @Test
    @TestCaseId("TC-UI-REL-VIS-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description(StorageRegionsAllureDescriptions.TC_UI_REL_VIS_001)
    public void sendFormExcludesOutsiderOutsideVisibilityRegions() {
        InScopeOutsiderScenario scenario = prepareInScopeWithOutsiderScenario();

        List<StorageResponse> apiNames = storageFixture.getNamesInStorageContext(
                UserRole.OWNER_2, owner2StorageId, true);
        Set<String> expectedNames = expectedSendFormRecipientNames(apiNames, owner2StorageId);

        assertThat(expectedNames)
                .contains(scenario.inScopeRecipient().getName())
                .doesNotContain(scenario.outsider().getName());

        RelocationCreateOutputPage sendForm = Allure.step(
                "UI: журнал → workspace OWNER_2 → Видати",
                this::openSendFormAsOwner2);
        sendForm.attachScreenshot("TC-UI-REL-VIS-001 — send form");

        Allure.step("Assert: outsider відсутній, in-scope присутній (пошук у dropdown)", () -> {
            List<String> inScopeOptions = sendForm.searchAndCollectRecipientOptions(
                    scenario.inScopeRecipient().getName());
            assertThat(inScopeOptions)
                    .as("UI показує in-scope отримувача")
                    .contains(scenario.inScopeRecipient().getName());
            assertThat(inScopeOptions)
                    .allMatch(expectedNames::contains);

            List<String> outsiderOptions = sendForm.searchAndCollectRecipientOptions(
                    scenario.outsider().getName());
            assertThat(outsiderOptions)
                    .as("UI не показує outsider поза областями")
                    .doesNotContain(scenario.outsider().getName());
        });
    }

    @Test
    @TestCaseId("TC-WMS-REG-RES-016")
    @Severity(SeverityLevel.CRITICAL)
    @Description(StorageRegionsAllureDescriptions.TC_WMS_REG_RES_016)
    public void sendFormProductListShowsOnlyInScopeResources() {
        ResourceResponse granted = resourceFixture.createUniqueResource("ui-wms-res-in-");
        ResourceResponse hidden = resourceFixture.createUniqueResource("ui-wms-res-hid-");

        StorageResponse owner2 = storageFixture.getById(UserRole.ADMIN, owner2StorageId);
        StorageRegionResponse region = regionFixture.createRegion(
                owner2, StorageAccessMode.RESOURCES, "ui-wms-res-reg-");
        regionFixture.addRegionMembers(region.getId(), owner2StorageId);
        regionFixture.addRegionResources(region.getId(), granted.getId());

        // Only granted gets stock — receive auto-grants resources into visibility scope.
        relocationFixture.ensureStock(owner2StorageId, granted.getId(), 25.0);

        injectOwner2Session(owner2StorageId);
        RelocationCreateOutputPage sendForm = Allure.step(
                "UI: журнал → Видати (OWNER_2 REGIONS)",
                () -> new RelocationPage(page).open().clickSend());
        sendForm.attachScreenshot("TC-WMS-REG-RES-016 — send form");

        String grantedTerm = searchTerm(granted.getName());
        List<String> grantedOptions = sendForm.searchAndCollectResourceOptions(grantedTerm);
        sendForm.attachScreenshot("TC-WMS-REG-RES-016 — granted search");
        assertThat(grantedOptions)
                .as("in-scope ресурс у «Список продукції»")
                .anyMatch(label -> containsIgnoreCase(label, granted.getName())
                        || containsIgnoreCase(label, grantedTerm));

        String hiddenTerm = searchTerm(hidden.getName());
        List<String> hiddenOptions = sendForm.searchAndCollectResourceOptions(hiddenTerm);
        sendForm.attachScreenshot("TC-WMS-REG-RES-016 — hidden search");
        assertThat(hiddenOptions)
                .as("hidden ресурс відсутній у autocomplete")
                .noneMatch(label -> containsIgnoreCase(label, hidden.getName()));
    }

    @Test
    @TestCaseId("TC-UI-REL-VIS-002")
    @Severity(SeverityLevel.CRITICAL)
    @Description(StorageRegionsAllureDescriptions.TC_UI_REL_VIS_002)
    public void sendFormShowsRegionAliasInRegionsMode() {
        RegionsAliasScenario scenario = prepareRegionsAliasScenario();

        List<StorageResponse> apiNames = storageFixture.getNamesInStorageContext(
                UserRole.OWNER_2, owner2StorageId, true);
        assertThat(apiNames.stream().map(StorageResponse::getName))
                .contains(scenario.region().getName())
                .doesNotContain(scenario.recipientAnchor().getName());

        Set<String> expectedNames = expectedSendFormRecipientNames(apiNames, owner2StorageId);
        assertThat(expectedNames)
                .contains(scenario.region().getName())
                .doesNotContain(scenario.recipientAnchor().getName());

        RelocationCreateOutputPage sendForm = openSendFormAsOwner2();
        sendForm.attachScreenshot("TC-UI-REL-VIS-002 — send form");

        List<String> aliasOptions = sendForm.searchAndCollectRecipientOptions(scenario.region().getName());
        sendForm.attachScreenshot("TC-UI-REL-VIS-002 — dropdown search alias");

        assertThat(aliasOptions)
                .as("REGIONS: у dropdown ім'я області")
                .contains(scenario.region().getName());
        assertThat(aliasOptions)
                .as("REGIONS: реальне ім'я anchor не показується")
                .doesNotContain(scenario.recipientAnchor().getName());
    }

    @Test
    @TestCaseId("TC-UI-REL-VIS-003")
    @Severity(SeverityLevel.CRITICAL)
    @Description(StorageRegionsAllureDescriptions.TC_UI_REL_VIS_003)
    public void sendToRegionsAliasDeliversToRecipientStorageAnchor() throws InterruptedException {
        RegionsAliasSendScenario scenario = prepareRegionsAliasSendScenario();
        RelocationStockSeeder.receiveFromSupplier(
                apiExecutor, UserRole.ADMIN, owner2StorageId, Map.of(resourceId, 50.0));
        ResourceResponse resource = resourceFixture.getById(UserRole.ADMIN, resourceId);

        StorageResponse anchorFromApi = Allure.step(
                "API: GET anchor локація id=" + scenario.anchor().getId(),
                () -> storageFixture.getById(UserRole.ADMIN, scenario.anchor().getId()));
        StorageRegionResponse regionFromApi = Allure.step(
                "API: GET область id=" + scenario.region().getId(),
                () -> regionFixture.getById(UserRole.ADMIN, scenario.region().getId()));

        assertThat(regionFromApi.getRecipientStorage())
                .as("API: область має recipientStorage (anchor)")
                .isNotNull();
        assertThat(regionFromApi.getRecipientStorage().getId())
                .as("API: region.recipientStorage.id = anchor.id")
                .isEqualTo(anchorFromApi.getId());

        Set<Long> tracked = Set.of(resourceId);
        Map<Long, ProductionStockAssertions.StockSnapshot> nonAnchorStockBefore = Allure.step(
                "API: snapshot залишків на non-anchor локаціях області перед send",
                () -> captureNonAnchorRegionStockBefore(regionFromApi, anchorFromApi, tracked));
        ProductionStockAssertions.StockSnapshot anchorStockBefore = RelocationStockAssertions.capture(
                apiExecutor, anchorFromApi.getId(), UserRole.ADMIN, tracked, "anchor before send (CREATED)");

        injectOwner2Session(owner2StorageId);

        StorageResponse aliasEntry = findRegionsAliasNameEntry(
                UserRole.OWNER_2, scenario.region(), anchorFromApi);
        assertThat(aliasEntry.getId())
                .as("id аліасу в /names ≠ sender location")
                .isNotEqualTo(scenario.senderInRegion().getId());

        RelocationCreateOutputPage sendForm = openSendFormAsOwner2();
        sendForm.attachScreenshot("TC-UI-REL-VIS-003 — send form");

        sendForm.selectRecipientByLabel(scenario.region().getName());
        assertThat(sendForm.getSelectedRecipientLabel()).isEqualTo(scenario.region().getName());

        sendForm.fillDescription(ALIAS_SEND_MARKER)
                .selectOutputResourceByName(resource.getName())
                .fillOutputQuantity(String.valueOf((int) SEND_AMOUNT))
                .fillInvoiceIssuerDefaults();
        sendForm.attachScreenshot("TC-UI-REL-VIS-003 — before submit");

        sendForm.submitSendExpectSuccess();
        RelocationResponse sent = awaitInTransitByDescription(ALIAS_SEND_MARKER, 45);
        Allure.step("API: recipient переміщення = anchor локація", () ->
                assertSendRecipientMatchesAnchorFromApi(
                        sent, anchorFromApi, regionFromApi, scenario, ALIAS_SEND_MARKER));
        Allure.step("API: stock не на інших локаціях області (лише anchor як recipient)", () -> {
            assertNonAnchorRegionLocationsStockUnchanged(nonAnchorStockBefore, resourceId, anchorFromApi.getId());
            ProductionStockAssertions.StockSnapshot anchorStockAfter = RelocationStockAssertions.capture(
                    apiExecutor, anchorFromApi.getId(), UserRole.ADMIN, tracked, "anchor after send (CREATED)");
            RelocationStockAssertions.assertUnchanged(
                    anchorStockBefore, anchorStockAfter, anchorFromApi.getId(), resourceId,
                    "CREATED: stock ще не зараховано на anchor до resolve");
        });
    }

    private StorageResponse findRegionsAliasNameEntry(
            UserRole viewer, StorageRegionResponse region, StorageResponse anchorFromApi) {
        List<StorageResponse> names = storageFixture.getNamesInStorageContext(viewer, owner2StorageId, true);
        StorageResponse aliasEntry = names.stream()
                .filter(s -> region.getName().equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "REGIONS alias «" + region.getName() + "» не знайдено в /names"));
        assertThat(aliasEntry.getId())
                .as("/names: id аліасу = GET /storages/{anchorId}.id (%d)", anchorFromApi.getId())
                .isEqualTo(anchorFromApi.getId());
        return aliasEntry;
    }

    private void assertSendRecipientMatchesAnchorFromApi(
            RelocationResponse sent,
            StorageResponse anchorFromApi,
            StorageRegionResponse regionFromApi,
            RegionsAliasSendScenario scenario,
            String marker) {
        assertThat(sent.getRecipient()).isNotNull();
        assertThat(sent.getRecipient().getId())
                .as("OWNER_2 journal: sent.recipient.id = anchor.id (GET /storages/%d)", anchorFromApi.getId())
                .isEqualTo(anchorFromApi.getId());
        assertThat(regionFromApi.getRecipientStorage().getId())
                .as("GET /storages/regions/{id}: recipientStorage.id = anchor.id")
                .isEqualTo(anchorFromApi.getId());
        assertThat(sent.getRecipient().getId())
                .as("recipient.id ≠ sender location у region")
                .isNotEqualTo(scenario.senderInRegion().getId());

        RelocationResponse sentAsAdmin = relocationFixture.findInTransitByDescription(
                UserRole.ADMIN, owner2StorageId, marker);
        assertThat(sentAsAdmin)
                .as("ADMIN journal: переміщення з маркером «%s»", marker)
                .isNotNull();
        assertThat(sentAsAdmin.getRecipient().getId())
                .as("ADMIN journal: recipient.id = anchor.id")
                .isEqualTo(anchorFromApi.getId());
        assertThat(sentAsAdmin.getRecipient().getName())
                .as("ADMIN journal: recipient.name = GET /storages/{id}.name")
                .isEqualTo(anchorFromApi.getName());

        log.info(
                "TC-UI-REL-VIS-003: anchor API id={}, name={}; region.recipientStorage.id={}; sent.id={}",
                anchorFromApi.getId(),
                anchorFromApi.getName(),
                regionFromApi.getRecipientStorage().getId(),
                sent.getId());
    }

    private Map<Long, ProductionStockAssertions.StockSnapshot> captureNonAnchorRegionStockBefore(
            StorageRegionResponse regionFromApi,
            StorageResponse anchorFromApi,
            Set<Long> resourceIds) {
        List<StorageRegionLocationResponse> locations = regionFixture.getRegionLocations(
                UserRole.ADMIN, regionFromApi.getId());
        Map<Long, ProductionStockAssertions.StockSnapshot> snapshots = new LinkedHashMap<>();
        for (StorageRegionLocationResponse location : locations) {
            if (anchorFromApi.getId().equals(location.getStorageId())) {
                continue;
            }
            snapshots.put(location.getStorageId(), RelocationStockAssertions.capture(
                    apiExecutor, location.getStorageId(), UserRole.ADMIN, resourceIds,
                    "non-anchor location id=" + location.getStorageId() + " before send"));
        }
        assertThat(snapshots)
                .as("область має non-anchor локації для перевірки, що stock туди не потрапив")
                .isNotEmpty();
        return snapshots;
    }

    private void assertNonAnchorRegionLocationsStockUnchanged(
            Map<Long, ProductionStockAssertions.StockSnapshot> beforeByLocation,
            Long resourceId,
            Long anchorId) {
        for (Map.Entry<Long, ProductionStockAssertions.StockSnapshot> entry : beforeByLocation.entrySet()) {
            Long storageId = entry.getKey();
            assertThat(storageId).as("non-anchor location ≠ anchor").isNotEqualTo(anchorId);
            ProductionStockAssertions.StockSnapshot after = RelocationStockAssertions.capture(
                    apiExecutor, storageId, UserRole.ADMIN, Set.of(resourceId),
                    "non-anchor location id=" + storageId + " after send");
            RelocationStockAssertions.assertUnchanged(
                    entry.getValue(), after, storageId, resourceId,
                    "видача на REGIONS alias → recipient anchor, не location " + storageId);
        }
    }

    private RelocationResponse awaitInTransitByDescription(String marker, int timeoutSeconds)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        while (System.currentTimeMillis() < deadline) {
            RelocationResponse found = relocationFixture.findInTransitByDescription(
                    UserRole.OWNER_2, owner2StorageId, marker);
            if (found != null) {
                return found;
            }
            Thread.sleep(1_000);
        }
        throw new AssertionError("Переміщення з маркером «" + marker + "» не з'явилось у журналі за " + timeoutSeconds + "s");
    }

    private RegionsAliasSendScenario prepareRegionsAliasSendScenario() {
        StorageResponse anchor = storageFixture.createUniqueStorage(ALIAS_SEND_PREFIX + "anchor-");
        StorageResponse senderInRegion = storageFixture.createUniqueStorage(ALIAS_SEND_PREFIX + "from-");

        StorageRegionResponse region = regionFixture.createRegion(
                anchor, StorageAccessMode.REGIONS, ALIAS_SEND_PREFIX + "reg-");
        regionFixture.addRegionLocations(region.getId(), senderInRegion.getId());
        regionFixture.addRegionMembers(region.getId(), owner2StorageId);

        return new RegionsAliasSendScenario(region, anchor, senderInRegion);
    }

    private InScopeOutsiderScenario prepareInScopeWithOutsiderScenario() {
        StorageResponse anchor = storageFixture.createUniqueStorage(SCENARIO_PREFIX + "anchor-");
        StorageResponse inScope = storageFixture.createUniqueStorage(SCENARIO_PREFIX + "in-");
        StorageResponse outsider = storageFixture.createUniqueStorage(SCENARIO_PREFIX + "out-");

        StorageRegionResponse region = regionFixture.createRegion(
                anchor, StorageAccessMode.FULL_ACCESS, SCENARIO_PREFIX + "reg-");
        regionFixture.addRegionLocations(region.getId(), inScope.getId());
        regionFixture.addRegionMembers(region.getId(), owner2StorageId);

        return new InScopeOutsiderScenario(inScope, outsider);
    }

    private RegionsAliasScenario prepareRegionsAliasScenario() {
        StorageResponse recipientAnchor = storageFixture.createUniqueStorage(SCENARIO_PREFIX + "rec-");
        StorageResponse sharedLocation = storageFixture.createUniqueStorage(SCENARIO_PREFIX + "loc-");

        StorageRegionResponse region = regionFixture.createRegion(
                recipientAnchor, StorageAccessMode.REGIONS, SCENARIO_PREFIX + "alias-reg-");
        regionFixture.addRegionLocations(region.getId(), recipientAnchor.getId(), sharedLocation.getId());
        regionFixture.addRegionMembers(region.getId(), owner2StorageId);

        return new RegionsAliasScenario(region, recipientAnchor);
    }

    private static Set<String> expectedSendFormRecipientNames(
            List<StorageResponse> apiNames, long senderStorageId) {
        Map<Long, String> uniqueById = new LinkedHashMap<>();
        for (StorageResponse storage : apiNames) {
            if (storage.getId() == null || storage.getId().equals(senderStorageId)) {
                continue;
            }
            if ("SUPPLIER".equals(storage.getType())) {
                continue;
            }
            uniqueById.putIfAbsent(storage.getId(), storage.getName());
        }
        return new HashSet<>(uniqueById.values());
    }

    private RelocationCreateOutputPage openSendFormAsOwner2() {
        injectOwner2Session(owner2StorageId);
        RelocationPage journal = new RelocationPage(page).open();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        // Isolated owner often has a single UNIT — workspace combobox is hidden.
        if (sidebar.isWorkspaceSelectorVisible()) {
            sidebar.selectWorkspaceByName(owner2StorageName);
        }
        return journal.clickSend();
    }

    private void injectOwner2Session(long selectedStorageId) {
        UserFixture.RestrictedOwnerUser owner = isolatedOwnerScope.boundOwner(UserRole.OWNER_2);
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(owner.username(), owner.password());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
    }

    private static String searchTerm(String name) {
        if (name == null) {
            return "";
        }
        return name.length() > 12 ? name.substring(0, 12) : name;
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null) {
            return false;
        }
        return haystack.toLowerCase().contains(needle.toLowerCase());
    }

    private record InScopeOutsiderScenario(StorageResponse inScopeRecipient, StorageResponse outsider) {
    }

    private record RegionsAliasScenario(StorageRegionResponse region, StorageResponse recipientAnchor) {
    }

    private record RegionsAliasSendScenario(
            StorageRegionResponse region,
            StorageResponse anchor,
            StorageResponse senderInRegion) {
    }
}
