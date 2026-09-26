package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.response.SimpleEntityResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.StorageTechnologicalMapModeResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.TechnologicalMapFormPage;
import com.erp.pages.TechnologicalMapsListPage;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Technological Maps")
@Feature("Tech map actions in the Цукрарня workspace")
public class TechnologicalMapWorkspaceActionsUiTest extends BaseUITest {

    private StorageFixture storageFixture;
    private TechnologicalMapFixture techMapFixture;
    private StorageResponse root;
    private StorageResponse child;
    private StorageTechnologicalMapMode originalRootMode;
    private final Set<Long> createdMapIds = new LinkedHashSet<>();

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        List<StorageResponse> roots = storageFixture.getPageContent(UserRole.ADMIN,
                        java.util.Map.of("name", "Цукрарня", "isActive", true, "size", 100)).stream()
                .filter(storage -> "Цукрарня".equals(storage.getName().trim())).toList();
        assertThat(roots).as("Exactly one active Цукрарня workspace must exist").hasSize(1);
        root = roots.getFirst();
        var modeResponse = apiExecutor.execute(ApiEndpointDefinition.TECH_MAP_MODE_GET,
                UserRole.ADMIN, String.valueOf(root.getId()));
        assertThat(modeResponse.statusCode()).isEqualTo(200);
        originalRootMode = modeResponse.as(StorageTechnologicalMapModeResponse.class).getMode();
        assertThat(originalRootMode).isNotNull();
        techMapFixture.setMode(root.getId(), StorageTechnologicalMapMode.EDIT_ALLOWED);
        child = storageFixture.createProductionStorage(root.getId(), "TM-Workspace");
        techMapFixture.prepareContext();
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
    }

    @DataProvider(name = "unavailableActions")
    public Object[][] unavailableActions() {
        return new Object[][]{{"Редагувати"}, {"Клонувати"}};
    }

    @Test(dataProvider = "unavailableActions")
    @TestCaseId("TC-UI-MFG-WORKSPACE-HINT")
    @Description("""
            CPMA-895: при виборі Цукрарні натиснути «Редагувати» або «Клонувати» для
            техкарти дочірньої локації. Після помилки завантаження показано повідомлення
            з назвою відповідної локації та відновлено список. Техкарта не змінена,
            нових версій немає, POST/PUT/PATCH/DELETE техкарт не відправляються.
            """)
    public void unavailableActionShowsLocationHintAndReturnsToList(String action) {
        TechnologicalMapResponse source = createSource("TM-Workspace-Hint");
        assertRootActionHint(source, action);
        assertThat(techMapFixture.getById(UserRole.ADMIN, source.getId(), child.getId()))
                .usingRecursiveComparison().isEqualTo(source);
        assertThat(techMapFixture.getVersionsByGroupId(UserRole.ADMIN, source.getGroupId(), child.getId()))
                .extracting(TechnologicalMapResponse::getId).containsExactly(source.getId());
    }

    @Test
    @TestCaseId("TC-UI-MFG-WORKSPACE-EDIT")
    @Description("""
            Отримати повідомлення про відповідну локацію при спробі редагування з Цукрарні.
            Через сайдбар перейти на вказану дочірню локацію, повторно натиснути «Редагувати»,
            змінити норму input і зберегти. API підтверджує нову версію, змінену норму
            та початкову прив'язку до локації.
            """)
    public void editAfterSwitchingToSuggestedLocationCreatesNewVersion() {
        TechnologicalMapResponse source = createSource("TM-Workspace-Edit");
        TechnologicalMapsListPage list = openSuggestedLocationAfterHint(source, "Редагувати");
        TechnologicalMapFormPage form = list.editTechMap(source.getName());
        assertThat(URI.create(page.url()).getPath())
                .isEqualTo("/technological-maps/update/" + source.getId());
        assertSelectedWorkspace(false);
        double newAmount = source.getInput().getFirst().getAmount() + 1;
        form.fillInputAmount(0, String.valueOf(newAmount));
        TechnologicalMapResponse updated = save("PUT", "/api/v1/technological-maps/" + source.getId());

        assertThat(updated.getId()).isNotEqualTo(source.getId());
        assertThat(updated.getGroupId()).isEqualTo(source.getGroupId());
        assertThat(updated.getVersion()).isEqualTo(source.getVersion() + 1);
        assertThat(updated.getName()).isEqualTo(source.getName());
        assertThat(updated.getInput().getFirst().getResource().getId())
                .isEqualTo(source.getInput().getFirst().getResource().getId());
        assertThat(updated.getInput().getFirst().getAmount()).isEqualTo(newAmount);
        assertThat(updated.getOutput()).usingRecursiveComparison().isEqualTo(source.getOutput());
        assertStorageBinding(updated);
    }

    @Test
    @TestCaseId("TC-UI-MFG-WORKSPACE-CLONE")
    @Description("""
            Отримати повідомлення про відповідну локацію при спробі клонування з Цукрарні.
            Через сайдбар перейти на вказану дочірню локацію, повторно натиснути «Клонувати»,
            задати унікальну назву і зберегти. Копія має окремі id та groupId,
            той самий склад і локацію; оригінал не змінений.
            """)
    public void cloneAfterSwitchingToSuggestedLocationPreservesSource() {
        TechnologicalMapResponse source = createSource("TM-Workspace-Clone");
        TechnologicalMapsListPage list = openSuggestedLocationAfterHint(source, "Клонувати");
        TechnologicalMapFormPage form = list.cloneTechMap(source.getName());
        assertThat(URI.create(page.url()).getPath()).isEqualTo("/technological-maps/create");
        assertThat(URI.create(page.url()).getQuery()).isEqualTo("cloneId=" + source.getId());
        assertSelectedWorkspace(false);
        String cloneName = source.getName() + "-copy-" + source.getId();
        form.fillName(cloneName);
        TechnologicalMapResponse clone = save("POST", "/api/v1/technological-maps");

        assertThat(clone.getId()).isNotEqualTo(source.getId());
        assertThat(clone.getGroupId()).isNotBlank().isNotEqualTo(source.getGroupId());
        assertThat(clone.getName()).isEqualTo(cloneName);
        assertThat(clone.getType()).isEqualTo(source.getType());
        assertThat(clone.getInput()).usingRecursiveComparison().isEqualTo(source.getInput());
        assertThat(clone.getOutput()).usingRecursiveComparison().isEqualTo(source.getOutput());
        assertStorageBinding(clone);
        assertThat(techMapFixture.getById(UserRole.ADMIN, source.getId(), child.getId()))
                .usingRecursiveComparison().isEqualTo(source);
    }

    private TechnologicalMapResponse createSource(String prefix) {
        TechnologicalMapResponse source = techMapFixture
                .createIsolatedProductionTechMap(UserRole.ADMIN, child.getId(), prefix).getTechMap();
        createdMapIds.add(source.getId());
        return source;
    }

    private void assertRootActionHint(TechnologicalMapResponse source, String action) {
        TechnologicalMapsListPage list = openRootWorkspace(source);
        List<String> mutations = new ArrayList<>();
        Consumer<Request> listener = request -> {
            if (request.url().contains("/api/v1/technological-maps")
                    && Set.of("POST", "PUT", "PATCH", "DELETE").contains(request.method())) {
                mutations.add(request.method() + " " + URI.create(request.url()).getPath());
            }
        };
        page.onRequest(listener);
        try {
            Response response = page.waitForResponse(
                    r -> "GET".equals(r.request().method())
                            && URI.create(r.url()).getPath().endsWith("/api/v1/technological-maps/" + source.getId()),
                    () -> list.clickTechMapAction(source.getName(), action));
            assertThat(response.status()).as("Map cannot load in the parent workspace").isBetween(400, 499);
            assertThat(list.waitForLocationHint(child.getName()))
                    .contains("Для вибраної Технологічної карти виберіть одну з наступних локацій: " + child.getName());
            page.waitForURL(url -> URI.create(url).getPath().equals(TechnologicalMapsListPage.PATH));
            list.waitForLoaded();
            assertSelectedWorkspace(true);
            assertThat(mutations).as("Unavailable action must not mutate tech maps").isEmpty();
        } finally {
            page.offRequest(listener);
        }
    }

    private TechnologicalMapsListPage openSuggestedLocationAfterHint(TechnologicalMapResponse source, String action) {
        assertRootActionHint(source, action);
        new AppSidebarPage(page).selectWorkspaceByName(child.getName());
        assertSelectedWorkspace(false);
        return new TechnologicalMapsListPage(page)
                .filterByProduct(source.getOutput().getFirst().getResource().getName());
    }

    private TechnologicalMapsListPage openRootWorkspace(TechnologicalMapResponse source) {
        // Both selections use the UI, independent of per-user localStorage key conventions.
        page.navigate(ConfigProvider.getBaseUrl() + TechnologicalMapsListPage.PATH);
        TechnologicalMapsListPage list = new TechnologicalMapsListPage(page).waitForLoaded();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        sidebar.selectWorkspaceByName(child.getName());
        assertSelectedWorkspace(false);
        sidebar.selectWorkspaceByName(root.getName().trim());
        assertSelectedWorkspace(true);
        // A unique output resource avoids pagination and collisions with existing maps.
        list.filterByProduct(source.getOutput().getFirst().getResource().getName());
        assertThat(list.isTechMapNameVisible(source.getName())).isTrue();
        return list;
    }

    private void assertSelectedWorkspace(boolean selectRoot) {
        String expectedName = (selectRoot ? root.getName() : child.getName()).trim();
        page.waitForCondition(() -> new AppSidebarPage(page).getSelectedLocationName().equals(expectedName));
    }

    private TechnologicalMapResponse save(String method, String path) {
        Response response = page.waitForResponse(
                r -> method.equals(r.request().method()) && URI.create(r.url()).getPath().endsWith(path),
                () -> page.getByRole(AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Зберегти").setExact(true)).click());
        assertThat(response.status()).as("Save tech map through UI").isBetween(200, 299);
        // Register the actual response ID before subsequent assertions, including GET verification.
        long id = io.restassured.path.json.JsonPath.from(response.text()).getLong("id");
        createdMapIds.add(id);
        page.waitForURL(url -> !url.contains("/technological-maps/update/")
                && !url.contains("/technological-maps/create"));
        return techMapFixture.getById(UserRole.ADMIN, id, child.getId());
    }

    private void assertStorageBinding(TechnologicalMapResponse map) {
        assertThat(map.getStorages()).extracting(SimpleEntityResponse::getId).containsExactly(child.getId());
    }

    @AfterClass(alwaysRun = true)
    public void cleanupWorkspaceMaps() {
        try {
            if (child != null) {
                for (Long id : createdMapIds) {
                    // Structural editing already deactivates the previous version.
                    var response = techMapFixture.deactivateTechMap(UserRole.ADMIN, id, child.getId());
                    assertThat(response.statusCode()).as("Cleanup tech map %s", id).isIn(200, 204, 404);
                }
            }
        } finally {
            try {
                if (root != null && originalRootMode != null) {
                    techMapFixture.setMode(root.getId(), originalRootMode);
                }
            } finally {
                if (storageFixture != null) {
                    storageFixture.deactivateTrackedStorages(UserRole.ADMIN);
                }
            }
        }
    }
}
