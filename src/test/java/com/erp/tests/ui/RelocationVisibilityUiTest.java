package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.models.request.StorageRequest;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageRegionLocationResponse;
import com.erp.models.response.StorageRegionResponse;
import com.erp.models.response.StorageResponse;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.utils.helpers.RelocationStockAssertions;
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

    private static final Object OWNER2_ACCESS_LOCK = new Object();
    private static final String SCENARIO_PREFIX = "ui-rel-vis-neg-";
    private static final String ALIAS_SEND_PREFIX = "ui-rel-vis-alias-";
    private static final String ALIAS_SEND_MARKER = "TC-UI-REL-VIS-003";
    private static final double SEND_AMOUNT = 5.0;

    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;

    private Long owner2StorageId;
    private Long resourceId;
    private String originalOwner2AccessMode;
    private boolean owner2AccessModeChanged;

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
        owner2StorageId = ConfigProvider.getOwner2StorageId();
        ensureOwner2RestrictedAccess();
        regionFixture.purgeViewerVisibilityScope(UserRole.ADMIN, owner2StorageId, storageFixture);
    }

    @AfterClass(alwaysRun = true)
    public void restoreOwner2AccessMode() {
        restoreOwner2AccessIfChanged();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupVisibilityScenario() {
        if ("staging".equals(System.getProperty("env", "debug"))) {
            regionFixture.clearTrackedRegions();
            storageFixture.clearTrackedStorages();
            return;
        }
        regionFixture.deleteTrackedRegions(UserRole.ADMIN);
        storageFixture.deactivateTrackedStorages(UserRole.ADMIN);
    }

    @Test
    @TestCaseId("TC-UI-REL-VIS-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description(StorageRegionsAllureDescriptions.TC_UI_REL_VIS_001)
    public void sendFormExcludesOutsiderOutsideVisibilityRegions() {
        InScopeOutsiderScenario scenario = prepareInScopeWithOutsiderScenario();
        injectOwner2Session(owner2StorageId);

        List<StorageResponse> apiNames = storageFixture.getNames(UserRole.OWNER_2, true, null);
        Set<String> expectedNames = expectedSendFormRecipientNames(apiNames, owner2StorageId);

        assertThat(expectedNames)
                .contains(scenario.inScopeRecipient().getName())
                .doesNotContain(scenario.outsider().getName());

        RelocationCreateOutputPage sendForm = Allure.step(
                "UI: журнал → Видати",
                () -> new RelocationPage(page).open().clickSend());
        sendForm.attachScreenshot("TC-UI-REL-VIS-001 — send form");

        List<String> visibleOptions = Allure.step(
                "UI: опції dropdown",
                () -> sendForm.openRecipientDropdown().collectRecipientOptionLabels());
        sendForm.attachScreenshot("TC-UI-REL-VIS-001 — dropdown open");

        Allure.step("Assert: outsider відсутній, in-scope присутній", () -> {
            assertThat(visibleOptions)
                    .as("UI не показує outsider поза областями")
                    .doesNotContain(scenario.outsider().getName());
            assertThat(visibleOptions)
                    .as("UI показує in-scope отримувача")
                    .contains(scenario.inScopeRecipient().getName());
            assertThat(visibleOptions)
                    .allMatch(expectedNames::contains);
        });
    }

    @Test
    @TestCaseId("TC-UI-REL-VIS-002")
    @Severity(SeverityLevel.CRITICAL)
    @Description(StorageRegionsAllureDescriptions.TC_UI_REL_VIS_002)
    public void sendFormShowsRegionAliasInRegionsMode() {
        RegionsAliasScenario scenario = prepareRegionsAliasScenario();
        injectOwner2Session(owner2StorageId);

        List<StorageResponse> apiNames = storageFixture.getNames(UserRole.OWNER_2, true, null);
        assertThat(apiNames.stream().map(StorageResponse::getName))
                .contains(scenario.region().getName())
                .doesNotContain(scenario.recipientAnchor().getName());

        Set<String> expectedNames = expectedSendFormRecipientNames(apiNames, owner2StorageId);
        assertThat(expectedNames)
                .contains(scenario.region().getName())
                .doesNotContain(scenario.recipientAnchor().getName());

        RelocationCreateOutputPage sendForm = new RelocationPage(page).open().clickSend();
        sendForm.attachScreenshot("TC-UI-REL-VIS-002 — send form");

        List<String> visibleOptions = sendForm.openRecipientDropdown().collectRecipientOptionLabels();
        sendForm.attachScreenshot("TC-UI-REL-VIS-002 — dropdown open");

        assertThat(visibleOptions)
                .as("REGIONS: у dropdown ім'я області")
                .contains(scenario.region().getName());
        assertThat(visibleOptions)
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

        RelocationCreateOutputPage sendForm = new RelocationPage(page).open().clickSend();
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
        List<StorageResponse> names = storageFixture.getNames(viewer, true, null);
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

    private void injectOwner2Session(long selectedStorageId) {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.OWNER_2.getUsername(), UserRole.OWNER_2.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
    }

    private void ensureOwner2RestrictedAccess() {
        synchronized (OWNER2_ACCESS_LOCK) {
            StorageResponse owner2Storage = storageFixture.getById(UserRole.ADMIN, owner2StorageId);
            originalOwner2AccessMode = owner2Storage.getAccessMode();
            if (!StorageAccessMode.REGIONS.name().equals(originalOwner2AccessMode)) {
                StorageRequest update = StorageDataFactory.withAccessMode(
                        owner2Storage, StorageAccessMode.REGIONS);
                storageFixture.update(UserRole.ADMIN, owner2StorageId, update);
                owner2AccessModeChanged = true;
            }
        }
    }

    private void restoreOwner2AccessIfChanged() {
        if (!owner2AccessModeChanged || originalOwner2AccessMode == null) {
            return;
        }
        synchronized (OWNER2_ACCESS_LOCK) {
            try {
                StorageResponse current = storageFixture.getById(UserRole.ADMIN, owner2StorageId);
                StorageRequest restore = StorageDataFactory.withAccessMode(
                        current, StorageAccessMode.valueOf(originalOwner2AccessMode));
                storageFixture.update(UserRole.ADMIN, owner2StorageId, restore);
            } catch (Exception e) {
                log.warn("Failed to restore OWNER_2 storage accessMode: {}", e.getMessage());
            }
        }
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
