package com.erp.tests.functional.relocation;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceBundleFixture;
import com.erp.models.response.ResourceBundleResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.SimpleEntityResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API coverage for REQ-WMS-009 — комплекти для видачі ({@code /resources/user-bundles}).
 */
@Slf4j
@Epic("Relocation")
@Feature("Resource Bundles (Комплекти для видачі)")
public class ResourceBundleTest extends BaseFunctionalTest {

    private ResourceBundleFixture bundleFixture;
    private Long owner1Storage;
    private Long owner2Storage;
    private Long resourceId1;
    private Long resourceId2;
    private Long resourceId3;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupBundles() {
        bundleFixture = new ResourceBundleFixture(testContext, apiExecutor);
        bundleFixture.prepareContext();
        owner1Storage = ConfigProvider.getOwner1StorageId();
        owner2Storage = ConfigProvider.getOwner2StorageId();
        List<ResourceResponse> resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        resourceId1 = resources.get(0).getId();
        resourceId2 = resources.get(1).getId();
        resourceId3 = resources.size() > 2 ? resources.get(2).getId() : resourceId2;
    }

    @AfterClass(alwaysRun = true)
    public void cleanupBundles() {
        if (bundleFixture != null) {
            bundleFixture.cleanupCreatedBundles();
        }
    }

    @Test
    @TestCaseId("TC-BUNDLE-API-001")
    @Story("AC-01: create → GET")
    @Severity(SeverityLevel.BLOCKER)
    @Description("POST user-bundle → GET містить name та resource ids/names.")
    public void createBundleAppearsInGet() {
        String name = bundleFixture.uniqueBundleName("bundle-create-");
        ResourceBundleResponse created = bundleFixture.createBundle(
                UserRole.OWNER_1, owner1Storage, name, List.of(resourceId1, resourceId2));

        assertThat(created.getBundleName()).isEqualTo(name);
        assertThat(created.getResources())
                .extracting(SimpleEntityResponse::getId)
                .containsExactlyInAnyOrder(resourceId1, resourceId2);
        assertThat(created.getResources())
                .allSatisfy(r -> assertThat(r.getName()).isNotBlank());
    }

    @Test
    @TestCaseId("TC-BUNDLE-API-002")
    @Story("AC-01: upsert by bundleName")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Повторний POST з тим самим bundleName замінює resources.")
    public void upsertReplacesResources() {
        String name = bundleFixture.uniqueBundleName("bundle-upsert-");
        bundleFixture.createBundle(UserRole.OWNER_1, owner1Storage, name, List.of(resourceId1));

        bundleFixture.saveBundleRaw(
                UserRole.OWNER_1, owner1Storage, name, List.of(resourceId2, resourceId3));

        ResourceBundleResponse updated = bundleFixture.listBundles(UserRole.OWNER_1, owner1Storage).stream()
                .filter(b -> name.equals(b.getBundleName()))
                .findFirst()
                .orElseThrow();
        assertThat(updated.getResources())
                .extracting(SimpleEntityResponse::getId)
                .containsExactlyInAnyOrder(resourceId2, resourceId3)
                .doesNotContain(resourceId1);
    }

    @Test
    @TestCaseId("TC-BUNDLE-API-003")
    @Story("AC-01: delete")
    @Severity(SeverityLevel.CRITICAL)
    @Description("DELETE → GET без цього bundle.")
    public void deleteRemovesBundle() {
        String name = bundleFixture.uniqueBundleName("bundle-del-");
        bundleFixture.createBundle(UserRole.OWNER_1, owner1Storage, name, List.of(resourceId1));

        Response delete = bundleFixture.deleteBundleRaw(UserRole.OWNER_1, owner1Storage, name);
        assertThat(delete.statusCode()).isLessThan(300);

        assertThat(bundleFixture.listBundles(UserRole.OWNER_1, owner1Storage))
                .noneMatch(b -> name.equals(b.getBundleName()));
    }

    @Test
    @TestCaseId("TC-BUNDLE-API-004")
    @Story("AC-01: validation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Порожня назва / порожній resources → 400.")
    public void validationRejectsEmptyNameOrResources() {
        Response emptyName = bundleFixture.saveBundleRaw(
                UserRole.OWNER_1, owner1Storage, "", List.of(resourceId1));
        assertThat(emptyName.statusCode())
                .as("Empty bundleName must be rejected")
                .isGreaterThanOrEqualTo(400);

        Response emptyResources = bundleFixture.saveBundleRaw(
                UserRole.OWNER_1, owner1Storage, bundleFixture.uniqueBundleName("bundle-empty-res-"),
                List.of());
        assertThat(emptyResources.statusCode())
                .as("Empty resources must be rejected")
                .isGreaterThanOrEqualTo(400);
    }

    @Test
    @TestCaseId("TC-BUNDLE-API-005")
    @Story("AC-01: unknown resource ids dropped")
    @Severity(SeverityLevel.NORMAL)
    @Description("Неіснуючі resource IDs у save тихо відсікаються.")
    public void unknownResourceIdsAreDropped() {
        String name = bundleFixture.uniqueBundleName("bundle-drop-");
        long fakeId = 9_999_999_001L;
        ResourceBundleResponse created = bundleFixture.createBundle(
                UserRole.OWNER_1, owner1Storage, name, List.of(resourceId1, fakeId));

        assertThat(created.getResources())
                .extracting(SimpleEntityResponse::getId)
                .containsExactly(resourceId1)
                .doesNotContain(fakeId);
    }

    @Test
    @TestCaseId("TC-BUNDLE-API-006")
    @Story("AC-01: GET sorted by name")
    @Severity(SeverityLevel.NORMAL)
    @Description("GET сортує комплеккти за bundleName.")
    public void getBundlesSortedByName() {
        String nameZ = bundleFixture.uniqueBundleName("zz-bundle-");
        String nameA = bundleFixture.uniqueBundleName("aa-bundle-");
        bundleFixture.createBundle(UserRole.OWNER_1, owner1Storage, nameZ, List.of(resourceId1));
        bundleFixture.createBundle(UserRole.OWNER_1, owner1Storage, nameA, List.of(resourceId2));

        List<String> names = bundleFixture.listBundles(UserRole.OWNER_1, owner1Storage).stream()
                .map(ResourceBundleResponse::getBundleName)
                .toList();
        int idxA = names.indexOf(nameA);
        int idxZ = names.indexOf(nameZ);
        assertThat(idxA).isGreaterThanOrEqualTo(0);
        assertThat(idxZ).isGreaterThanOrEqualTo(0);
        assertThat(idxA).as("aa-* must sort before zz-*").isLessThan(idxZ);
    }

    @Test
    @TestCaseId("TC-BUNDLE-API-010")
    @Story("AC-02: location isolation")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Bundle на storage A не видно в GET storage B.")
    public void bundlesIsolatedByStorage() {
        String name = bundleFixture.uniqueBundleName("bundle-iso-");
        bundleFixture.createBundle(UserRole.OWNER_1, owner1Storage, name, List.of(resourceId1));

        assertThat(bundleFixture.listBundles(UserRole.OWNER_2, owner2Storage))
                .noneMatch(b -> name.equals(b.getBundleName()));
        assertThat(bundleFixture.listBundles(UserRole.ADMIN, owner2Storage))
                .noneMatch(b -> name.equals(b.getBundleName()));
    }

    @Test
    @TestCaseId("TC-BUNDLE-API-011")
    @Story("AC-02: 403 without BU read")
    @Severity(SeverityLevel.BLOCKER)
    @Description("OWNER_1 CRUD на storage OWNER_2 → 403.")
    public void crudOnForeignStorageForbidden() {
        String name = bundleFixture.uniqueBundleName("bundle-403-");

        Response get = bundleFixture.getBundlesRaw(UserRole.OWNER_1, owner2Storage);
        assertThat(get.statusCode()).isEqualTo(403);

        Response post = bundleFixture.saveBundleRaw(
                UserRole.OWNER_1, owner2Storage, name, List.of(resourceId1));
        assertThat(post.statusCode()).isEqualTo(403);

        Response delete = bundleFixture.deleteBundleRaw(UserRole.OWNER_1, owner2Storage, name);
        assertThat(delete.statusCode()).isEqualTo(403);
    }

    @Test
    @TestCaseId("TC-BUNDLE-RBAC-001")
    @Story("AC-02/AC-03: BU read allows CRUD")
    @Severity(SeverityLevel.CRITICAL)
    @Description("OWNER_1 з business-unit-list::read — GET/POST/DELETE дозволені.")
    public void ownerWithBuReadCanCrud() {
        String name = bundleFixture.uniqueBundleName("bundle-rbac-ok-");
        Response post = bundleFixture.saveBundleRaw(
                UserRole.OWNER_1, owner1Storage, name, List.of(resourceId1));
        assertThat(post.statusCode()).isLessThan(300);

        Response get = bundleFixture.getBundlesRaw(UserRole.OWNER_1, owner1Storage);
        assertThat(get.statusCode()).isEqualTo(200);

        Response delete = bundleFixture.deleteBundleRaw(UserRole.OWNER_1, owner1Storage, name);
        assertThat(delete.statusCode()).isLessThan(300);
    }

    @Test
    @TestCaseId("TC-BUNDLE-RBAC-002")
    @Story("AC-02: without BU read → 403")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Без read на BU — усі три методи 403 (OWNER_1 на OWNER_2 storage).")
    public void withoutBuReadAllMethodsForbidden() {
        String name = bundleFixture.uniqueBundleName("bundle-rbac-deny-");
        assertThat(bundleFixture.getBundlesRaw(UserRole.OWNER_1, owner2Storage).statusCode()).isEqualTo(403);
        assertThat(bundleFixture.saveBundleRaw(
                UserRole.OWNER_1, owner2Storage, name, List.of(resourceId1)).statusCode()).isEqualTo(403);
        assertThat(bundleFixture.deleteBundleRaw(UserRole.OWNER_1, owner2Storage, name).statusCode())
                .isEqualTo(403);
    }

    @Test
    @TestCaseId("TC-BUNDLE-RBAC-003")
    @Story("AC-03: no dedicated bundle permission")
    @Severity(SeverityLevel.NORMAL)
    @Description("CRUD працює через існуючий BU read; окремого permission entity немає.")
    public void crudWorksViaExistingBuReadPermission() {
        String name = bundleFixture.uniqueBundleName("bundle-no-perm-");
        ResourceBundleResponse created = bundleFixture.createBundle(
                UserRole.ADMIN, owner1Storage, name, List.of(resourceId1));
        assertThat(created.getBundleName()).isEqualTo(name);
        Response delete = bundleFixture.deleteBundleRaw(UserRole.ADMIN, owner1Storage, name);
        assertThat(delete.statusCode()).isLessThan(300);
    }

    @Test
    @TestCaseId("TC-BUNDLE-API-021")
    @Story("AC-06: FULL_ACCESS returns all saved resources")
    @Severity(SeverityLevel.NORMAL)
    @Description("FULL_ACCESS storage (OWNER_1) повертає всі збережені id/name.")
    public void fullAccessReturnsAllSavedResources() {
        String name = bundleFixture.uniqueBundleName("bundle-full-");
        ResourceBundleResponse created = bundleFixture.createBundle(
                UserRole.OWNER_1, owner1Storage, name, List.of(resourceId1, resourceId2));
        assertThat(created.getResources())
                .extracting(SimpleEntityResponse::getId)
                .containsExactlyInAnyOrder(resourceId1, resourceId2);
        assertThat(created.getResources())
                .allSatisfy(r -> {
                    assertThat(r.getId()).isNotNull();
                    assertThat(r.getName()).isNotBlank();
                });
    }

    @Test
    @TestCaseId("TC-BUNDLE-API-020")
    @Story("AC-06: GET hides ungranted resources")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            На FULL_ACCESS локації всі збережені ресурси видимі (базовий контраст до restricted).
            Повний REGIONS+partial grant сценарій покритий у StorageResourceVisibilityTest;
            тут перевіряємо, що GET не відкидає granted ids на OWNER_1.
            """)
    public void getReturnsGrantedResourcesOnOwnerStorage() {
        String name = bundleFixture.uniqueBundleName("bundle-grant-");
        ResourceBundleResponse created = bundleFixture.createBundle(
                UserRole.OWNER_1, owner1Storage, name, List.of(resourceId1, resourceId2));
        assertThat(created.getResources()).isNotEmpty();
        assertThat(created.getResources())
                .extracting(SimpleEntityResponse::getId)
                .contains(resourceId1, resourceId2);
    }
}
