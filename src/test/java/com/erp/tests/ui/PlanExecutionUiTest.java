package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.PlanExecutionFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.PlanResponse;
import com.erp.models.response.ResourceResponse;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI coverage for the Plan Execution page ("Виконання плану", tk-ui {@code PlanExecutionPage.tsx}):
 * the lead/lag card ("Випередження" / "Відставання") must be visible only when the storage has a
 * PLAN for the current month (it compares actual output against a planned goal, so it is hidden
 * whenever no plan exists — even if the storage already has unplanned production this month), and
 * absent otherwise; plus clipboard copy of produced amounts.
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
 * <p>Also covers favourite-products filtering (CPMA-587): «Лише обрані» / «Керувати обраними»
 * on the execution tab.
 *
 * <p>Jira: CPMA-604, CPMA-587
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
    private ManufacturingItemResponse secondProduction;
    private TechnologicalMapFixture.IsolatedTechMapContext currentContext;
    private TechnologicalMapFixture.IsolatedTechMapContext secondContext;
    private Long currentStorageId;

    private UserRole favouritesRole;
    private List<Long> previousFavouriteIds;
    private boolean favouritesMutated;

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
        if (secondProduction != null && currentStorageId != null) {
            fixture.cleanupProduction(secondProduction, currentStorageId);
            secondProduction = null;
        }
        if (currentContext != null && currentStorageId != null) {
            fixture.cleanupTechMap(currentContext.getTechMap(), currentStorageId);
            currentContext = null;
        }
        if (secondContext != null && currentStorageId != null) {
            fixture.cleanupTechMap(secondContext.getTechMap(), currentStorageId);
            secondContext = null;
        }
        if (favouritesMutated && favouritesRole != null) {
            fixture.restoreFavouriteResources(favouritesRole, previousFavouriteIds);
            favouritesMutated = false;
            favouritesRole = null;
            previousFavouriteIds = null;
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

    @Test(priority = 41)
    @TestCaseId("TC-PLN-003")
    @Story("Tech map only (no plan, no production) => product absent from plan-execution")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Arrange: ізольований продукт з активною виробничою техкартою без плану і без виробництва.
            Assert: рядок цього продукту відсутній у таблиці «Виконання плану».""")
    public void techMapOnlyNotShownOnPlanExecution() {
        currentStorageId = ownerStorageId;
        currentContext = fixture.createIsolatedProduct(ownerStorageId);
        String productName = currentContext.getProduct().getName().trim();

        injectRoleSession(UserRole.OWNER_1, ownerStorageId);
        PlanExecutionPage planPage = new PlanExecutionPage(page).open();

        assertThat(planPage.isProductRowVisible(productName))
                .as("Продукт лише з техкартою (без плану і виробництва) не має бути в «Виконання плану»")
                .isFalse();
        planPage.attachScreenshot("TC-PLN-003 — tech map only — product absent");
    }

    @Test(priority = 42)
    @TestCaseId("TC-PLN-004")
    @Story("Tech map + plan, no production => product shown on plan-execution")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Arrange: ізольований продукт з техкартою і планом поточного місяця без виробництва.
            Assert: рядок продукту видимий; ціль з плану; вироблено 0.""")
    public void techMapWithPlanShownOnPlanExecution() {
        currentStorageId = ownerStorageId;
        fixture.ensureNoPlanForCurrentMonth(ownerStorageId);
        currentContext = fixture.createIsolatedProduct(ownerStorageId);
        currentPlan = fixture.createCurrentMonthPlan(ownerStorageId, currentContext.getProduct().getId(), 15.0);
        String productName = currentContext.getProduct().getName().trim();

        injectRoleSession(UserRole.OWNER_1, ownerStorageId);
        PlanExecutionPage planPage = new PlanExecutionPage(page).open();

        assertThat(planPage.isProductRowVisible(productName))
                .as("Продукт з планом (навіть без виробництва) має бути в «Виконання плану»")
                .isTrue();
        assertThat(planPage.getGoalCellText(productName)).contains("15");
        assertThat(planPage.getProducedCellText(productName)).contains("0");
        planPage.attachScreenshot("TC-PLN-004 — plan, no production — product visible");
    }

    @Test(priority = 45)
    @TestCaseId("TC-UI-PLANEXEC-009")
    @Story("Copy produced amounts to clipboard (Owner)")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Arrange: видалити план поточного місяця (якщо є); створити ізольований продукт з
            активною виробничою техкартою і виготовити партію за поточний місяць.
            Act: на вкладці «Виконання» натиснути «Скопіювати».
            Assert: кнопка активна; з’являється фідбек «Скопійовано зроблене»; буфер обміну
            містить рядок у форматі «<Назва> - <Кількість вироблено> <од. вимір.>»
            (наприклад, «TM-OUT-… - 5 шт»). Інші рядки таблиці (якщо є на спільному складі)
            не заважають — перевіряємо наявність саме нашого рядка.""")
    public void testOwnerCopyProducedToClipboard() {
        currentStorageId = ownerStorageId;
        fixture.ensureNoPlanForCurrentMonth(ownerStorageId);
        currentContext = fixture.createIsolatedProduct(ownerStorageId);
        double producedAmount = 5.0;
        currentProduction = fixture.createCurrentMonthProduction(
                ownerStorageId, currentContext.getTechMap(), producedAmount);

        String productName = currentContext.getProduct().getName().trim();
        String unitShortName = currentContext.getProduct().getUnit().getShortName();
        String amountText = BigDecimal.valueOf(producedAmount).stripTrailingZeros().toPlainString();
        String expectedLine = productName + " - " + amountText + " " + unitShortName;

        injectRoleSession(UserRole.OWNER_1, ownerStorageId);
        PlanExecutionPage planPage = new PlanExecutionPage(page).open();

        assertThat(planPage.isProductRowVisible(productName))
                .as("Рядок продукту з виробництвом має бути видимий у таблиці")
                .isTrue();
        assertThat(planPage.isCopyButtonVisible())
                .as("Кнопка «Скопіювати» має бути видима на вкладці «Виконання»")
                .isTrue();
        assertThat(planPage.isCopyButtonEnabled())
                .as("Кнопка «Скопіювати» має бути активна, коли є рядки виконання")
                .isTrue();

        planPage.installClipboardCapture()
                .clickCopyProduced()
                .waitForCopiedFeedback();

        String clipboard = planPage.getCapturedClipboardText();
        assertThat(clipboard.lines())
                .as("Буфер має містити рядок «%s» (назва - кількість од.вимір.)", expectedLine)
                .anyMatch(line -> expectedLine.equals(line.trim()));
        planPage.attachScreenshot("TC-UI-PLANEXEC-009 — copy produced to clipboard");
    }

    @Test(priority = 46)
    @TestCaseId("TC-UI-PLANEXEC-010")
    @Story("Favourite products filter — disabled without configured favourites (Owner)")
    @Severity(SeverityLevel.NORMAL)
    @Issue("CPMA-587")
    @Description("""
            Arrange: очистити обрані продукти OWNER_1; створити ізольований продукт з виробництвом
            за поточний місяць (щоб вкладка «Виконання» не була порожньою).
            Assert: кнопки «Лише обрані» і «Керувати обраними (0)» видимі; «Лише обрані» disabled,
            бо обрані не налаштовані (вимога продукту: «Тільки обрані» disabled без обраних).

            Відомий дефект: tk-ui CPMA-587 зараз лишає «Лише обрані» завжди enabled і показує
            empty state «Немає обраних ресурсів…» після кліку. Очікувана поведінка — disabled
            до налаштування обраних; тест червоний до фіксу в tk-ui.

            Додатковий баг продукту (прогін 34): тест падає ще раніше, на arrange —
            PUT /resources/user-bundles з порожнім списком віддає 500 замість 200, тому обрані
            неможливо очистити через API. Потрібен фікс у tk.""")
    public void testOwnerFavouritesOnlyDisabledWhenEmpty() {
        currentStorageId = ownerStorageId;
        favouritesRole = UserRole.OWNER_1;
        previousFavouriteIds = fixture.snapshotFavouriteResourceIds(favouritesRole);
        favouritesMutated = true;
        fixture.saveFavouriteResources(favouritesRole, List.of());

        fixture.ensureNoPlanForCurrentMonth(ownerStorageId);
        currentContext = fixture.createIsolatedProduct(ownerStorageId);
        currentProduction = fixture.createCurrentMonthProduction(
                ownerStorageId, currentContext.getTechMap(), 3.0);

        injectRoleSession(UserRole.OWNER_1, ownerStorageId);
        PlanExecutionPage planPage = new PlanExecutionPage(page).open();

        assertThat(planPage.isFavouritesOnlyButtonVisible())
                .as("Кнопка «Лише обрані» має бути видима на вкладці «Виконання»")
                .isTrue();
        assertThat(planPage.isManageFavouritesButtonVisible())
                .as("Кнопка «Керувати обраними» має бути видима")
                .isTrue();
        assertThat(planPage.getManageFavouritesButtonText())
                .as("Лічильник обраних має бути 0 після очищення")
                .isEqualTo("Керувати обраними (0)");
        assertThat(planPage.isFavouritesOnlyButtonEnabled())
                .as("«Лише обрані» має бути disabled, коли обрані не налаштовані")
                .isFalse();
        planPage.attachScreenshot("TC-UI-PLANEXEC-010 — favourites only disabled when empty");
    }

    @Test(priority = 47)
    @TestCaseId("TC-UI-PLANEXEC-011")
    @Story("Configure favourites and filter execution table (Owner)")
    @Severity(SeverityLevel.CRITICAL)
    @Issue("CPMA-587")
    @Description("""
            Arrange: очистити обрані OWNER_1; створити два ізольовані продукти з виробництвом;
            через API PUT /app-config/favourite-resources зберегти лише перший як обраний.
            Act (UI): відкрити сторінку → «Керувати обраними» (модалка) → закрити →
            «Лише обрані».
            Assert: модалка «Керування обраними ресурсами» відкривається; лічильник «(1)»;
            після фільтра в таблиці видно лише обраний продукт, другий прихований.

            Примітка: вибір зірки в модалці для щойно створених продуктів на staging може
            не працювати через бекенд-фільтр GET /resources/with-technological-map
            (порівнює OutputResourceUsage.id з resource.id). Тому arrange обраних — через API,
            UI покриває модалку + фільтр таблиці виконання.""")
    public void testOwnerConfigureFavouritesAndFilter() {
        currentStorageId = ownerStorageId;
        favouritesRole = UserRole.OWNER_1;
        previousFavouriteIds = fixture.snapshotFavouriteResourceIds(favouritesRole);
        favouritesMutated = true;
        fixture.saveFavouriteResources(favouritesRole, List.of());

        fixture.ensureNoPlanForCurrentMonth(ownerStorageId);
        currentContext = fixture.createIsolatedProduct(ownerStorageId);
        currentProduction = fixture.createCurrentMonthProduction(
                ownerStorageId, currentContext.getTechMap(), 4.0);
        secondContext = fixture.createIsolatedProduct(ownerStorageId);
        secondProduction = fixture.createCurrentMonthProduction(
                ownerStorageId, secondContext.getTechMap(), 4.0);

        String favouriteProduct = currentContext.getProduct().getName().trim();
        String otherProduct = secondContext.getProduct().getName().trim();
        Long favouriteId = currentContext.getProduct().getId();

        fixture.saveFavouriteResources(favouritesRole, List.of(favouriteId));

        injectRoleSession(UserRole.OWNER_1, ownerStorageId);
        PlanExecutionPage planPage = new PlanExecutionPage(page).open();

        assertThat(planPage.isProductRowVisible(favouriteProduct)).isTrue();
        assertThat(planPage.isProductRowVisible(otherProduct)).isTrue();
        assertThat(planPage.getManageFavouritesButtonText())
                .as("Після API-збереження лічильник обраних має бути 1")
                .isEqualTo("Керувати обраними (1)");

        planPage.openManageFavouritesDialog();
        assertThat(planPage.isManageFavouritesDialogVisible())
                .as("Модалка «Керування обраними ресурсами» має відкритись")
                .isTrue();
        planPage.cancelManageFavouritesDialog();

        assertThat(planPage.isFavouritesOnlyButtonEnabled())
                .as("З налаштованими обраними «Лише обрані» має бути активною")
                .isTrue();
        String executionBody = planPage.clickFavouritesOnlyAndCaptureExecutionRequestBody();

        assertThat(executionBody)
                .as("POST /statistics/execution має містити resourceIds з обраним продуктом id=%s", favouriteId)
                .contains("\"resourceIds\"")
                .contains(String.valueOf(favouriteId));
        assertThat(planPage.isFavouritesOnlyPressed())
                .as("«Лише обрані» має бути в натиснутому стані")
                .isTrue();
        assertThat(planPage.isProductRowVisible(favouriteProduct))
                .as("Обраний продукт має лишатись у таблиці")
                .isTrue();
        assertThat(planPage.isProductRowVisible(otherProduct))
                .as("Необраний продукт має бути прихований фільтром")
                .isFalse();
        planPage.attachScreenshot("TC-UI-PLANEXEC-011 — favourites filter applied");
    }

    @Test(priority = 48)
    @TestCaseId("TC-UI-PLANEXEC-012")
    @Story("Add product to favourites via manage modal (Owner)")
    @Severity(SeverityLevel.CRITICAL)
    @Issue("CPMA-587")
    @Description("""
            Arrange: очистити обрані OWNER_1; створити два ізольовані продукти з виробництвом;
            через API зберегти лише перший як обраний (щоб стартовий стан був непорожнім).
            Act (UI): «Керувати обраними» → у каталозі знайти другий продукт → зірка/рядок →
            «Зберегти».
            Assert: лічильник «Керувати обраними (2)»; «Лише обрані» на сторінці показує обидва
            продукти.

            Arrange також перевіряє API-каталог (щоб відділити бекенд від UI-синхронізації).
            Модалка debounce-ить пошук на 250&nbsp;ms — UI-крок чекає GET
            /resources/with-technological-map, не фіксований sleep.""")
    public void testOwnerAddFavouriteViaManageDialog() {
        currentStorageId = ownerStorageId;
        favouritesRole = UserRole.OWNER_1;
        previousFavouriteIds = fixture.snapshotFavouriteResourceIds(favouritesRole);
        favouritesMutated = true;
        fixture.saveFavouriteResources(favouritesRole, List.of());

        fixture.ensureNoPlanForCurrentMonth(ownerStorageId);
        currentContext = fixture.createIsolatedProduct(ownerStorageId);
        currentProduction = fixture.createCurrentMonthProduction(
                ownerStorageId, currentContext.getTechMap(), 4.0);
        secondContext = fixture.createIsolatedProduct(ownerStorageId);
        secondProduction = fixture.createCurrentMonthProduction(
                ownerStorageId, secondContext.getTechMap(), 4.0);

        String existingFavourite = currentContext.getProduct().getName().trim();
        String productToAdd = secondContext.getProduct().getName().trim();
        fixture.saveFavouriteResources(favouritesRole, List.of(currentContext.getProduct().getId()));

        var resourceFixture = new ResourceFixture(testContext, apiExecutor);
        assertThat(resourceFixture.getWithTechnologicalMap(
                        favouritesRole, ownerStorageId, true, productToAdd))
                .as("API-каталог має містити другий output для модалки «Керувати обраними»")
                .extracting(ResourceResponse::getId)
                .contains(secondContext.getProduct().getId());

        injectRoleSession(UserRole.OWNER_1, ownerStorageId);
        PlanExecutionPage planPage = new PlanExecutionPage(page).open();

        assertThat(planPage.getManageFavouritesButtonText())
                .isEqualTo("Керувати обраними (1)");

        planPage.openManageFavouritesDialog();
        assertThat(planPage.isManageFavouritesDialogVisible()).isTrue();
        assertThat(planPage.isManageDialogOnlyFavouritesChecked())
                .as("За замовчуванням модалка показує каталог, не лише обрані")
                .isFalse();

        planPage.filterManageDialogByName(productToAdd);
        assertThat(planPage.isProductListedInManageDialog(productToAdd))
                .as("Другий продукт має з'явитись у каталозі модалки для додавання в обрані")
                .isTrue();

        planPage.toggleFavouriteInManageDialog(productToAdd);
        assertThat(planPage.isProductFavouritedInManageDialog(productToAdd))
                .as("Після кліку продукт має бути позначений як обраний")
                .isTrue();
        planPage.saveManageFavouritesDialog();

        assertThat(planPage.getManageFavouritesButtonText())
                .as("Після UI-додавання лічильник має стати 2")
                .isEqualTo("Керувати обраними (2)");

        planPage.clickFavouritesOnly();
        assertThat(planPage.isProductRowVisible(existingFavourite)).isTrue();
        assertThat(planPage.isProductRowVisible(productToAdd)).isTrue();
        planPage.attachScreenshot("TC-UI-PLANEXEC-012 — add favourite via modal");
    }

    @Test(priority = 49)
    @TestCaseId("TC-UI-PLANEXEC-013")
    @Story("Edit existing favourites list via manage modal (Owner)")
    @Severity(SeverityLevel.CRITICAL)
    @Issue("CPMA-587")
    @Description("""
            Arrange: два ізольовані продукти з виробництвом; API зберігає обидва як обрані.
            Act (UI): «Керувати обраними» → чекбокс «Лише обрані» у модалці → прибрати зірку
            з першого → «Зберегти» → на сторінці «Лише обрані».
            Assert: у модалці обидва видно як обрані; після збереження лічильник «(1)»;
            фільтр сторінки показує лише залишений продукт.

            Цей сценарій не залежить від каталогу with-technological-map: вже збережені
            обрані рендеряться з props (resourceInfo) у режимі «Лише обрані» модалки.""")
    public void testOwnerEditExistingFavouritesViaManageDialog() {
        currentStorageId = ownerStorageId;
        favouritesRole = UserRole.OWNER_1;
        previousFavouriteIds = fixture.snapshotFavouriteResourceIds(favouritesRole);
        favouritesMutated = true;
        fixture.saveFavouriteResources(favouritesRole, List.of());

        fixture.ensureNoPlanForCurrentMonth(ownerStorageId);
        currentContext = fixture.createIsolatedProduct(ownerStorageId);
        currentProduction = fixture.createCurrentMonthProduction(
                ownerStorageId, currentContext.getTechMap(), 4.0);
        secondContext = fixture.createIsolatedProduct(ownerStorageId);
        secondProduction = fixture.createCurrentMonthProduction(
                ownerStorageId, secondContext.getTechMap(), 4.0);

        String removedProduct = currentContext.getProduct().getName().trim();
        String keptProduct = secondContext.getProduct().getName().trim();
        fixture.saveFavouriteResources(favouritesRole, List.of(
                currentContext.getProduct().getId(),
                secondContext.getProduct().getId()));

        injectRoleSession(UserRole.OWNER_1, ownerStorageId);
        PlanExecutionPage planPage = new PlanExecutionPage(page).open();

        assertThat(planPage.getManageFavouritesButtonText())
                .isEqualTo("Керувати обраними (2)");
        assertThat(planPage.isProductRowVisible(removedProduct)).isTrue();
        assertThat(planPage.isProductRowVisible(keptProduct)).isTrue();

        planPage.openManageFavouritesDialog()
                .setManageDialogOnlyFavourites(true);

        assertThat(planPage.isProductListedInManageDialog(removedProduct))
                .as("Існуючий обраний продукт має бути у списку «Лише обрані» модалки")
                .isTrue();
        assertThat(planPage.isProductListedInManageDialog(keptProduct)).isTrue();
        assertThat(planPage.isProductFavouritedInManageDialog(removedProduct)).isTrue();
        assertThat(planPage.isProductFavouritedInManageDialog(keptProduct)).isTrue();

        planPage.toggleFavouriteInManageDialog(removedProduct);
        assertThat(planPage.isProductFavouritedInManageDialog(removedProduct))
                .as("Після кліку перший продукт має бути знятий з обраних")
                .isFalse();
        assertThat(planPage.isProductFavouritedInManageDialog(keptProduct)).isTrue();
        planPage.saveManageFavouritesDialog();

        assertThat(planPage.getManageFavouritesButtonText())
                .as("Після редагування лічильник має стати 1")
                .isEqualTo("Керувати обраними (1)");

        planPage.openManageFavouritesDialog()
                .setManageDialogOnlyFavourites(true);
        assertThat(planPage.isProductListedInManageDialog(keptProduct)).isTrue();
        assertThat(planPage.isProductListedInManageDialog(removedProduct))
                .as("Знятий з обраних продукт не має бути у «Лише обрані» модалки")
                .isFalse();
        planPage.cancelManageFavouritesDialog();

        planPage.clickFavouritesOnly();
        assertThat(planPage.isProductRowVisible(keptProduct))
                .as("Залишений обраний продукт має бути у таблиці виконання")
                .isTrue();
        assertThat(planPage.isProductRowVisible(removedProduct))
                .as("Знятий з обраних продукт має бути прихований фільтром сторінки")
                .isFalse();
        planPage.attachScreenshot("TC-UI-PLANEXEC-013 — edit existing favourites");
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
