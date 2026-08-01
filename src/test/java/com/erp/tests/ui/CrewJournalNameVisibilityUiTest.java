package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.RelocationState;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.models.common.RelocationJournalRow;
import com.erp.models.request.StorageRequest;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.pages.RelocationPage;
import com.erp.tests.functional.storage.StorageRegionsAllureDescriptions;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI: назви екіпажів у журналі «Видано»/«Отримано» завжди реальні,
 * навіть коли CREW поза областями видимості REGIONS-користувача
 * ({@code StorageNamingService} — CREW не маскується як {@code _приховано_}).
 */
@Slf4j
@Epic("Relocation")
@Feature("Crew journal names")
@Story("Crew names regardless of visibility scope")
public class CrewJournalNameVisibilityUiTest extends BaseUITest {

    private static final Object OWNER2_ACCESS_LOCK = new Object();
    private static final String HIDDEN_NAME = "_приховано_";
    private static final String SCENARIO_PREFIX = "ui-crew-jn-";
    private static final String RESOURCE_PREFIX = "ui-crew-jn-res-";
    private static final double SEND_AMOUNT = 5.0;

    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;
    private CrewRegionFixture crewFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;

    private Long owner2StorageId;
    private Long resourceId;
    private String originalOwner2AccessMode;
    private boolean owner2AccessModeChanged;

    private CrewRegionScenario crewScenario;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        regionFixture = new StorageRegionFixture(testContext, apiExecutor);
        crewFixture = new CrewRegionFixture(testContext, apiExecutor, storageFixture, regionFixture);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);

        relocationFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();

        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        resourceId = resource.getId();

        owner2StorageId = ConfigProvider.getOwner2StorageId();
        ensureOwner2RestrictedAccess();
        regionFixture.purgeViewerVisibilityScope(UserRole.ADMIN, owner2StorageId, storageFixture);
    }

    @AfterClass(alwaysRun = true)
    public void restoreOwner2AccessMode() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
        restoreOwner2AccessIfChanged();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupScenarioArtifacts() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
        crewScenario = null;
    }

    @Test(priority = 10)
    @TestCaseId("TC-UI-CREW-012")
    @Severity(SeverityLevel.CRITICAL)
    @Description(StorageRegionsAllureDescriptions.TC_UI_CREW_012)
    public void sentTabShowsCrewNameOutsideVisibilityScope() {
        crewScenario = crewFixture.prepareSingleCrewScenario(SCENARIO_PREFIX + "sent-");
        String crewName = crewScenario.crew().getName();
        String marker = "TC-UI-CREW-012-" + System.currentTimeMillis();

        Allure.step("API: stock на OWNER_2 і send → crew → finish відправником (поза REGIONS scope)", () -> {
            RelocationStockSeeder.receiveFromSupplier(
                    apiExecutor, UserRole.OWNER_2, owner2StorageId, Map.of(resourceId, 50.0));
            RelocationResponse sent = relocationFixture.createSendWithDescription(
                    UserRole.ADMIN,
                    owner2StorageId,
                    crewScenario.crew().getId(),
                    resourceId,
                    SEND_AMOUNT,
                    marker);
            assertThat(sent.getRecipient()).isNotNull();
            assertThat(sent.getRecipient().getId()).isEqualTo(crewScenario.crew().getId());
            assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);
            RelocationResponse finished = relocationFixture.resolve(
                    UserRole.ADMIN, sent.getId(), owner2StorageId, RelocationState.FINISHED);
            assertThat(finished.getState()).isEqualTo(RelocationState.FINISHED);
        });

        injectOwner2Session(owner2StorageId);

        RelocationPage journal = Allure.step(
                "UI: журнал → «Видано»",
                () -> new RelocationPage(page).open().openSentTab().waitForJournalDataSettled());

        assertThat(journal.isRowWithTextVisible(marker))
                .as("Рядок з маркером «%s» має бути у «Видано»", marker)
                .isTrue();

        RelocationJournalRow row = findRowContaining(journal.getDisplayedJournalRows(), marker, crewName);
        assertThat(row.getRecipientName())
                .as("Колонка «До»: реальна назва екіпажу поза областю видимості")
                .isEqualTo(crewName);
        assertThat(row.getRecipientName())
                .as("Колонка «До» не маскується як «_приховано_» для CREW")
                .isNotEqualTo(HIDDEN_NAME);

        journal.attachScreenshot("TC-UI-CREW-012 — Видано crew name");
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-CREW-013")
    @Severity(SeverityLevel.CRITICAL)
    @Description(StorageRegionsAllureDescriptions.TC_UI_CREW_013)
    public void receivedTabShowsCrewNameOutsideVisibilityScope() {
        crewScenario = crewFixture.prepareSingleCrewScenario(SCENARIO_PREFIX + "recv-");
        String crewName = crewScenario.crew().getName();
        String marker = "TC-UI-CREW-013-" + System.currentTimeMillis();

        Allure.step("API: stock на crew (send+finish) → send crew → OWNER_2 → resolve FINISHED", () -> {
            relocationFixture.ensureStock(crewScenario.memberStorageId(), resourceId, 100.0);
            relocationFixture.createSendAndFinishBySender(
                    UserRole.ADMIN,
                    crewScenario.memberStorageId(),
                    crewScenario.crew().getId(),
                    resourceId,
                    SEND_AMOUNT);

            RelocationResponse inTransit = relocationFixture.createSendWithDescription(
                    UserRole.ADMIN,
                    crewScenario.crew().getId(),
                    owner2StorageId,
                    resourceId,
                    SEND_AMOUNT,
                    marker);
            relocationFixture.resolve(
                    UserRole.OWNER_2, inTransit.getId(), owner2StorageId, RelocationState.FINISHED, marker);
        });

        injectOwner2Session(owner2StorageId);

        RelocationPage journal = Allure.step(
                "UI: журнал → «Отримано»",
                () -> new RelocationPage(page).open().openReceivedTab().waitForJournalDataSettled());

        assertThat(journal.isRowWithTextVisible(marker))
                .as("Рядок з маркером «%s» має бути у «Отримано»", marker)
                .isTrue();
        assertThat(journal.isRowWithTextVisible(crewName))
                .as("Назва екіпажу видима у «Отримано»")
                .isTrue();

        assertThat(journal.getDisplayedJournalRows())
                .as("Колонка «Від»: реальна назва екіпажу поза областю видимості")
                .anySatisfy(row -> {
                    assertThat(row.getSenderName()).isEqualTo(crewName);
                    assertThat(row.getSenderName()).isNotEqualTo(HIDDEN_NAME);
                });

        journal.attachScreenshot("TC-UI-CREW-013 — Отримано crew name");
    }

    @Test(priority = 30)
    @TestCaseId("TC-UI-CREW-014")
    @Severity(SeverityLevel.NORMAL)
    @Description(StorageRegionsAllureDescriptions.TC_UI_CREW_014)
    public void receivedTabHidesNonCrewOutsiderName() {
        StorageResponse outsider = storageFixture.createUniqueStorage(SCENARIO_PREFIX + "out-");
        String marker = "TC-UI-CREW-014-" + System.currentTimeMillis();

        Allure.step("API: send outsider (non-CREW) → OWNER_2 → resolve FINISHED", () -> {
            RelocationStockSeeder.receiveFromSupplier(
                    apiExecutor, UserRole.ADMIN, outsider.getId(), Map.of(resourceId, 50.0));
            RelocationResponse inTransit = relocationFixture.createSendWithDescription(
                    UserRole.ADMIN,
                    outsider.getId(),
                    owner2StorageId,
                    resourceId,
                    SEND_AMOUNT,
                    marker);
            relocationFixture.resolve(
                    UserRole.OWNER_2, inTransit.getId(), owner2StorageId, RelocationState.FINISHED, marker);
        });

        injectOwner2Session(owner2StorageId);

        RelocationPage journal = Allure.step(
                "UI: журнал → «Отримано»",
                () -> new RelocationPage(page).open().openReceivedTab().waitForJournalDataSettled());

        assertThat(journal.isRowWithTextVisible(marker))
                .as("Рядок з маркером «%s» має бути у «Отримано»", marker)
                .isTrue();
        assertThat(journal.isRowWithTextVisible(HIDDEN_NAME))
                .as("Non-CREW outsider маскується як «_приховано_»")
                .isTrue();
        assertThat(journal.isRowWithTextVisible(outsider.getName()))
                .as("Реальне ім'я outsider не показується")
                .isFalse();

        assertThat(journal.getDisplayedJournalRows())
                .as("Колонка «Від» для non-CREW поза scope = «_приховано_»")
                .anyMatch(row -> HIDDEN_NAME.equals(row.getSenderName()));

        journal.attachScreenshot("TC-UI-CREW-014 — Отримано outsider hidden");
    }

    /**
     * Sent-tab rows expose description in col 6; match marker there when present,
     * otherwise fall back to crew name in recipient column.
     */
    private static RelocationJournalRow findRowContaining(
            List<RelocationJournalRow> rows, String marker, String crewName) {
        return rows.stream()
                .filter(row -> (row.getDescription() != null && row.getDescription().contains(marker))
                        || crewName.equals(row.getRecipientName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Не знайдено рядок журналу з маркером «" + marker + "» або екіпажем «" + crewName + "»"));
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
}
