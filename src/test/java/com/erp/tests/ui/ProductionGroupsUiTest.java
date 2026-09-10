package com.erp.tests.ui;

import com.erp.enums.UserRole;
import com.erp.fixtures.ProductionGroupUiFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.StorageResponse;
import com.erp.pages.ProductionGroupPlanningPage;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import io.qameta.allure.Feature;
import org.testng.annotations.*;
import java.util.*;
import java.util.regex.Pattern;
import static com.erp.api.endpoints.ApiEndpointDefinition.*;
import static com.erp.fixtures.ProductionGroupUiFixture.ok;
import static com.erp.pages.ProductionGroupPlanningPage.regexLiteral;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Feature("Production groups UI")
public class ProductionGroupsUiTest extends BaseUITest {
    private ProductionGroupUiFixture fixture;
    private ProductionGroupPlanningPage planning;
    private long orderId;

    @Test
    @com.erp.annotations.TestCaseId("TC-PG-031")
    public void sendSelectorsExcludeProductionGroups() {
        new com.erp.pages.RelocationPage(page).open().clickSend();
        verifyRelocationLocations("Видати", fixture.groupA, fixture.memberA1, fixture.outside);
    }

    @Test
    @com.erp.annotations.TestCaseId("TC-PG-032")
    public void receiveSelectorsExcludeProductionGroups() {
        List<StorageResponse> locations = fixture.receiveLocations();
        new com.erp.pages.RelocationPage(page).open().clickReceive();
        verifyRelocationLocations("Отримати", locations.get(0), locations.get(1), locations.get(2));
    }

    private void verifyRelocationLocations(String action, StorageResponse groupLocation,
                                           StorageResponse member, StorageResponse outside) {
        // The regular send/receive forms each have one editable location selector;
        // the other location comes from the selected workspace.
        Locator inputs = page.locator("input[placeholder='Оберіть склад...']");
        assertThat(inputs).hasCount(1);
        Locator input = inputs.first();
        org.assertj.core.api.SoftAssertions errors = new org.assertj.core.api.SoftAssertions();
        input.click();
        Locator options = page.locator("[data-slot='combobox-item']");
        input.fill(outside.getName());
        assertThat(options.filter(new Locator.FilterOptions().setHasText(outside.getName()))).isVisible();
        for (String query : List.of("", groupLocation.getName(), groupLocation.getName().split("-loc_")[0])) {
            input.fill(query);
            for (String group : List.of(groupLocation.getName())) {
                try {
                    assertThat(options.filter(new Locator.FilterOptions().setHasText(group))).hasCount(0);
                } catch (AssertionError failure) {
                    attachScreenshot(action + " — group visible; search=" + query);
                    errors.fail(action + ": production group visible: " + group + "; search=" + query, failure);
                }
            }
        }
        for (String location : List.of(member.getName(), outside.getName())) {
            input.fill(location);
            Locator option = options.filter(new Locator.FilterOptions().setHasText(location));
            assertThat(option).isVisible();
        }
        options.filter(new Locator.FilterOptions().setHasText(outside.getName())).click();
        assertThat(input).hasValue(outside.getName());
        page.keyboard().press("Escape");
        attachScreenshot(action + " — ordinary location remains selectable");
        errors.assertAll();
    }

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void seedGroups() {
        fixture = new ProductionGroupUiFixture(testContext, apiExecutor);
        fixture.seed(getPlaywrightSessionProvider());
    }
    @BeforeMethod(alwaysRun = true)
    public void createProductionOrder() {
        orderId = fixture.createOrder();
        admin();
    }
    @AfterMethod(alwaysRun = true)
    public void cleanupOrders() { if (fixture != null) fixture.cleanupOrders(); }
    @AfterClass(alwaysRun = true)
    public void cleanupGroups() { if (fixture != null) fixture.close(); }

    @Test
    public void twoGroupsPlanInRoundsAndPlannerGenerates() {
        planning.openOrder(orderId);
        planning.assign(fixture.output.getName(), fixture.groupA.getName(), 10);
        planning.step4();
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("Делеговано групам")))).isVisible();
        assertThat(page.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText(fixture.groupA.getName())))
                .containsText(fixture.target.getName());
        planning.assertGenerationBlocked();
        planning.clickApi("Надіслати запити групам", "POST", "/production-orders/" + orderId + "/delegations");
        assertThat(planning.button("Перевірити плани груп")).isVisible();
        planning.assertGenerationBlocked();
        long first = fixture.requestId(orderId, fixture.groupA.getId());

        owner(fixture.ownerA, fixture.groupA.getId());
        openRequestFromQueue(first);
        assertThat(page.getByText("Ваші локації:", new Page.GetByTextOptions().setExact(false)))
                .containsText(fixture.memberA1.getName());
        assertThat(page.getByText("Ваші локації:", new Page.GetByTextOptions().setExact(false)))
                .containsText(fixture.memberA2.getName());
        assertThat(planning.item(fixture.component.getName())).hasCount(0);
        planning.openAssignment(fixture.output.getName());
        assertOptions(0, List.of(fixture.memberA1.getName(), fixture.memberA2.getName()),
                List.of(fixture.outside.getName(), fixture.groupB.getName()));
        planning.selectLocation(0, fixture.memberA1.getName()); planning.amount(0, 4);
        assertThat(page.getByRole(AriaRole.DIALOG).getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Зберегти").setExact(true))).isDisabled();
        planning.button("Додати локацію").click();
        planning.selectLocation(1, fixture.memberA2.getName()); planning.amount(1, 6);
        planning.saveAssignment();
        planning.clickApi("Зберегти план групи", "PUT", "/production-order-delegations/" + first + "/plan");
        assertThat(page.getByText(Pattern.compile("Запит спланований:"))).isVisible();
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("До списку запитів")).click();
        assertThat(page.locator("a[href='/production-delegations/" + first + "']")).hasCount(0);

        admin(); planning.openOrder(orderId);
        planning.expandLevels();
        assertThat(planning.item(fixture.output.getName())).containsText(fixture.memberA1.getName());
        planning.assign(fixture.component.getName(), fixture.groupB.getName(), 10);
        planning.step4(); planning.assertGenerationBlocked();
        planning.clickApi("Надіслати запити групам", "POST", "/production-orders/" + orderId + "/delegations");
        long second = fixture.requestId(orderId, fixture.groupB.getId());
        owner(fixture.ownerB, fixture.groupB.getId());
        openRequestFromQueue(second);
        assertThat(planning.item(fixture.output.getName())).hasCount(0);
        planning.assign(fixture.component.getName(), fixture.memberB.getName(), 10);
        planning.clickApi("Зберегти план групи", "PUT", "/production-order-delegations/" + second + "/plan");
        admin(); planning.openOrder(orderId); planning.step4();
        assertThat(planning.button("Згенерувати")).isEnabled();
        planning.clickApi("Згенерувати", "POST", "/production-orders/" + orderId + "/generate");
        assertThat(page.getByText(Pattern.compile("Створено \\d+ завдань"))).isVisible();
        org.assertj.core.api.Assertions.assertThat(fixture.getOrder(orderId).jsonPath().getString("state")).isEqualTo("IN_PROGRESS");
        org.assertj.core.api.Assertions.assertThat(fixture.getOrder(orderId).jsonPath().getInt("delegationProgress.planned")).isEqualTo(2);
        attachScreenshot("Two groups completed; tasks generated");
    }

    @Test
    public void partialDelegationOffersGroupInsteadOfMembers() {
        planning.openOrder(orderId);
        planning.openAssignment(fixture.output.getName());
        assertOptions(0, List.of(fixture.groupA.getName(), fixture.outside.getName()),
                List.of(fixture.memberA1.getName(), fixture.memberA2.getName()));
        planning.selectLocation(0, fixture.groupA.getName()); planning.amount(0, 4);
        planning.button("Додати локацію").click();
        planning.selectLocation(1, fixture.outside.getName()); planning.amount(1, 6);
        planning.saveAssignment();
        planning.expandLevels();
        assertThat(planning.item(fixture.output.getName())).containsText(fixture.groupA.getName());
        assertThat(planning.item(fixture.output.getName())).containsText("розподілить група");
        assertThat(planning.item(fixture.output.getName())).containsText(fixture.outside.getName());
        planning.assign(fixture.component.getName(), fixture.groupB.getName(), 6);
        planning.step4(); planning.assertGenerationBlocked();
        planning.clickApi("Надіслати запити групам", "POST", "/production-orders/" + orderId + "/delegations");
        io.restassured.response.Response requests = ok(fixture.call(PRODUCTION_ORDER_GET_DELEGATIONS, null, orderId));
        org.assertj.core.api.Assertions.assertThat(requests.jsonPath().getDouble("find { it.groupStorage.id == " + fixture.groupA.getId() + " }.amount"))
                .isEqualTo(4.0);
    }

    @Test
    public void staleAnswerShowsErrorAndKeepsEnteredAllocation() {
        long id = fixture.send(orderId);
        owner(fixture.ownerA, fixture.groupA.getId());
        openRequestFromQueue(id);
        planning.assign(fixture.output.getName(), fixture.memberA1.getName(), 10);
        fixture.changeSentPlan(orderId);
        Map<String, Object> before = fixture.getOrder(orderId).jsonPath().getMap("decomposition");
        Response response = page.waitForResponse(r -> r.request().method().equals("PUT")
                        && r.url().endsWith("/production-order-delegations/" + id + "/plan"),
                () -> planning.button("Зберегти план групи").click());
        org.assertj.core.api.Assertions.assertThat(response.status()).isEqualTo(400);
        assertThat(page.getByText(Pattern.compile("Замовник змінив план"))).isVisible();
        assertThat(planning.button("Зберегти план групи")).isEnabled();
        planning.openAssignment(fixture.output.getName());
        assertThat(planning.assignmentRow(0).locator("input[type=number]")).hasValue("10");
        org.assertj.core.api.Assertions.assertThat(fixture.getOrder(orderId).jsonPath().getMap("decomposition")).isEqualTo(before);
        attachScreenshot("Stale plan refused; entered allocation remains");
    }

    @Test
    public void queueIsScopedToGroupOwner() {
        long ownId = fixture.send(orderId);
        fixture.changeSentPlan(orderId);
        long foreignId = fixture.requestId(orderId, fixture.groupB.getId());
        owner(fixture.ownerA, fixture.groupA.getId());
        navigate("/production-delegations");
        assertThat(page.locator("a[href='/production-delegations/" + ownId + "']")).isVisible();
        assertThat(page.locator("a[href='/production-delegations/" + foreignId + "']")).hasCount(0);
        owner(fixture.ownerB, fixture.groupB.getId());
        navigate("/production-delegations");
        assertThat(page.locator("a[href='/production-delegations/" + foreignId + "']")).isVisible();
        assertThat(page.locator("a[href='/production-delegations/" + ownId + "']")).hasCount(0);
    }

    @Test
    public void progressAndCountersReflectOutstandingRequests() {
        fixture.send(orderId); fixture.changeSentPlan(orderId);
        navigate("/production-orders/" + orderId);
        Locator section = page.locator("section").filter(new Locator.FilterOptions()
                .setHas(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("Запити групам")))));
        assertThat(section).containsText("сплановано 0 з 2");
        assertThat(section.getByText("очікує групу", new Locator.GetByTextOptions().setExact(true))).hasCount(2);
        assertThat(section).containsText(fixture.target.getName());
        assertThat(section).containsText(fixture.outside.getName());
        navigate("/production-orders");
        assertThat(orderRow())
                .containsText("Запити груп 0/2");
        int orders = ok(apiExecutor.executeWithQueryParams(ORDER_GET_PAGE, UserRole.ADMIN,
                Map.of("storageIds", fixture.target.getId(), "states", List.of("NEW", "IN_PROGRESS"), "size", 1)))
                .jsonPath().getInt("page.totalElements");
        int production = ok(apiExecutor.executeWithQueryParams(PRODUCTION_ORDER_GET_PAGE, UserRole.ADMIN,
                Map.of("storageIds", fixture.target.getId(), "states", List.of("NEW", "IN_PROGRESS"), "size", 1)))
                .jsonPath().getInt("page.totalElements");
        int queue = ok(fixture.call(PRODUCTION_DELEGATION_QUEUE, null)).jsonPath().getList("$").size();
        assertCounter("Замовлення", orders); assertCounter("Виробничі замовлення", production);
        assertCounter("Запити на виробництво", queue);
        assertThat(page.locator("[data-sidebar='menu-button']").filter(new Locator.FilterOptions()
                .setHasText(Pattern.compile("^Замовлення")))).hasText(counterPattern("Замовлення", orders + production + queue));
        attachScreenshot("Request progress and sidebar counters");
        ok(fixture.call(PRODUCTION_ORDER_PUT_CANCEL, null, orderId));
        page.reload();
        assertCounter("Виробничі замовлення", production - 1);
        // A private owner's queue has no unrelated concurrent requests on shared dev.
        owner(fixture.ownerA, fixture.groupA.getId()); navigate("/production-delegations");
        assertThat(page.getByText("Немає запитів до ваших груп", new Page.GetByTextOptions().setExact(false))).isVisible();
        assertCounter("Запити на виробництво", 0);
    }

    @Test
    public void locationCheckboxPersistsAndGroupIsNotADestination() {
        StorageResponse location = fixture.newLocation();
        navigate("/storage"); navigate("/storage/update/" + location.getId());
        Locator flag = page.getByRole(AriaRole.CHECKBOX, new Page.GetByRoleOptions().setName("Група виробництва"));
        assertThat(flag).not().isChecked(); flag.check();
        planning.clickApi("Зберегти", "PUT", "/storages/" + location.getId());
        navigate("/storage/update/" + location.getId()); assertThat(flag).isChecked();
        planning.openOrder(orderId); planning.tab("1. Що виробити").click();
        page.getByRole(AriaRole.TABPANEL).getByRole(AriaRole.COMBOBOX).first().click();
        assertThat(page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(fixture.target.getName()).setExact(true))).isVisible();
        assertThat(page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(location.getName()).setExact(true))).hasCount(0);
        assertThat(page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(fixture.groupA.getName()).setExact(true))).hasCount(0);
    }

    @Test
    public void refreshLoadsGroupAnswerAndUpdatesProgress() {
        long requestId = fixture.send(orderId);
        planning.openOrder(orderId); planning.step4();
        assertThat(planning.button("Перевірити плани груп")).isVisible();
        fixture.answerA(requestId);
        planning.clickApi("Перевірити плани груп", "GET", "/production-orders/" + orderId);
        assertThat(planning.tab("4. Завдання на локації")).isDisabled();
        planning.tab("2. Хто буде виробляти?").click(); planning.expandLevels();
        assertThat(planning.item(fixture.output.getName())).containsText(fixture.memberA1.getName());
        assertThat(planning.item(fixture.component.getName())).isVisible();
        navigate("/production-orders/" + orderId);
        assertThat(page.getByText("сплановано групою", new Page.GetByTextOptions().setExact(true))).isVisible();
        assertThat(page.getByText(Pattern.compile("сплановано 1 з 1"))).isVisible();
        navigate("/production-orders");
        assertThat(orderRow())
                .containsText("Запити груп 1/1");
        owner(fixture.ownerA, fixture.groupA.getId()); navigate("/production-delegations");
        assertThat(page.getByText("Немає запитів до ваших груп", new Page.GetByTextOptions().setExact(false))).isVisible();
        assertCounter("Запити на виробництво", 0);
    }

    private Locator orderRow() {
        return page.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHas(
                page.getByRole(AriaRole.CELL, new Page.GetByRoleOptions().setName(String.valueOf(orderId)).setExact(true))));
    }
    private void assertCounter(String label, int value) {
        assertThat(page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(Pattern.compile("^" + label))))
                .hasText(counterPattern(label, value));
    }
    private Pattern counterPattern(String label, int value) {
        return Pattern.compile("^" + regexLiteral(label) + "\\s*" + (value == 0 ? "" : value) + "$");
    }
    private void assertOptions(int row, List<String> present, List<String> absent) {
        planning.assignmentRow(row).getByRole(AriaRole.COMBOBOX).first().click();
        for (String name : present) assertThat(page.getByRole(AriaRole.OPTION,
                new Page.GetByRoleOptions().setName(Pattern.compile("^" + regexLiteral(name))))).isVisible();
        for (String name : absent) assertThat(page.getByRole(AriaRole.OPTION,
                new Page.GetByRoleOptions().setName(Pattern.compile("^" + regexLiteral(name))))).hasCount(0);
        page.keyboard().press("Escape");
    }
    private void openRequestFromQueue(long id) {
        navigate("/production-delegations");
        page.locator("a[href='/production-delegations/" + id + "']").click();
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("Запит групі")))).isVisible();
    }
    private void navigate(String path) { page.navigate(ConfigProvider.getBaseUrl() + path); }
    private void admin() { session(cachedSessionCookies(UserRole.ADMIN), fixture.target.getId()); }
    private void owner(UserFixture.RestrictedOwnerUser owner, long group) {
        session(authService.getSessionForUser(owner.username(), owner.password()), group);
    }
    private void session(Map<String, String> cookies, long location) {
        if (page != null) page.close();
        if (browserContext != null) browserContext.close();
        browserContext = getPlaywrightSessionProvider().getBrowser().newContext(new Browser.NewContextOptions()
                .setIgnoreHTTPSErrors(true).setViewportSize(1600, 1100));
        injectSessionCookies(cookies, sessionCookieDomain());
        browserContext.addInitScript("localStorage.setItem('selectedStorageId', '" + location + "');");
        page = browserContext.newPage(); page.setDefaultTimeout(30000); page.setDefaultNavigationTimeout(45000);
        planning = new ProductionGroupPlanningPage(page);
    }
}
