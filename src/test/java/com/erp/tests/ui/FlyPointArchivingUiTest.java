package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.BusinessRole;
import com.erp.enums.MilUnitType;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.pages.FlyPointClosePage;
import com.erp.pages.FlyPointsPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Real DEV workflow; all storages and the keeper actor are created by this class. */
@Epic("Inventory")
@Feature("Fly point archiving")
public class FlyPointArchivingUiTest extends BaseUITest {

    private StorageFixture storages;
    private InventoryFixture inventory;
    private RelocationFixture relocations;
    private ResourceFixture resources;
    private UserFixture users;
    private StorageResponse battalion;
    private ResourceResponse resource;
    private UserFixture.BusinessActor keeper;
    private final List<Long> ownedPointIds = new ArrayList<>();

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storages = new StorageFixture(testContext, apiExecutor);
        inventory = new InventoryFixture(testContext, apiExecutor);
        relocations = new RelocationFixture(testContext, apiExecutor);
        resources = new ResourceFixture(testContext, apiExecutor);
        users = new UserFixture(testContext, apiExecutor);

        // POST /storages does not persist milUnitType, so use a read-only existing
        // battalion as the parent of exclusively test-owned points.
        battalion = findExistingBattalion();
        resources.fetchSharedUnit(3);
        resources.fetchSharedResourceCategory();
        resource = resources.createUniqueResource("fp-archive-");
        browserContext.addInitScript("localStorage.setItem('selectedStorageId', '" + battalion.getId() + "');"
                + "localStorage.setItem('selectedStorageId:" + UserRole.ADMIN.getUsername() + "', '"
                + battalion.getId() + "');");
    }

    @AfterClass(alwaysRun = true)
    public void cleanupArchivingScenario() {
        if (inventory != null) {
            for (Long id : ownedPointIds) {
                try {
                    inventory.clearStock(id);
                } catch (Exception ignored) {
                    // StorageFixture keeps failed cleanup in the artifact registry for suite retry.
                }
            }
        }
        if (users != null) users.deactivateTrackedUsers();
        if (storages != null) storages.deactivateTrackedStorages(UserRole.ADMIN);
    }

    @Test(priority = 10)
    @TestCaseId("TC-FPA-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Адмін архівує порожню точку через кнопку картки у списку; активний фільтр її приховує.")
    public void adminArchivesEmptyPointFromListCard() {
        StorageResponse point = point("admin-empty-");
        Response listing = apiExecutor.executeWithQueryParams(ApiEndpointDefinition.FLY_POINT_GET_ALL,
                UserRole.ADMIN, Map.of("storageIds", battalion.getId()));
        assertThat(listing.statusCode()).isEqualTo(200);
        assertThat(listing.asString()).as("Точка має бути в API списку").contains(point.getName());
        loginAdmin();

        FlyPointsPage list = new FlyPointsPage(page).open();
        page.getByTestId("fly-point-" + point.getId() + "-archive").waitFor();
        assertThat(list.hasPointCard(point.getId())).isTrue();
        page.getByTestId("fly-point-" + point.getId() + "-archive").click();
        assertThat(page.url()).contains("/fly-points/close/" + point.getId());
        FlyPointClosePage close = new FlyPointClosePage(page).waitForReady();
        assertThat(close.content())
                .contains("Архівувати точку вильоту", point.getName());
        close.confirmArchive();

        assertThat(storages.getById(UserRole.ADMIN, point.getId()).getActive()).isFalse();
        list.waitForLoaded();
        assertThat(list.hasPointCard(point.getId())).isFalse();
        list.selectStatus(FlyPointsPage.ALL_STATUSES);
        assertThat(list.hasPointCard(point.getId())).isTrue();
    }

    @Test(priority = 20)
    @TestCaseId(value = "TC-FPA-002", roles = BusinessRole.UNIT_KOMIRNIK)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Комірник тільки з ролями «Керівник локації» + «Екіпажі: перегляд» може архівувати свою порожню точку.")
    public void standardKeeperCanArchiveEmptyPoint() {
        StorageResponse point = point("keeper-empty-");
        loginKeeper();

        FlyPointsPage list = new FlyPointsPage(page).open();
        page.locator("a[href='/fly-points/" + point.getId() + "']").waitFor();
        assertThat(list.hasPointCard(point.getId())).isTrue();
        assertThat(page.getByTestId("fly-point-" + point.getId() + "-archive").isVisible())
                .as("Комірник має бачити архівацію без додаткових грантів")
                .isTrue();
        page.getByTestId("fly-point-" + point.getId() + "-archive").click();
        new FlyPointClosePage(page).waitForReady().confirmArchive();
        assertThat(storages.getById(UserRole.ADMIN, point.getId()).getActive()).isFalse();
    }

    @Test(priority = 30)
    @TestCaseId("TC-FPA-003")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Активний екіпаж показується першим блокером; прямий DELETE точки не обходить перевірку.")
    public void activeCrewBlocksUiAndDirectApiArchive() {
        StorageResponse point = point("crew-block-");
        StorageResponse crew = storages.createCrewStorage(point.getId(), "crew-block-");
        loginAdmin();

        FlyPointClosePage close = new FlyPointClosePage(page).open(point.getId());
        assertThat(close.content()).contains("На точці є активні екіпажі");
        assertThat(close.hasAction("fly-point-close-move-crews")).isTrue();
        assertThat(close.hasAction("fly-point-close-confirm-archive")).isFalse();
        close.openCrews();
        page.getByTestId("fly-point-close-crew-" + crew.getId()).waitFor();
        assertThat(page.getByTestId("fly-point-close-crew-" + crew.getId()).innerText())
                .contains(crew.getName());

        Response forbidden = storages.deactivate(UserRole.ADMIN, point.getId());
        assertThat(forbidden.statusCode()).isBetween(400, 499);
        assertThat(storages.getById(UserRole.ADMIN, point.getId()).getActive()).isTrue();
    }

    @Test(priority = 40)
    @TestCaseId("TC-FPA-004")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Адмін переміщує єдиний активний екіпаж на іншу активну точку і архівує початкову.")
    public void adminMovesCrewThenArchivesPoint() {
        StorageResponse source = point("crew-source-");
        StorageResponse target = point("crew-target-");
        StorageResponse crew = storages.createCrewStorage(source.getId(), "crew-move-");
        loginAdmin();

        FlyPointClosePage close = new FlyPointClosePage(page).open(source.getId());
        close.openCrews().moveCrew(crew.getId(), target.getName()).doneWithCrews();
        assertThat(storages.getById(UserRole.ADMIN, crew.getId()).getParent().getId())
                .isEqualTo(target.getId());
        assertThat(close.hasAction("fly-point-close-confirm-archive")).isTrue();
        close.confirmArchive();
        assertThat(storages.getById(UserRole.ADMIN, source.getId()).getActive()).isFalse();
    }

    @Test(priority = 50)
    @TestCaseId("TC-FPA-005")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Позитивний залишок блокує архівацію і показує назву, одиницю, кількість та партію.")
    public void stockWarningShowsRequiredFieldsAndBlocksDirectArchive() {
        StorageResponse point = point("stock-block-");
        String batchNumber = "FPA-BATCH-" + System.currentTimeMillis();
        relocations.seedBatchOnStorage(point.getId(), resource.getId(), 7, batchNumber);
        loginAdmin();

        FlyPointClosePage close = new FlyPointClosePage(page).open(point.getId());
        assertThat(storages.deactivate(UserRole.ADMIN, point.getId()).statusCode())
                .isBetween(400, 499);
        assertThat(close.content()).contains("На точці є залишки", resource.getName(),
                resource.getUnit().getShortName(), batchNumber, "7");
        assertThat(close.hasAction("fly-point-close-relocate-resources")).isTrue();
        assertThat(close.hasAction("fly-point-close-create-incident")).isTrue();
    }

    @Test(priority = 60)
    @TestCaseId("TC-FPA-006")
    @Severity(SeverityLevel.NORMAL)
    @Description("З попередження про залишок переміщення і подія відкривають правильні форми з поверненням до архівації.")
    public void stockActionsOpenFormsWithArchiveReturnRoute() {
        StorageResponse point = point("stock-route-");
        relocations.seedExactStock(point.getId(), resource.getId(), 4);
        loginAdmin();

        FlyPointClosePage close = new FlyPointClosePage(page).open(point.getId());
        close.openRelocation();
        assertThat(page.url()).contains("flyPointId=" + point.getId(),
                "returnTo=/fly-points/close/" + point.getId());

        close.open(point.getId()).openIncident();
        assertThat(page.url()).contains("/inventory/create-incident/" + point.getId(),
                "returnTo=/fly-points/close/" + point.getId());
    }

    @Test(priority = 70)
    @TestCaseId("TC-FPA-007")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Після видачі всього залишку вантаж у дорозі сам по собі не блокує архівацію.")
    public void inTransitWithoutPhysicalStockDoesNotBlockArchive() {
        StorageResponse source = point("transit-source-");
        StorageResponse target = point("transit-target-");
        relocations.seedExactStock(source.getId(), resource.getId(), 3);
        relocations.createSend(UserRole.ADMIN, source.getId(), target.getId(), resource.getId(), 3);

        Response prerequisites = apiExecutor.execute(
                ApiEndpointDefinition.FLY_POINT_GET_CLOSE_PREREQUISITES,
                UserRole.ADMIN, null, String.valueOf(source.getId()));
        assertThat(prerequisites.statusCode()).isEqualTo(200);
        assertThat(prerequisites.jsonPath().getList("stocks")).isEmpty();
        assertThat(storages.deactivate(UserRole.ADMIN, source.getId()).statusCode()).isEqualTo(200);
    }

    @Test(priority = 80)
    @TestCaseId("TC-FPA-009")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Прикріплений екіпаж із балансом на точці не можна архівувати через прямий API.")
    public void attachedCrewWithPointStockCannotBeArchived() {
        StorageResponse point = point("crew-stock-parent-");
        StorageResponse crew = storages.createCrewStorage(point.getId(), "crew-own-stock-");
        // Attached crew inventory is proxied to its fly point in the product model.
        relocations.seedExactStock(point.getId(), resource.getId(), 2);

        assertThat(storages.deactivate(UserRole.ADMIN, crew.getId()).statusCode())
                .isBetween(400, 499);
        assertThat(storages.getById(UserRole.ADMIN, crew.getId()).getActive()).isTrue();
    }

    @Test(priority = 90)
    @TestCaseId("TC-FPA-010")
    @Severity(SeverityLevel.CRITICAL)
    @Description("За наявності екіпажу й залишку UI показує екіпаж першим, а після переміщення — залишок.")
    public void crewThenStockAreSequentialBlockers() {
        StorageResponse source = point("two-blockers-source-");
        StorageResponse target = point("two-blockers-target-");
        StorageResponse crew = storages.createCrewStorage(source.getId(), "two-blockers-crew-");
        relocations.seedExactStock(source.getId(), resource.getId(), 3);
        loginAdmin();

        FlyPointClosePage close = new FlyPointClosePage(page).open(source.getId());
        assertThat(close.content()).contains("На точці є активні екіпажі");
        assertThat(close.hasAction("fly-point-close-relocate-resources")).isFalse();
        close.openCrews().moveCrew(crew.getId(), target.getName()).doneWithCrews();
        page.getByTestId("fly-point-close-relocate-resources").waitFor();
        assertThat(close.content()).contains("На точці є залишки");
        assertThat(close.hasAction("fly-point-close-confirm-archive")).isFalse();
        assertThat(storages.getById(UserRole.ADMIN, source.getId()).getActive()).isTrue();
    }

    @Test(priority = 100)
    @TestCaseId("TC-FPA-011")
    @Severity(SeverityLevel.NORMAL)
    @Description("Скасування архівації не деактивує точку.")
    public void cancelArchiveKeepsPointActive() {
        StorageResponse point = point("cancel-");
        loginAdmin();
        new FlyPointClosePage(page).open(point.getId()).cancel();
        page.waitForURL(url -> url.endsWith("/fly-points"));
        assertThat(storages.getById(UserRole.ADMIN, point.getId()).getActive()).isTrue();
    }

    @Test(priority = 110)
    @TestCaseId("TC-FPA-013")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Сервер повторно перевіряє активні екіпажі, створені після відкриття підтвердження.")
    public void concurrentCrewCreationPreventsFinalArchive() {
        StorageResponse point = point("concurrent-");
        loginAdmin();
        FlyPointClosePage close = new FlyPointClosePage(page).open(point.getId());
        assertThat(close.hasAction("fly-point-close-confirm-archive")).isTrue();

        storages.createCrewStorage(point.getId(), "concurrent-crew-");
        com.microsoft.playwright.Response deletion = page.waitForResponse(
                response -> response.url().contains("/api/v1/storages/" + point.getId())
                        && "DELETE".equals(response.request().method()),
                () -> page.getByTestId("fly-point-close-confirm-archive").click());

        assertThat(deletion.status()).as("Сервер повторно перевіряє блокери")
                .isBetween(400, 499);
        assertThat(storages.getById(UserRole.ADMIN, point.getId()).getActive()).isTrue();
    }

    private StorageResponse point(String prefix) {
        StorageResponse point = storages.createFlyPointStorage(battalion.getId(), "fpa-" + prefix);
        ownedPointIds.add(point.getId());
        return point;
    }

    private StorageResponse findExistingBattalion() {
        for (long seed : List.of(ConfigProvider.getOwner1StorageId(),
                ConfigProvider.getUnitStorageId(), 156L)) {
            long id = seed;
            for (int depth = 0; depth < 12 && id > 0; depth++) {
                StorageResponse current = storages.getById(UserRole.ADMIN, id);
                if (MilUnitType.BATALION.name().equals(current.getMilUnitType())) {
                    return current;
                }
                id = current.getParent() == null ? 0 : current.getParent().getId();
            }
        }
        throw new IllegalStateException("No existing BATALION in configured DEV storage ancestry");
    }

    private void loginAdmin() {
        login(UserRole.ADMIN.getUsername(), UserRole.ADMIN.getPassword());
    }

    private void loginKeeper() {
        if (keeper == null) {
            keeper = users.createBusinessActor(getPlaywrightSessionProvider(),
                    BusinessRole.UNIT_KOMIRNIK, List.of(battalion));
            browserContext.addInitScript("localStorage.setItem('selectedStorageId:"
                    + keeper.username() + "', '" + battalion.getId() + "');");
        }
        login(keeper.username(), keeper.password());
    }

    private void login(String username, String password) {
        browserContext.clearCookies();
        injectSessionCookies(authService.getSessionForUser(username, password), sessionCookieDomain());
    }
}
