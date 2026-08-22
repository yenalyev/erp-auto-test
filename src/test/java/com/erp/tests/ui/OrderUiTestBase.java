package com.erp.tests.ui;

import com.erp.enums.UserRole;
import com.erp.fixtures.OrderFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import java.util.List;
import java.util.Map;

@Slf4j
abstract class OrderUiTestBase extends BaseUITest {

    /** Підрозділ 3bat — create/see own orders. */
    protected static final UserRole REQUESTER = UserRole.UNIT_ANALYST;
    /** alkatras — other unit; must not see 3bat orders. */
    protected static final UserRole OUTSIDER = UserRole.OWNER_1;
    /** Administrator — order::manage lifecycle (take-to-work, book, send). */
    protected static final UserRole MANAGER = UserRole.ADMIN;
    /** Owner of gathering storage — prepare bookings on gathering side. */
    protected static final UserRole GATHERER = UserRole.ORDER_GATHERER;

    protected OrderFixture orderFixture;
    protected RelocationFixture relocationFixture;
    protected StorageFixture storageFixture;

    protected long requesterStorageId;
    protected long gatheringStorageId;
    protected Long resourceId;
    protected String resourceName;
    protected String requesterStorageName;
    protected String gatheringStorageName;

    /** Need JDBC to upsert {@code order_availability_root_storage} when configured. */
    @Override
    protected boolean shouldInitializeDatabase() {
        return ConfigProvider.useDatabase() || ConfigProvider.getOrderAvailabilityRootStorageId() > 0;
    }

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        orderFixture = new OrderFixture(testContext, apiExecutor);
        relocationFixture = orderFixture.relocation();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        orderFixture.ensureAvailabilityRootConfig(getDbHelper());
        orderFixture.prepareContext();

        requesterStorageId = testContext.get(ContextKey.ORDER_REQUESTER_STORAGE_ID);
        gatheringStorageId = ConfigProvider.getOrderGatheringStorageId();
        resourceId = testContext.get(ContextKey.ORDER_RESOURCE_ID);
        List<ResourceResponse> resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        resourceName = resources.stream()
                .filter(r -> resourceId.equals(r.getId()))
                .map(ResourceResponse::getName)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Resource name not found for id " + resourceId));

        requesterStorageName = storageFixture.getNames(UserRole.ADMIN, true, null, requesterStorageId)
                .getFirst()
                .getName();
        gatheringStorageName = storageFixture.getNames(UserRole.ADMIN, true, null, gatheringStorageId)
                .getFirst()
                .getName();
    }

    @BeforeMethod(alwaysRun = true)
    public void ensureGatheringStock() {
        relocationFixture.ensureStock(gatheringStorageId, resourceId, 200.0);
    }

    protected void injectRoleSession(UserRole role, long selectedStorageId) {
        injectRoleSession(role, Long.valueOf(selectedStorageId));
    }

    protected void injectRoleSession(UserRole role, Long selectedStorageId) {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(role.getUsername(), role.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        if (selectedStorageId == null) {
            browserContext.addInitScript("localStorage.setItem('selectedStorageId', 'all');");
        } else {
            browserContext.addInitScript(
                    "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
        }
    }

    protected void reopenPageWithSession(UserRole role, long selectedStorageId) {
        reopenPageWithSession(role, Long.valueOf(selectedStorageId));
    }

    protected void reopenPageWithSession(UserRole role, Long selectedStorageId) {
        if (page != null) {
            try {
                page.close();
            } catch (Exception e) {
                log.debug("Could not close page before session reinject: {}", e.getMessage());
            }
        }
        injectRoleSession(role, selectedStorageId);
        page = browserContext.newPage();
        int timeoutMs = ConfigProvider.getUiTimeoutSeconds() * 1000;
        page.setDefaultTimeout(timeoutMs);
        page.setDefaultNavigationTimeout(timeoutMs);
    }

    /** 3bat session on requester UNIT — create / view own orders. */
    protected void loginAsOwner() {
        reopenPageWithSession(REQUESTER, requesterStorageId);
    }

    /** Admin session on requester storage — manage lifecycle (take-to-work, booking, send). */
    protected void loginAsAdmin() {
        reopenPageWithSession(MANAGER, requesterStorageId);
    }

    /** Admin session in «Всі локації» workspace. */
    protected void loginAsAdminAllLocations() {
        reopenPageWithSession(MANAGER, null);
    }

    protected void syncGatheringFromContext() {
        Long resolved = testContext.get(ContextKey.ORDER_GATHERING_STORAGE_ID);
        if (resolved == null || resolved == gatheringStorageId) {
            return;
        }
        gatheringStorageId = resolved;
        gatheringStorageName = storageFixture.getNames(UserRole.ADMIN, true, null, gatheringStorageId)
                .getFirst()
                .getName();
        relocationFixture.ensureStock(gatheringStorageId, resourceId, 200.0);
    }

    protected OrderResponse prepareManagedInProgressUi() {
        OrderResponse order = orderFixture.prepareInProgressWithGathering(REQUESTER, MANAGER);
        syncGatheringFromContext();
        return order;
    }
}
