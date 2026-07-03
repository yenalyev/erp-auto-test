package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.PlanExecutionFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.PlanResponse;
import com.erp.pages.PlanExecutionPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI coverage for the Plan Execution page ("Виконання плану", tk-ui {@code PlanExecutionPage.tsx}):
 * the lead/lag card ("Випередження" / "Відставання") must be visible only when the storage has a
 * PLAN for the current month (it compares actual output against a planned goal, so it is hidden
 * whenever no plan exists — even if the storage already has unplanned production this month), and
 * absent otherwise.
 *
 * <p>Covers all 4 data combinations (no plan/no production, no plan/has production, plan/production,
 * plan/no production) for two personas:
 * <ul>
 *     <li>Owner — logs in as {@code OWNER_1}, viewing their own storage.</li>
 *     <li>Admin — logs in as {@code ADMIN}, viewing {@code OWNER_2}'s storage (kept separate from
 *     the Owner group's storage to avoid data collisions between the two groups).</li>
 * </ul>
 *
 * <p>Each scenario arranges its own uniquely-named product with a freshly created PRODUCTION tech
 * map ({@link PlanExecutionFixture#createIsolatedProduct}), so a test's own row is never affected
 * by other products' history. The "no plan / no production" scenarios are the exception: since the
 * lead/lag card depends on the whole storage's product list (not just our own product), they run
 * first (lowest priority in each persona group) and fail fast via
 * {@link PlanExecutionFixture#assertNoProductionThisMonth} if the shared dev/staging storage
 * already has unrelated current-month production — see that method's Javadoc for rationale.
 *
 * <p>Jira: CPMA-604
 */
@Slf4j
@Issue("CPMA-604")
@Epic("Plans")
@Feature("Plan Execution UI (Виконання плану)")
public class PlanExecutionUiTest extends BaseUITest {

    private PlanExecutionFixture fixture;
    private Long ownerStorageId;
    private Long adminViewStorageId;

    private PlanResponse currentPlan;
    private ManufacturingItemResponse currentProduction;
    private TechnologicalMapFixture.IsolatedTechMapContext currentContext;
    private Long currentStorageId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        fixture = new PlanExecutionFixture(testContext, apiExecutor);
        fixture.prepareContext();

        ownerStorageId = ConfigProvider.getOwner1StorageId();
        adminViewStorageId = ConfigProvider.getOwner2StorageId();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupArtifacts() {
        if (currentPlan != null) {
            fixture.cleanupPlan(currentPlan);
            currentPlan = null;
        }
        if (currentProduction != null && currentStorageId != null) {
            fixture.cleanupProduction(currentProduction, currentStorageId);
            currentProduction = null;
        }
        if (currentContext != null && currentStorageId != null) {
            fixture.cleanupTechMap(currentContext.getTechMap(), currentStorageId);
            currentContext = null;
        }
        currentStorageId = null;
    }

    // -------------------------------------------------------------------
    // Owner persona — OWNER_1's own storage
    // -------------------------------------------------------------------

    @Test(priority = 10)
    @TestCaseId("TC-UI-PLANEXEC-001")
    @Story("No plan and no production => lead/lag card hidden (Owner)")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Передумова (fail-fast): сховище OWNER_1 не повинно мати виробництва за поточний місяць —
            якщо на ньому вже є стороннє виробництво (від інших тестів/демо-активності), тест явно
            падає з діагностичним повідомленням замість хибного результату (див.
            PlanExecutionFixture#assertNoProductionThisMonth).
            Arrange: видалити план поточного місяця (якщо є); створити ізольовану виробничу
            техкарту без плану і без виробництва.
            Assert: картка «Випередження»/«Відставання» відсутня, показано порожній стан
            «Дані про виконання плану за цей місяць відсутні».""")
    public void testOwnerNoPlanNoProduction() {
        currentStorageId = ownerStorageId;
        fixture.ensureNoPlanForCurrentMonth(ownerStorageId);
        fixture.assertNoProductionThisMonth(ownerStorageId);
        currentContext = fixture.createIsolatedProduct(ownerStorageId);

        injectRoleSession(UserRole.OWNER_1, ownerStorageId);
        PlanExecutionPage planPage = new PlanExecutionPage(page).open();

        assertThat(planPage.isLeadLagCardVisible())
                .as("Картка «Випередження/Відставання» має бути відсутня без плану і виробництва")
                .isFalse();
        assertThat(planPage.isEmptyStateVisible())
                .as("Має відображатись порожній стан «Дані про виконання плану за цей місяць відсутні»")
                .isTrue();
        planPage.attachScreenshot("TC-UI-PLANEXEC-001 — no plan, no production — no card");
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-PLANEXEC-002")
    @Story("No plan but has production => lead/lag card still hidden (Owner)")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Arrange: видалити план поточного місяця (якщо є); створити ізольований продукт з
            активною виробничою техкартою і виготовити партію за поточний місяць без плану на нього.
            Assert: картка «Випередження»/«Відставання» відсутня — вона прив'язана до наявності
            ПЛАНУ (порівняння факту з ціллю), а не просто до наявності виробництва; рядок продукту
            все ж показує вироблену кількість у таблиці, а колонка «Ціль» — «—» (плану немає).""")
    public void testOwnerNoPlanHasProduction() {
        currentStorageId = ownerStorageId;
        fixture.ensureNoPlanForCurrentMonth(ownerStorageId);
        currentContext = fixture.createIsolatedProduct(ownerStorageId);
        currentProduction = fixture.createCurrentMonthProduction(ownerStorageId, currentContext.getTechMap(), 5.0);
        String productName = currentContext.getProduct().getName().trim();

        injectRoleSession(UserRole.OWNER_1, ownerStorageId);
        PlanExecutionPage planPage = new PlanExecutionPage(page).open();

        assertThat(planPage.isLeadLagCardVisible())
                .as("Картка «Випередження/Відставання» має залишатись прихованою без плану, навіть якщо є виробництво")
                .isFalse();
        assertThat(planPage.isProductRowVisible(productName))
                .as("Рядок продукту з виробництвом має бути видимий у таблиці")
                .isTrue();
        assertThat(planPage.isGoalAbsent(productName))
                .as("Без плану колонка «Ціль» має показувати «—»")
                .isTrue();
        planPage.attachScreenshot("TC-UI-PLANEXEC-002 — no plan, has production — no card");
    }

    @Test(priority = 30)
    @TestCaseId("TC-UI-PLANEXEC-003")
    @Story("Plan and production both present => lead/lag card visible (Owner)")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Arrange: створити ізольований продукт з активною виробничою техкартою; створити план
            поточного місяця (ціль 20) і виготовити партію (5 од.) для цього ж продукту.
            Assert: картка «Випередження»/«Відставання» відображається; рядок продукту показує
            і ціль, і вироблену кількість.""")
    public void testOwnerPlanAndProduction() {
        currentStorageId = ownerStorageId;
        fixture.ensureNoPlanForCurrentMonth(ownerStorageId);
        currentContext = fixture.createIsolatedProduct(ownerStorageId);
        currentPlan = fixture.createCurrentMonthPlan(ownerStorageId, currentContext.getProduct().getId(), 20.0);
        currentProduction = fixture.createCurrentMonthProduction(ownerStorageId, currentContext.getTechMap(), 5.0);
        String productName = currentContext.getProduct().getName().trim();

        injectRoleSession(UserRole.OWNER_1, ownerStorageId);
        PlanExecutionPage planPage = new PlanExecutionPage(page).open();

        assertThat(planPage.isLeadLagCardVisible())
                .as("Картка має з'явитись — є і план, і виробництво")
                .isTrue();
        assertThat(planPage.isProductRowVisible(productName)).isTrue();
        assertThat(planPage.getGoalCellText(productName)).contains("20");
        assertThat(planPage.getProducedCellText(productName)).contains("5");
        planPage.attachScreenshot("TC-UI-PLANEXEC-003 — plan and production — card visible");
    }

    @Test(priority = 40)
    @TestCaseId("TC-UI-PLANEXEC-004")
    @Story("Plan present but no production => lead/lag card visible (Owner)")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Arrange: створити ізольований продукт з активною виробничою техкартою; створити план
            поточного місяця (ціль 15) без жодного виробництва.
            Assert: картка «Випередження»/«Відставання» відображається; рядок продукту показує
            ціль 15 і вироблено 0.""")
    public void testOwnerPlanNoProduction() {
        currentStorageId = ownerStorageId;
        fixture.ensureNoPlanForCurrentMonth(ownerStorageId);
        currentContext = fixture.createIsolatedProduct(ownerStorageId);
        currentPlan = fixture.createCurrentMonthPlan(ownerStorageId, currentContext.getProduct().getId(), 15.0);
        String productName = currentContext.getProduct().getName().trim();

        injectRoleSession(UserRole.OWNER_1, ownerStorageId);
        PlanExecutionPage planPage = new PlanExecutionPage(page).open();

        assertThat(planPage.isLeadLagCardVisible())
                .as("Картка має з'явитись — є план (навіть без виробництва)")
                .isTrue();
        assertThat(planPage.isProductRowVisible(productName)).isTrue();
        assertThat(planPage.getGoalCellText(productName)).contains("15");
        assertThat(planPage.getProducedCellText(productName)).contains("0");
        planPage.attachScreenshot("TC-UI-PLANEXEC-004 — plan, no production — card visible");
    }

    // -------------------------------------------------------------------
    // Admin persona — viewing OWNER_2's storage
    // -------------------------------------------------------------------

    @Test(priority = 50)
    @TestCaseId("TC-UI-PLANEXEC-005")
    @Story("No plan and no production => lead/lag card hidden (Admin)")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Той самий сценарій, що й TC-UI-PLANEXEC-001, але під логіном ADMIN, який переглядає
            сховище OWNER_2 (окреме від сховища овнер-групи, щоб уникнути колізій даних).
            Передумова (fail-fast): сховище OWNER_2 не повинно мати виробництва за поточний місяць.
            Assert: картка відсутня, показано порожній стан.""")
    public void testAdminNoPlanNoProduction() {
        currentStorageId = adminViewStorageId;
        fixture.ensureNoPlanForCurrentMonth(adminViewStorageId);
        fixture.assertNoProductionThisMonth(adminViewStorageId);
        currentContext = fixture.createIsolatedProduct(adminViewStorageId);

        injectRoleSession(UserRole.ADMIN, adminViewStorageId);
        PlanExecutionPage planPage = new PlanExecutionPage(page).open();

        assertThat(planPage.isLeadLagCardVisible())
                .as("Картка «Випередження/Відставання» має бути відсутня без плану і виробництва")
                .isFalse();
        assertThat(planPage.isEmptyStateVisible())
                .as("Має відображатись порожній стан «Дані про виконання плану за цей місяць відсутні»")
                .isTrue();
        planPage.attachScreenshot("TC-UI-PLANEXEC-005 — Admin — no plan, no production — no card");
    }

    @Test(priority = 60)
    @TestCaseId("TC-UI-PLANEXEC-006")
    @Story("No plan but has production => lead/lag card still hidden (Admin)")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Той самий сценарій, що й TC-UI-PLANEXEC-002, але під логіном ADMIN на сховищі OWNER_2.
            Assert: картка відсутня (немає плану для порівняння факту з ціллю); рядок продукту
            все ж показує вироблену кількість у таблиці, «Ціль» — «—».""")
    public void testAdminNoPlanHasProduction() {
        currentStorageId = adminViewStorageId;
        fixture.ensureNoPlanForCurrentMonth(adminViewStorageId);
        currentContext = fixture.createIsolatedProduct(adminViewStorageId);
        currentProduction = fixture.createCurrentMonthProduction(adminViewStorageId, currentContext.getTechMap(), 5.0);
        String productName = currentContext.getProduct().getName().trim();

        injectRoleSession(UserRole.ADMIN, adminViewStorageId);
        PlanExecutionPage planPage = new PlanExecutionPage(page).open();

        assertThat(planPage.isLeadLagCardVisible())
                .as("Картка «Випередження/Відставання» має залишатись прихованою без плану, навіть якщо є виробництво")
                .isFalse();
        assertThat(planPage.isProductRowVisible(productName)).isTrue();
        assertThat(planPage.isGoalAbsent(productName))
                .as("Без плану колонка «Ціль» має показувати «—»")
                .isTrue();
        planPage.attachScreenshot("TC-UI-PLANEXEC-006 — Admin — no plan, has production — no card");
    }

    @Test(priority = 70)
    @TestCaseId("TC-UI-PLANEXEC-007")
    @Story("Plan and production both present => lead/lag card visible (Admin)")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Той самий сценарій, що й TC-UI-PLANEXEC-003, але під логіном ADMIN на сховищі OWNER_2.
            Assert: картка відображається; рядок продукту показує і ціль, і вироблену кількість.""")
    public void testAdminPlanAndProduction() {
        currentStorageId = adminViewStorageId;
        fixture.ensureNoPlanForCurrentMonth(adminViewStorageId);
        currentContext = fixture.createIsolatedProduct(adminViewStorageId);
        currentPlan = fixture.createCurrentMonthPlan(adminViewStorageId, currentContext.getProduct().getId(), 20.0);
        currentProduction = fixture.createCurrentMonthProduction(adminViewStorageId, currentContext.getTechMap(), 5.0);
        String productName = currentContext.getProduct().getName().trim();

        injectRoleSession(UserRole.ADMIN, adminViewStorageId);
        PlanExecutionPage planPage = new PlanExecutionPage(page).open();

        assertThat(planPage.isLeadLagCardVisible())
                .as("Картка має з'явитись — є і план, і виробництво")
                .isTrue();
        assertThat(planPage.isProductRowVisible(productName)).isTrue();
        assertThat(planPage.getGoalCellText(productName)).contains("20");
        assertThat(planPage.getProducedCellText(productName)).contains("5");
        planPage.attachScreenshot("TC-UI-PLANEXEC-007 — Admin — plan and production — card visible");
    }

    @Test(priority = 80)
    @TestCaseId("TC-UI-PLANEXEC-008")
    @Story("Plan present but no production => lead/lag card visible (Admin)")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Той самий сценарій, що й TC-UI-PLANEXEC-004, але під логіном ADMIN на сховищі OWNER_2.
            Assert: картка відображається; ціль 15, вироблено 0.""")
    public void testAdminPlanNoProduction() {
        currentStorageId = adminViewStorageId;
        fixture.ensureNoPlanForCurrentMonth(adminViewStorageId);
        currentContext = fixture.createIsolatedProduct(adminViewStorageId);
        currentPlan = fixture.createCurrentMonthPlan(adminViewStorageId, currentContext.getProduct().getId(), 15.0);
        String productName = currentContext.getProduct().getName().trim();

        injectRoleSession(UserRole.ADMIN, adminViewStorageId);
        PlanExecutionPage planPage = new PlanExecutionPage(page).open();

        assertThat(planPage.isLeadLagCardVisible())
                .as("Картка має з'явитись — є план (навіть без виробництва)")
                .isTrue();
        assertThat(planPage.isProductRowVisible(productName)).isTrue();
        assertThat(planPage.getGoalCellText(productName)).contains("15");
        assertThat(planPage.getProducedCellText(productName)).contains("0");
        planPage.attachScreenshot("TC-UI-PLANEXEC-008 — Admin — plan, no production — card visible");
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

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
