package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageRegionResourceResponse;
import com.erp.models.response.StorageRegionResponse;
import com.erp.models.response.StorageResponse;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Master Data")
@Feature("Storages")
@Story("System ALL RESOURCES region")
public class SystemAllResourcesRegionTest extends StorageApiTestBase {

    /**
     * Shared stem only — never use alone as autocomplete/page search on staging:
     * leftover {@code sys-all-res-*} from prior runs fill {@code size=50}/{@code size=500}.
     */
    private static final String RESOURCE_STEM = "sys-all-res-";

    private final Set<Long> systemRegionMembers = new LinkedHashSet<>();
    private ResourceFixture resourceFixture;

    @BeforeClass(alwaysRun = true)
    public void setupSystemRegionTests() {
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
    }

    @AfterMethod(alwaysRun = true)
    public void detachSystemRegionMembers() {
        if (systemRegionMembers.isEmpty()) {
            return;
        }
        StorageRegionResponse systemRegion = regionFixture.findSystemAllResourcesRegion();
        for (Long storageId : List.copyOf(systemRegionMembers)) {
            try {
                regionFixture.removeRegionMembers(systemRegion.getId(), storageId);
            } finally {
                systemRegionMembers.remove(storageId);
            }
        }
    }

    @Test(priority = 1)
    @TestCaseId("TC-STR-RES-019")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Перевіряє seed V71: один RESOURCES-регіон без recipient містить sentinel id=0 «Всі ресурси».")
    public void systemAllResourcesRegionSeedIsPresent() {
        StorageRegionResponse region = regionFixture.findSystemAllResourcesRegion();

        assertThat(region.getAccessMode()).isEqualTo(StorageAccessMode.RESOURCES.name());
        assertThat(region.getRecipientStorage()).isNull();

        List<StorageRegionResourceResponse> resources =
                regionFixture.getRegionResources(UserRole.ADMIN, region.getId());
        assertThat(resources)
                .anySatisfy(resource -> {
                    assertThat(resource.getResourceId()).isZero();
                    assertThat(resource.getName()).isEqualTo("Всі ресурси");
                });
    }

    @Test(priority = 2)
    @TestCaseId("TC-STR-RES-020")
    @Severity(SeverityLevel.CRITICAL)
    @Description("RESTRICTED unit до membership не бачить тестові ресурси; wildcard id=0 відкриває весь каталог.")
    public void memberSeesCompleteResourceCatalog() {
        String run = uniqueRunPrefix("member");
        ResourceResponse first = resourceFixture.createUniqueResource(run + "a-");
        ResourceResponse second = resourceFixture.createUniqueResource(run + "b-");
        StorageResponse unit = createRestrictedUnitWithoutResourceRegion("sys-all-member-");

        assertThat(resourceIds(unit, run)).doesNotContain(first.getId(), second.getId());

        attachToSystemRegion(unit.getId());

        assertThat(resourceIds(unit, run))
                .contains(first.getId(), second.getId())
                .doesNotContain(0L);
        assertThat(resourceIds(unit, first.getName())).contains(first.getId());
        assertThat(resourceIds(unit, second.getName())).contains(second.getId());
    }

    @Test(priority = 3)
    @TestCaseId("TC-STR-RES-021")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Після DELETE member wildcard-доступ до каталогу зникає.")
    public void revokingMembershipRemovesWildcardAccess() {
        ResourceResponse resource = resourceFixture.createUniqueResource(uniqueRunPrefix("revoke"));
        StorageResponse unit = createRestrictedUnitWithoutResourceRegion("sys-all-revoke-");
        attachToSystemRegion(unit.getId());

        assertThat(resourceIds(unit, resource.getName())).contains(resource.getId());

        regionFixture.detachMemberFromSystemAllResourcesRegion(unit.getId());
        systemRegionMembers.remove(unit.getId());

        assertThat(resourceIds(unit, resource.getName())).doesNotContain(resource.getId());
    }

    @Test(priority = 4)
    @TestCaseId("TC-STR-RES-022")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Wildcard-доступ застосовується лише до member; інший RESTRICTED unit ізольований.")
    public void wildcardAccessIsIsolatedToMembers() {
        ResourceResponse resource = resourceFixture.createUniqueResource(uniqueRunPrefix("isolation"));
        StorageResponse member = createRestrictedUnitWithoutResourceRegion("sys-all-member-a-");
        StorageResponse outsider = createRestrictedUnitWithoutResourceRegion("sys-all-member-b-");
        attachToSystemRegion(member.getId());

        assertThat(resourceIds(member, resource.getName())).contains(resource.getId());
        assertThat(resourceIds(outsider, resource.getName())).doesNotContain(resource.getId());
    }

    @Test(priority = 5)
    @TestCaseId("TC-STR-RES-023")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Перевіряє, що технічний resource id=0 не витікає у scoped GET /resources.
            Ресурс шукаємо за name (staging засмічений sys-all-res-*).
            Sentinel — окремим запитом name=«Всі ресурси» без isActive (inactive seed).
            Відомий дефект: getPageByFiltersAndAccess не містить умови r.id > 0.
            """)
    public void scopedResourcePageDoesNotExposeSentinel() {
        ResourceResponse resource = resourceFixture.createUniqueResource(uniqueRunPrefix("page"));
        StorageResponse unit = createRestrictedUnitWithoutResourceRegion("sys-all-page-");
        attachToSystemRegion(unit.getId());

        List<Long> byName = resourceFixture.getPageForStorage(
                        UserRole.ADMIN, unit.getId(), resource.getName(), null, null).stream()
                .map(ResourceResponse::getId)
                .toList();
        assertThat(byName).contains(resource.getId());

        // Sentinel is_active=false — без isActive-фільтра page може повернути id=0 (product bug).
        List<Long> sentinelHits = resourceFixture.getPageForStorage(
                        UserRole.ADMIN, unit.getId(), "Всі ресурси", null, null).stream()
                .map(ResourceResponse::getId)
                .toList();
        assertThat(sentinelHits).doesNotContain(0L);
    }

    @Test(priority = 6)
    @TestCaseId("TC-STR-RES-024")
    @Severity(SeverityLevel.NORMAL)
    @Description("Union системного wildcard та кастомного RESOURCES-регіону повертає повний каталог без дублікатів.")
    public void wildcardDominatesCustomResourceRegionUnion() {
        String run = uniqueRunPrefix("union");
        ResourceResponse granted = resourceFixture.createUniqueResource(run + "a-");
        ResourceResponse outside = resourceFixture.createUniqueResource(run + "b-");
        StorageResponse unit = createRestrictedUnitWithoutResourceRegion("sys-all-union-");

        StorageRegionResponse customRegion =
                regionFixture.createRegion(unit, StorageAccessMode.RESOURCES, "sys-all-custom-");
        regionFixture.addRegionMembers(customRegion.getId(), unit.getId());
        regionFixture.addRegionResources(customRegion.getId(), granted.getId());
        attachToSystemRegion(unit.getId());

        List<Long> ids = resourceIds(unit, run);
        assertThat(ids).contains(granted.getId(), outside.getId());
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test(priority = 7)
    @TestCaseId("TC-STR-RES-025")
    @Severity(SeverityLevel.CRITICAL)
    @Description("CREW використовує wildcard системного регіону через grants батьківського UNIT.")
    public void crewInheritsSystemRegionFromParentUnit() {
        ResourceResponse resource = resourceFixture.createUniqueResource(uniqueRunPrefix("crew"));
        StorageResponse unit = createRestrictedUnitWithoutResourceRegion("sys-all-crew-unit-");
        attachToSystemRegion(unit.getId());
        StorageResponse crew = storageFixture.createStorage(
                StorageDataFactory.crewStorage(unit.getId(), "sys-all-crew-")
                        .accessMode(StorageAccessMode.REGIONS)
                        .build());

        assertThat(resourceIds(crew, resource.getName())).contains(resource.getId());
    }

    @Test(priority = 8)
    @TestCaseId("TC-STR-RES-026")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            POST order з quantity=0 гарантовано не створює артефакт, але проходить resource grant-check.
            Відповідь має містити лише quantity validation, не помилку доступу до wildcard-visible resource.
            Відомий дефект: findGrantedResourceIds не розгортає resource_id=0.
            """)
    public void orderGrantCheckRecognizesSystemRegionWildcard() {
        ResourceResponse resource = resourceFixture.createUniqueResource(uniqueRunPrefix("order"));
        StorageResponse unit = createRestrictedUnitWithoutResourceRegion("sys-all-order-");
        attachToSystemRegion(unit.getId());

        assertThat(resourceIds(unit, resource.getName())).contains(resource.getId());

        Map<String, Object> request = Map.of(
                "storageId", unit.getId(),
                "lines", List.of(Map.of(
                        "resourceId", resource.getId(),
                        "quantity", 0)));
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_POST_CREATE, UserRole.ADMIN, request);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.asString())
                .contains("Кількість повинна бути більша від нуля")
                .doesNotContain("Локація не має доступу до ресурсу з id " + resource.getId());
    }

    private StorageResponse createRestrictedUnitWithoutResourceRegion(String prefix) {
        StorageResponse parent = storageFixture.resolveParentUnit();
        return storageFixture.createStorage(
                StorageDataFactory.restrictedUnitStorage(parent.getId(), prefix).build());
    }

    private void attachToSystemRegion(Long storageId) {
        regionFixture.attachMemberToSystemAllResourcesRegion(storageId);
        systemRegionMembers.add(storageId);
    }

    /** Per-scenario token so autocomplete size=50 hits only this run's resources on polluted staging. */
    private static String uniqueRunPrefix(String scenario) {
        return RESOURCE_STEM + scenario + "-" + Long.toString(System.currentTimeMillis(), 36)
                + "-" + UUID.randomUUID().toString().substring(0, 8) + "-";
    }

    private List<Long> resourceIds(StorageResponse storage, String search) {
        return resourceFixture.autocompleteForStorage(
                        UserRole.ADMIN, storage.getId(), search, false).stream()
                .map(ResourceResponse::getId)
                .toList();
    }
}
