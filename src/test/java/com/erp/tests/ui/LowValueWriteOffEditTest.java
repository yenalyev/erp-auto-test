package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.LowValueWriteOffFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.models.request.ResourceBatchSelectionRequest;
import com.erp.pages.LowValueWriteOffPage;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.*;
import org.testng.annotations.*;

import java.util.List;

import static com.erp.fixtures.LowValueWriteOffFixture.asset;
import static org.assertj.core.api.Assertions.assertThat;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Epic("Inventory")
@Feature("REQ-MSHP — Редагування списання МШП")
public class LowValueWriteOffEditTest extends BaseUITest {
    private LowValueWriteOffFixture fixture;

    @BeforeClass(alwaysRun = true)
    @Override public void baseTestClassSetup() {
        super.baseTestClassSetup();
        fixture = new LowValueWriteOffFixture(testContext, apiExecutor);
        fixture.prepare(10);
        // Manual batch-selection permission is available only on production-capable locations.
        new StorageFixture(testContext, apiExecutor).ensureProductionFeature(UserRole.ADMIN, fixture.a.getId());
    }

    @AfterClass(alwaysRun = true)
    public void cleanupEditData() { if (fixture != null) fixture.cleanup(); }

    @DataProvider public Object[][] validEditAmounts() { return new Object[][]{{4}, {6}, {10}}; }

    @Test(dataProvider = "validEditAmounts") @TestCaseId("TC-MSHP-API-010")
    @Description("FIFO API control: write off 6 of 10, then replace debit by 4/6/10. Current stock + own debit is available; delete restores all 10.")
    public void fifoApiAcceptsValidEdit(int amount) {
        long id = createSix(false);
        try {
            var response = fixture.update(UserRole.ADMIN, id,
                    fixture.body(fixture.a.getId(), List.of(asset(fixture.second.getId(), amount))));
            assertThat(response.statusCode()).as("Valid FIFO update: %s", response.asString()).isEqualTo(200);
            fixture.assertStock(fixture.a.getId(), fixture.second.getId(), 10 - amount);
            assertThat(fixture.get(UserRole.ADMIN, id, fixture.a.getId()).jsonPath().getDouble("assets[0].amount"))
                    .isEqualTo(amount);
        } finally { fixture.remove(id, fixture.a.getId()); }
        fixture.assertStock(fixture.a.getId(), fixture.second.getId(), 10);
    }

    @Test(dataProvider = "validEditAmounts") @TestCaseId("TC-MSHP-UI-011")
    @Description("Regression for manual bug: FIFO edit shows available 10 but rejects valid 4/6/10. Re-enter the total through the actual UI and require enabled confirmation, real PUT and exact stock. No workaround clears hidden batches.")
    public void fifoUiAcceptsValidEdit(int amount) {
        long id = createSix(false);
        try {
            LowValueWriteOffPage edit = openEdit(id);
            Locator quantity = page.getByPlaceholder("Кількість").first();
            assertThat(quantity).isEnabled();
            assertThat(quantity).hasValue("6");
            assertThat(page.getByPlaceholder("Оберіть ресурс...").first()).hasValue(
                    fixture.second.getName() + " - доступно: 10" + fixture.second.getUnit().getShortName());
            quantity.fill("");
            quantity.fill(String.valueOf(amount));
            // Preserve diagnostic evidence before asserting expected product behavior.
            attachScreenshot("TC-MSHP-UI-011 — FIFO " + amount + " із доступних 10");
            fixture.assertStock(fixture.a.getId(), fixture.second.getId(), 4);
            assertThat(edit.submit()).isEnabled();
            submitEdit(id);
            fixture.assertStock(fixture.a.getId(), fixture.second.getId(), 10 - amount);
            assertThat(fixture.get(UserRole.ADMIN, id, fixture.a.getId()).jsonPath().getDouble("assets[0].amount"))
                    .isEqualTo(amount);
        } finally { fixture.remove(id, fixture.a.getId()); }
        fixture.assertStock(fixture.a.getId(), fixture.second.getId(), 10);
    }

    @Test @TestCaseId("TC-MSHP-UI-012")
    @Description("Positive control: with explicit batch selection enabled, the edit offers the original batch plus its own 6, accepts 4, sends PUT and restores stock on cancellation.")
    public void batchTrackedUiAcceptsValidEdit() {
        long id = createSix(true);
        try {
            var edit = openEdit(id);
            assertThat(page.getByPlaceholder("Кількість").first()).isDisabled();
            Locator batchQuantity = page.getByPlaceholder("Кількість").last();
            assertThat(batchQuantity).isEnabled();
            batchQuantity.fill("4");
            assertThat(edit.submit()).isEnabled();
            submitEdit(id);
            fixture.assertStock(fixture.a.getId(), fixture.second.getId(), 6);
        } finally {
            fixture.remove(id, fixture.a.getId());
            setBatchSelection(false);
        }
        fixture.assertStock(fixture.a.getId(), fixture.second.getId(), 10);
    }

    private long createSix(boolean tracked) {
        setBatchSelection(tracked);
        fixture.assertStock(fixture.a.getId(), fixture.second.getId(), 10);
        var item = asset(fixture.second.getId(), 6);
        if (tracked) item.put("batchId", fixture.batches(fixture.a.getId(), fixture.second.getId()).getFirst().getBatchUuid().toString());
        long id = fixture.create(UserRole.ADMIN, fixture.body(fixture.a.getId(), List.of(item)));
        fixture.assertStock(fixture.a.getId(), fixture.second.getId(), 4);
        return id;
    }

    private void setBatchSelection(boolean enabled) {
        var response = apiExecutor.execute(ApiEndpointDefinition.RESOURCE_BATCH_SELECTION_PUT, UserRole.ADMIN,
                new ResourceBatchSelectionRequest(enabled), fixture.a.getId(), fixture.second.getId());
        assertThat(response.statusCode()).as("Own batch-selection setting").isIn(200, 204);
    }

    private LowValueWriteOffPage openEdit(long id) {
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
        page.navigate(ConfigProvider.getBaseUrl());
        page.evaluate("localStorage.clear()");
        page.evaluate("([u,s]) => {localStorage.setItem('selectedStorageId',s);localStorage.setItem('selectedStorageId:'+u,s)}",
                List.of(UserRole.ADMIN.getUsername(), String.valueOf(fixture.a.getId())));
        var edit = new LowValueWriteOffPage(page);
        edit.open(LowValueWriteOffPage.FORM + "/" + id + "?storageId=" + fixture.a.getId());
        assertThat(edit.submit()).isVisible();
        return edit;
    }

    private void submitEdit(long id) {
        var response = page.waitForResponse(r -> r.request().method().equals("PUT")
                        && r.url().contains("/low-value-resources/" + id),
                () -> page.getByTestId("low-value-write-off-submit").click());
        assertThat(response.status()).as("Actual edit PUT: %s", response.text()).isEqualTo(200);
        page.waitForURL("**/inventory/low-value-write-offs");
    }
}
