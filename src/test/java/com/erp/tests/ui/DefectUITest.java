package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.defect.DefectDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.DefectFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.models.request.DefectWriteOffRequest;
import com.erp.models.response.DefectResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.DefectFormPage;
import com.erp.pages.DefectsPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI smoke coverage for the Defect ("Брак") module (tk-ui {@code DefectListPage} / {@code DefectFormPage}).
 *
 * <p>Also verifies, at the UI layer, the two client-side-only guards that made
 * {@code DefectTest.testCannotWriteOffMoreThanCreated*} / {@code testCannotDelete*WrittenOff*}
 * pass with real backend responses of 200 (no server-side enforcement — see
 * {@code DefectWriteOffDialog.canSubmit} and {@code DefectListPage.handleDelete}):
 * the normal user flow through the UI is safe, but the same operations sent directly to the
 * API bypass these checks entirely (tracked as backend defects, not covered here).
 *
 * <p>The third "known defect" (RELOCATION defect offered for outbound sends / in-transit
 * relocations) is guarded by the {@code RELOCATION_STATES_FOR_DEFECT} filter in
 * {@code DefectFormPage.tsx} (FINISHED / AUTO_FINISHED / RETURNED only) — verified here by
 * confirming a CREATED (in-transit) relocation never appears in the relocation picker.
 */
@Slf4j
@Epic("Defects")
@Feature("Defect Management UI (Брак)")
public class DefectUITest extends BaseUITest {

    private DefectFixture fixture;
    private ResourceFixture resourceFixture;
    private StorageFixture storageFixture;
    private Long storageId;
    private Long owner2StorageId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        fixture = new DefectFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        storageFixture = new StorageFixture(testContext, apiExecutor);
        fixture.prepareContext();
        fixture.fetchSharedUnit(3);
        fixture.fetchSharedResourceCategory();

        storageId = ConfigProvider.getOwner1StorageId();
        owner2StorageId = ConfigProvider.getOwner2StorageId();
    }

    @BeforeMethod(alwaysRun = true)
    public void prepareUiSession() {
        // ADMIN is used for the browser session: on this environment OWNER_1 lacks the
        // frontend `dispose` permission (no «Списати» button rendered at all), even though
        // the backend accepts write-off calls made by OWNER_1 directly (see DefectTest.java) —
        // a UI/API permission mismatch worth flagging separately from this suite's scope.
        injectRoleSession(UserRole.ADMIN, storageId);
    }

    @Test(priority = 10)
    @TestCaseId("TC-UI-DEF-001")
    @Story("Defect journal loads")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Сторінка /defects завантажується: заголовок «Брак», кнопка «Додати запис» видимі")
    public void testDefectListLoads() {
        DefectsPage defectsPage = new DefectsPage(page).open();

        assertThat(defectsPage.isHeadingVisible()).as("Заголовок «Брак»").isTrue();
        assertThat(defectsPage.isCreateButtonVisible()).as("Кнопка «Додати запис»").isTrue();
        defectsPage.attachScreenshot("TC-UI-DEF-001 — defect journal loaded");
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-DEF-002")
    @Story("Create a STORAGE defect via UI")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Форма /defects/create: тип «Склад», ресурс, кількість — новий запис з'являється у списку")
    public void testCreateStorageDefect() {
        String resourceName = seedResourceWithStock("ui-def-create-", 10.0);

        DefectsPage defectsPage = new DefectsPage(page).open();
        DefectFormPage form = defectsPage.clickCreate();

        form.selectType(DefectFormPage.TYPE_STORAGE)
                .selectResourceByName(resourceName)
                .fillAmount("4")
                .fillDescription("TC-UI-DEF-002 UI create");
        page.waitForTimeout(300);

        assertThat(form.isSubmitDisabled()).as("Кнопка «Зберегти» має бути активною").isFalse();
        form.attachScreenshot("TC-UI-DEF-002 — form filled");

        DefectsPage afterSubmit = form.submitAndWaitForList();

        assertThat(afterSubmit.isRowWithResourceVisible(resourceName))
                .as("Новий запис браку має з'явитись у списку")
                .isTrue();
        assertThat(afterSubmit.getRemainingAmount(resourceName)).contains("4");
        afterSubmit.attachScreenshot("TC-UI-DEF-002 — created record in list");
    }

    @Test(priority = 30)
    @TestCaseId("TC-UI-DEF-003")
    @Story("Write off within remaining amount succeeds")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Списати 4 з 6 через діалог «Списати брак» — залишок і «Списано» оновлюються коректно")
    public void testWriteOffWithinLimitSucceeds() {
        String resourceName = createStorageDefectViaApi(6.0);

        DefectsPage defectsPage = new DefectsPage(page).open();
        defectsPage.openWriteOffDialog(resourceName);
        assertThat(defectsPage.isWriteOffDialogVisible()).as("Діалог «Списати брак»").isTrue();

        defectsPage.fillWriteOffQuantity("4");
        assertThat(defectsPage.isWriteOffSaveDisabled())
                .as("«Зберегти» має бути активним для коректної кількості")
                .isFalse();
        defectsPage.attachScreenshot("TC-UI-DEF-003 — write-off dialog filled");

        defectsPage.saveWriteOff();

        assertThat(defectsPage.getRemainingAmount(resourceName)).contains("2");
        assertThat(defectsPage.getWrittenOffAmount(resourceName)).contains("4");
        defectsPage.attachScreenshot("TC-UI-DEF-003 — after write-off");
    }

    @Test(priority = 40)
    @TestCaseId("TC-UI-DEF-004")
    @Story("UI blocks over-limit write-off (client-side guard)")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Відомий дефект бекенда: POST /defects/write-off приймає amount > залишку браку (200 замість 4xx) —
            див. DefectTest.testCannotWriteOffMoreThanCreated*. У звичайному UI-флоу ця операція
            неможлива: DefectWriteOffDialog.canSubmit блокує «Зберегти», коли кількість > залишку.
            Цей тест підтверджує, що захист справді працює на рівні UI (defense-in-depth відсутній лише
            на бекенді).""")
    public void testUiBlocksOverLimitWriteOff() {
        String resourceName = createStorageDefectViaApi(6.0);

        DefectsPage defectsPage = new DefectsPage(page).open();
        defectsPage.openWriteOffDialog(resourceName);
        defectsPage.fillWriteOffQuantity("10");

        assertThat(defectsPage.isWriteOffSaveDisabled())
                .as("«Зберегти» має бути заблоковано при кількості > залишку браку")
                .isTrue();
        defectsPage.attachScreenshot("TC-UI-DEF-004 — over-limit write-off blocked");

        defectsPage.cancelWriteOffDialog();
        assertThat(defectsPage.getRemainingAmount(resourceName))
                .as("Залишок браку не мав змінитись")
                .contains("6");
    }

    @Test(priority = 50)
    @TestCaseId("TC-UI-DEF-005")
    @Story("UI blocks deleting a defect with write-offs (client-side guard)")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Відомий дефект бекенда: DELETE /defects/{id} видаляє запис навіть якщо є списання (200 замість 4xx) —
            див. DefectTest.testCannotDeleteDefectWithWriteOffs / testCannotDeleteWrittenOffDefect.
            У звичайному UI-флоу видалення блокується нативним alert() у DefectListPage.handleDelete,
            коли item.writeOffAmount > 0 — запит DELETE до API взагалі не надсилається.""")
    public void testUiBlocksDeleteOfDefectWithWriteOff() {
        String resourceName = createStorageDefectViaApi(6.0);
        DefectResponse created = fixture.getById(UserRole.OWNER_1,
                fixture.listDefects(com.erp.models.query.DefectQuery.builder()
                                .storageId(storageId).resourceSearch(resourceName).pageSize(1).build())
                        .getFirst().getId());

        DefectWriteOffRequest writeOff = DefectDataFactory.buildWriteOffForDefect(
                created, storageId, 3.0, "TC-UI-DEF-005 partial write-off setup");
        fixture.writeOffAs(UserRole.OWNER_1, writeOff);

        DefectsPage defectsPage = new DefectsPage(page).open();
        String alertMessage = defectsPage.clickDeleteExpectingBlockAlert(resourceName);

        assertThat(alertMessage)
                .as("Alert має пояснювати, чому видалення заблоковано")
                .contains("не може бути видалений");
        assertThat(defectsPage.isRowWithResourceVisible(resourceName))
                .as("Запис браку має лишитись у списку — видалення заблоковано на UI")
                .isTrue();
        defectsPage.attachScreenshot("TC-UI-DEF-005 — delete blocked by alert");
    }

    @Test(priority = 60)
    @TestCaseId("TC-UI-DEF-006")
    @Story("In-transit relocations never appear in the relocation defect picker")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Відомий дефект бекенда: POST /defects приймає relocationId переміщення у статусі CREATED —
            див. DefectTest.testInTransitNotOfferedForDefect. У звичайному UI-флоу це неможливо:
            DefectFormPage фільтрує переміщення до RELOCATION_STATES_FOR_DEFECT
            (FINISHED / AUTO_FINISHED / RETURNED) — переміщення CREATED ніколи не потрапляє у таблицю вибору.""")
    public void testInTransitRelocationNeverOfferedInPicker() {
        ResourceResponse resource = resourceFixture.createUniqueResource("ui-def-transit-");
        Long resourceId = resource.getId();
        String resourceName = resource.getName().trim();
        fixture.createExternalReceipt(resourceId, 10.0, "ui-transit-" + System.currentTimeMillis());
        fixture.getRelocationFixture().createSend(UserRole.OWNER_1, storageId, owner2StorageId, resourceId, 5.0);

        String senderName = storageFixture.getById(UserRole.ADMIN, storageId).getName().trim();

        injectRoleSession(UserRole.OWNER_2, owner2StorageId);
        DefectFormPage form = new DefectFormPage(page).open();
        form.selectType(DefectFormPage.TYPE_RELOCATION)
                .selectResourceByName(resourceName)
                .selectSenderByName(senderName);
        page.waitForTimeout(500);

        assertThat(form.getSourceTableRowCount())
                .as("Переміщення CREATED (в дорозі) не повинно потрапляти у список для вибору")
                .isZero();
        form.attachScreenshot("TC-UI-DEF-006 — in-transit relocation absent from picker");
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    /** Creates a fresh (uniquely-named) resource, tops it up via an external receipt, returns its name. */
    private String seedResourceWithStock(String namePrefix, double amount) {
        ResourceResponse resource = resourceFixture.createUniqueResource(namePrefix);
        fixture.createExternalReceipt(resource.getId(), amount, namePrefix + System.currentTimeMillis());
        return resource.getName().trim();
    }

    /** Seeds stock via API and creates a STORAGE-type defect directly, returning the resource name. */
    private String createStorageDefectViaApi(double defectAmount) {
        ResourceResponse resource = resourceFixture.createUniqueResource("ui-def-wo-");
        Long resourceId = resource.getId();
        fixture.createExternalReceipt(resourceId, defectAmount + 5.0, "ui-wo-" + System.currentTimeMillis());
        fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildStorageFifoDefect(storageId, resourceId, defectAmount));
        return resource.getName().trim();
    }

    private void injectRoleSession(UserRole role, long selectedStorageId) {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(role.getUsername(), role.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
    }
}
