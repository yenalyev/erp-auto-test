package com.erp.tests.functional.storage;

import com.erp.enums.UserRole;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.UserMeResponse;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ops preflight for {@link StorageVisibilityTest}: OWNER_2 JWT must contain
 * exactly {@code owner2.storage.id}. Extra {@code var_business_unit_id(_ro)::*}
 * claims cause class-level SkipException for TC-STR-REG-020…052.
 */
@Slf4j
@Epic("Master Data")
@Feature("Storages")
@Story("OWNER_2 business-unit scope preflight")
public class Owner2ScopePreflightTest extends StorageApiTestBase {

    private UserFixture userFixture;
    private Long owner2StorageId;

    @BeforeClass(alwaysRun = true)
    public void setupPreflight() {
        owner2StorageId = ConfigProvider.getOwner2StorageId();
        userFixture = new UserFixture(testContext, apiExecutor);
    }

    @Test(priority = 1)
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Preflight: GET /users/me as OWNER_2 → allowedStorageIds must equal {owner2.storage.id}.
            If this fails, fix Keycloak / Users admin (remove extra var_business_unit_id(_ro)),
            then re-run StorageVisibilityTest (TC-STR-REG-020…052).
            """)
    public void owner2AllowedStorageIdsMustMatchConfiguredStorage() {
        UserMeResponse me = userFixture.getMe(UserRole.OWNER_2);
        Set<Long> allowed = me.getAllowedStorageIds() == null
                ? Set.of()
                : new HashSet<>(me.getAllowedStorageIds());

        Allure.addAttachment(
                "OWNER_2 scope",
                "text/plain",
                String.format(
                        "user=%s%nowner2.storage.id=%d%nallowedStorageIds=%s%n",
                        UserRole.OWNER_2.getUsername(),
                        owner2StorageId,
                        allowed));
        log.info("OWNER_2 ({}) allowedStorageIds={} (expected {{{}}})",
                UserRole.OWNER_2.getUsername(), allowed, owner2StorageId);

        assertThat(allowed)
                .as("OWNER_2 (%s) allowedStorageIds must be exactly {owner2.storage.id=%d}. "
                                + "Remove extra var_business_unit_id(_ro) in Keycloak / Users admin.",
                        UserRole.OWNER_2.getUsername(),
                        owner2StorageId)
                .isEqualTo(Set.of(owner2StorageId));
    }
}
