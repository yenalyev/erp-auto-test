package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.TechMapCukrarniaEditFixture;
import com.erp.models.response.SimpleEntityResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.TechnologicalMapFormPage;
import com.erp.pages.TechnologicalMapsListPage;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.*;
import org.testng.annotations.*;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Technological Maps")
@Feature("REQ-MFG-CUK-EDIT — Редагування під Цукрарнею")
public class TechMapCukrarniaEditUiTest extends BaseUITest {
    private TechMapCukrarniaEditFixture data;

    @BeforeMethod(alwaysRun = true)
    public void arrangeCukrarnia() {
        data = new TechMapCukrarniaEditFixture(testContext, apiExecutor);
        data.prepare(true);
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupCukrarnia() { if (data != null) data.cleanup(); }

    @Test
    @TestCaseId("TC-TM-CUK-UI-001")
    @Description("Вибрати справжню Цукрарню через sidebar; у рядку карти внучатої локації натиснути Редагувати; змінити назву й зберегти. Робочий простір, id, версія, склад і початкові локації зберігаються.")
    public void renameWithoutLeavingCukrarnia() {
        var form = openFromWorkspace(true, "TC-TM-CUK-UI-001");
        String name = data.marker + "-ui-renamed";
        form.fillName(name);
        form.attachScreenshot("TC-TM-CUK-UI-001 — назва перед збереженням");
        var updated = save();
        assertThat(updated.getName()).isEqualTo(name);
        assertThat(updated).usingRecursiveComparison().ignoringFields("name").isEqualTo(data.source);
        assertSelected(data.root.getName());
        data.assertSiblingUnchanged();
    }

    @Test
    @TestCaseId("TC-TM-CUK-UI-002")
    @Description("Під Цукрарнею змінити норму input і зберегти реальним PUT форми: storageIds залишаються початковими. Нова версія тієї самої групи, початкова карта в історії, сусідня незмінна.")
    public void structuralEditWithoutLeavingCukrarnia() {
        var form = openFromWorkspace(true, "TC-TM-CUK-UI-002");
        form.fillInputAmount(0, "3");
        form.attachScreenshot("TC-TM-CUK-UI-002 — нова норма");
        var updated = save();
        assertThat(updated.getId()).isNotEqualTo(data.source.getId());
        assertThat(updated.getVersion()).isEqualTo(data.source.getVersion() + 1);
        assertThat(updated.getGroupId()).isEqualTo(data.source.getGroupId());
        assertThat(updated.getInput().getFirst().getAmount()).isEqualTo(3.0);
        assertThat(updated.getOutput()).usingRecursiveComparison().isEqualTo(data.source.getOutput());
        assertThat(TechnologicalMapDataFactory.fromExisting(updated).build().getGroups())
                .usingRecursiveComparison().ignoringFieldsMatchingRegexes("id", ".*\\.id")
                .isEqualTo(TechnologicalMapDataFactory.fromExisting(data.source).build().getGroups());
        assertThat(data.maps.getVersionsByGroupId(UserRole.ADMIN, updated.getGroupId(), data.grandchild.getId()))
                .extracting(TechnologicalMapResponse::getId).containsExactly(updated.getId(), data.source.getId());
        assertSelected(data.root.getName());
        data.assertSiblingUnchanged();
    }

    @Test
    @TestCaseId("TC-TM-CUK-UI-003")
    @Description("Під Цукрарнею відкрити редагування, змінити назву/норму та Скасувати. Немає POST/PUT/PATCH/DELETE техкарт, змін оригіналу, версій або сусідньої карти; Цукрарня лишається вибраною.")
    public void cancelHasNoSideEffects() {
        var form = openFromWorkspace(true, "TC-TM-CUK-UI-003");
        List<String> mutations = new ArrayList<>();
        Consumer<Request> listener = request -> {
            if (request.url().contains("/api/v1/technological-maps")
                    && Set.of("POST", "PUT", "PATCH", "DELETE").contains(request.method()))
                mutations.add(request.method() + " " + URI.create(request.url()).getPath());
        };
        page.onRequest(listener);
        try {
            form.fillName(data.marker + "-cancelled").fillInputAmount(0, "5");
            form.attachScreenshot("TC-TM-CUK-UI-003 — незбережені зміни");
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Скасувати").setExact(true)).click();
            page.waitForURL(url -> URI.create(url).getPath().equals(TechnologicalMapsListPage.PATH));
            new TechnologicalMapsListPage(page).waitForLoaded();
            assertThat(mutations).isEmpty();
        } finally { page.offRequest(listener); }
        data.assertUnchanged(data.source);
        data.assertSiblingUnchanged();
        assertSelected(data.root.getName());
    }

    @Test
    @TestCaseId("TC-TM-CUK-UI-004")
    @Description("Під Цукрарнею відкрити форму й ввести нульову норму input. Зберегти показує помилку, форма залишається відкритою, оригінал та історія незмінні.")
    public void invalidAmountKeepsFormAndOriginal() {
        var form = openFromWorkspace(true, "TC-TM-CUK-UI-004");
        form.fillInputAmount(0, "0");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Зберегти").setExact(true)).click();
        form.waitForErrorVisible();
        form.attachScreenshot("TC-TM-CUK-UI-004 — помилка нульової норми");
        assertThat(form.getErrorText()).isNotBlank();
        assertThat(URI.create(page.url()).getPath()).isEqualTo("/technological-maps/update/" + data.source.getId());
        data.assertUnchanged(data.source);
        data.assertSiblingUnchanged();
    }

    @Test
    @TestCaseId("TC-TM-CUK-UI-005")
    @Description("Контроль регресії: вибрати власну локацію карти через sidebar, відредагувати назву; зберегти без зміни версії/прив'язки. Цей сценарій незалежний від завантаження під Цукрарнею.")
    public void ownLocationEditingStillWorks() {
        var form = openFromWorkspace(false, "TC-TM-CUK-UI-005");
        String name = data.marker + "-leaf-renamed";
        form.fillName(name).attachScreenshot("TC-TM-CUK-UI-005 — редагування у власній локації");
        var updated = save();
        assertThat(updated.getName()).isEqualTo(name);
        assertThat(updated).usingRecursiveComparison().ignoringFields("name").isEqualTo(data.source);
        data.assertSiblingUnchanged();
        assertSelected(data.grandchild.getName());
    }

    private TechnologicalMapFormPage openFromWorkspace(boolean root, String testId) {
        Set<Request> pending = new java.util.HashSet<>();
        Consumer<Request> started = request -> { if (isListRequest(request)) pending.add(request); };
        Consumer<Request> finished = pending::remove;
        page.onRequest(started);
        page.onRequestFinished(finished);
        page.onRequestFailed(finished);
        try {
            page.waitForResponse(r -> isListRequest(r.request()),
                    () -> page.navigate(ConfigProvider.getBaseUrl() + TechnologicalMapsListPage.PATH)).finished();
            var list = new TechnologicalMapsListPage(page).waitForLoaded();
            String name = root ? data.root.getName() : data.grandchild.getName();
            long storageId = root ? data.root.getId() : data.grandchild.getId();
            var sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
            if (!sidebar.getSelectedLocationName().equals(name.trim())) {
                page.waitForResponse(r -> isListRequest(r.request()) && hasStorage(r.url(), storageId),
                        () -> sidebar.selectWorkspaceByName(name.trim())).finished();
            }
            assertSelected(name);
            // Finish outstanding initial/workspace loads before issuing the filter request.
            page.waitForCondition(pending::isEmpty);
            String product = data.source.getOutput().getFirst().getResource().getName();
            Response filtered = page.waitForResponse(r -> isListRequest(r.request())
                            && hasStorage(r.url(), storageId) && queryPairs(r.url()).stream()
                            .anyMatch(pair -> pair[0].equals("productTerm") && pair[1].equals(product)),
                    () -> list.filterByProduct(product));
            assertThat(filtered.status()).isEqualTo(200);
            filtered.finished();
            page.waitForCondition(pending::isEmpty);
            com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(page.getByText(data.source.getName(),
                    new Page.GetByTextOptions().setExact(true))).isVisible(
                    new com.microsoft.playwright.assertions.LocatorAssertions.IsVisibleOptions()
                            .setTimeout(ConfigProvider.getUiTimeoutSeconds() * 1000));
            list.attachScreenshot(testId + " — карта в обраному робочому просторі");
            Response response = page.waitForResponse(r -> r.request().method().equals("GET")
                            && URI.create(r.url()).getPath().endsWith("/api/v1/technological-maps/" + data.source.getId()),
                    () -> list.clickTechMapAction(data.source.getName(), "Редагувати"));
            attachScreenshot(testId + " — результат завантаження форми");
            assertThat(response.status()).as("Edit form GET in workspace %s: %s", name, response.text()).isEqualTo(200);
            var form = new TechnologicalMapFormPage(page).waitForUpdateLoaded();
            assertSelected(name);
            assertThat(page.getByPlaceholder("Наприклад: ПТМ-3 (з ТГА)").inputValue()).isEqualTo(data.source.getName());
            return form;
        } finally {
            page.offRequest(started);
            page.offRequestFinished(finished);
            page.offRequestFailed(finished);
        }
    }

    private boolean isListRequest(Request request) {
        return request.method().equals("GET")
                && URI.create(request.url()).getPath().endsWith("/api/v1/technological-maps");
    }

    private boolean hasStorage(String url, long storageId) {
        return queryPairs(url).stream().anyMatch(pair -> pair[0].startsWith("storageIds")
                && pair[1].equals(String.valueOf(storageId)));
    }

    private List<String[]> queryPairs(String url) {
        String query = URI.create(url).getRawQuery();
        if (query == null) return List.of();
        return java.util.Arrays.stream(query.split("&")).map(part -> {
            String[] pair = part.split("=", 2);
            return new String[]{URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : ""};
        }).toList();
    }

    private TechnologicalMapResponse save() {
        Response response = page.waitForResponse(r -> r.request().method().equals("PUT")
                        && URI.create(r.url()).getPath().endsWith("/api/v1/technological-maps/" + data.source.getId()),
                () -> page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Зберегти").setExact(true)).click());
        if (response.status() == 200) data.track(io.restassured.path.json.JsonPath.from(response.text()).getLong("id"),
                data.grandchild.getId());
        assertThat(response.status()).as(response.text()).isEqualTo(200);
        var body = io.restassured.path.json.JsonPath.from(response.request().postData());
        assertThat(body.getList("storageIds", Long.class)).containsExactly(data.grandchild.getId());
        for (java.util.Map<String, Object> group : body.<java.util.Map<String, Object>>getList("groups")) {
            assertThat(group).doesNotContainKey("id");
            for (var option : (java.util.List<?>) group.get("alternativeResources"))
                assertThat((java.util.Map<String, Object>) option).doesNotContainKey("id");
        }
        long id = io.restassured.path.json.JsonPath.from(response.text()).getLong("id");
        page.waitForURL(url -> URI.create(url).getPath().equals(TechnologicalMapsListPage.PATH));
        var result = data.maps.getById(UserRole.ADMIN, id, data.grandchild.getId());
        assertThat(result.getStorages()).extracting(SimpleEntityResponse::getId).containsExactly(data.grandchild.getId());
        return result;
    }

    private void assertSelected(String name) {
        page.waitForCondition(() -> new AppSidebarPage(page).getSelectedLocationName().equals(name.trim()));
    }
}
