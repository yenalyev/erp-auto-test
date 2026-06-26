package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.models.request.StorageRequest;
import com.erp.models.response.StorageRegionResponse;
import com.erp.models.response.StorageResponse;
import com.erp.pages.RelocationCreateOutputPage;
import com.erp.pages.RelocationPage;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI-перевірка dropdown «Кому відправляю»: список отримувачів має відповідати
 * {@code GET /storages/names?isActive=true} з урахуванням областей видимості поточного підрозділу.
 * <p>Дзеркало API-сценарію {@code TC-STR-REG-034}: member у кількох областях з однією спільною
 * локацією — локація з'являється в селекторі рівно один раз.
 */
@Slf4j
@Epic("Relocation")
@Feature("Relocation UI")
@Story("Send form recipient selector")
public class RelocationSendRecipientUiTest extends BaseUITest {

    private static final Object OWNER2_ACCESS_LOCK = new Object();
    private static final String SCENARIO_PREFIX = "ui-rel-vis-";

    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;

    private Long owner2StorageId;
    private String originalOwner2AccessMode;
    private boolean owner2AccessModeChanged;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        regionFixture = new StorageRegionFixture(testContext, apiExecutor);
        owner2StorageId = ConfigProvider.getOwner2StorageId();
        ensureOwner2RestrictedAccess();
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
    @TestCaseId("TC-UI-REL-010")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Дзеркало TC-STR-REG-034 для UI форми видачі.
            
            Arrange (API):
            - OWNER_2 (REGIONS) — member у 3 областях FULL_ACCESS;
            - спільна локація в locations усіх трьох областей.
            
            Assert API:
            - GET /storages/names?isActive=true містить спільну локацію рівно 1 раз (без дублікатів id).
            
            Assert UI (як useRelocationCreateOutput):
            - toStorages = names фільтр: type≠SUPPLIER, id≠selectedStorageId;
            - dropdown «Кому відправляю» не дублює label спільної локації;
            - кожна видима опція UI ∈ дозволеному набору з API (області видимості).
            """)
    public void sendFormRecipientListMatchesVisibilityRegions() {
        VisibilityScenario scenario = prepareThreeRegionsSharedLocationScenario();
        injectOwner2Session(owner2StorageId);

        List<StorageResponse> apiNames = Allure.step(
                "API: GET /storages/names?isActive=true для OWNER_2",
                () -> storageFixture.getNames(UserRole.OWNER_2, true, null));

        Allure.step("Assert API: union областей видимості без дублікатів id", () -> {
            List<Long> ids = apiNames.stream().map(StorageResponse::getId).toList();
            assertThat(ids).contains(scenario.sharedLocation().getId());
            assertThat(ids.stream().filter(id -> id.equals(scenario.sharedLocation().getId())).count())
                    .as("Спільна локація з кількох областей — один запис у /names (TC-STR-REG-034)")
                    .isEqualTo(1);
            assertThat(new HashSet<>(ids))
                    .as("Жодного дубліката storage.id у /storages/names?isActive=true")
                    .hasSize(ids.size());
        });

        Set<String> expectedRecipientNames = expectedSendFormRecipientNames(apiNames, owner2StorageId);
        assertThat(expectedRecipientNames)
                .as("Спільна локація видима через області для OWNER_2")
                .contains(scenario.sharedLocation().getName());

        RelocationCreateOutputPage sendForm = Allure.step(
                "UI: журнал → Видати → форма «Видача»",
                () -> new RelocationPage(page).open().clickSend());
        sendForm.attachScreenshot("TC-UI-REL-010 — send form");

        List<String> visibleOptions = Allure.step(
                "UI: опції dropdown «Кому відправляю»",
                () -> sendForm.openRecipientDropdown().collectRecipientOptionLabels());
        sendForm.attachScreenshot("TC-UI-REL-010 — dropdown open");

        Allure.step("Assert UI: видимі опції ⊆ дозволених з API (області видимості)", () -> {
            assertThat(visibleOptions).isNotEmpty();
            assertThat(new HashSet<>(visibleOptions))
                    .as("UI не дублює label серед видимих опцій")
                    .hasSize(visibleOptions.size());
            assertThat(visibleOptions)
                    .as("Кожна опція UI має бути з /storages/names після фільтра форми видачі")
                    .allMatch(expectedRecipientNames::contains);
        });

        Allure.step("Assert UI: спільна локація з 3 областей — рівно 1 раз у пошуку", () -> {
            List<String> sharedMatches = sendForm.searchAndCollectRecipientOptions(
                    scenario.sharedLocation().getName());
            sendForm.attachScreenshot("TC-UI-REL-010 — search shared location");

            assertThat(sharedMatches)
                    .as("Локація «%s» має з'явитись у dropdown рівно один раз",
                            scenario.sharedLocation().getName())
                    .containsExactly(scenario.sharedLocation().getName());
        });

        Allure.step("UI: обрати спільну локацію як отримувача", () -> {
            sendForm.selectRecipientByLabel(scenario.sharedLocation().getName());
            sendForm.attachScreenshot("TC-UI-REL-010 — recipient selected");
            assertThat(sendForm.getSelectedRecipientLabel())
                    .contains(scenario.sharedLocation().getName());
        });
    }

    private VisibilityScenario prepareThreeRegionsSharedLocationScenario() {
        StorageResponse sharedLocation = storageFixture.createUniqueStorage(SCENARIO_PREFIX + "loc-");
        StorageResponse recipient1 = storageFixture.createUniqueStorage(SCENARIO_PREFIX + "r1-");
        StorageResponse recipient2 = storageFixture.createUniqueStorage(SCENARIO_PREFIX + "r2-");
        StorageResponse recipient3 = storageFixture.createUniqueStorage(SCENARIO_PREFIX + "r3-");

        StorageRegionResponse region1 = regionFixture.createRegion(
                recipient1, StorageAccessMode.FULL_ACCESS, SCENARIO_PREFIX + "reg1-");
        StorageRegionResponse region2 = regionFixture.createRegion(
                recipient2, StorageAccessMode.FULL_ACCESS, SCENARIO_PREFIX + "reg2-");
        StorageRegionResponse region3 = regionFixture.createRegion(
                recipient3, StorageAccessMode.FULL_ACCESS, SCENARIO_PREFIX + "reg3-");

        regionFixture.addRegionLocations(region1.getId(), sharedLocation.getId());
        regionFixture.addRegionLocations(region2.getId(), sharedLocation.getId());
        regionFixture.addRegionLocations(region3.getId(), sharedLocation.getId());
        regionFixture.addRegionMembers(region1.getId(), owner2StorageId);
        regionFixture.addRegionMembers(region2.getId(), owner2StorageId);
        regionFixture.addRegionMembers(region3.getId(), owner2StorageId);

        return new VisibilityScenario(sharedLocation);
    }

    /**
     * Фільтр як у {@code useRelocationCreateOutput.ts}: без SUPPLIER і без поточного sender.
     * Дедуплікація за id — очікувана поведінка після union областей видимості.
     */
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
                log.info("OWNER_2 storage {} set to REGIONS for TC-UI-REL-010", owner2StorageId);
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
                log.info("OWNER_2 storage {} accessMode restored to {}", owner2StorageId, originalOwner2AccessMode);
            } catch (Exception e) {
                log.warn("Failed to restore OWNER_2 storage accessMode: {}", e.getMessage());
            }
        }
    }

    private record VisibilityScenario(StorageResponse sharedLocation) {
    }
}
