package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.storage.StorageRegionDataFactory;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.UserRole;
import com.erp.models.request.StorageRegionRequest;
import com.erp.models.response.StorageLocationLinkResponse;
import com.erp.models.response.StorageLocationSuggestionResponse;
import com.erp.models.response.StorageRegionLocationResponse;
import com.erp.models.response.StorageRegionMemberResponse;
import com.erp.models.response.StorageRegionResponse;
import com.erp.models.response.StorageResponse;
import com.erp.utils.helpers.AllureHelper;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRUD областей видимості (StorageRegion) та керування locations/members/explicit grants.
 * <p>Бізнес-контекст: адміністратор централізовано керує named-набором дозволених локацій
 * ({@code storage_region}) і прив'язками member/locations; окремо — ручний grant через
 * {@code storage_location}. Усі тестові локації та області видаляються в {@link StorageApiTestBase}.
 */
@Slf4j
@Epic("Master Data")
@Feature("Storages")
@Story("Storage Visibility Regions")
public class StorageRegionTest extends StorageApiTestBase {

    @BeforeClass(alwaysRun = true)
    @Step("Підготовка середовища для тестів областей видимості")
    public void setupStorageRegionTest() {
        storageFixture.prepareContext();
        SchemaRegistry.logSchemaCoverage();
    }

    @Test(priority = 10)
    @TestCaseId("TC-STR-REG-001")
    @Description("""
            Що перевіряємо: ADMIN може створити область видимості (StorageRegion) через POST.
            Тестові дані: recipient — нова локація reg-rec-; request з name, accessMode=FULL_ACCESS,
            recipientStorage=recipient.id. Роль: ADMIN (business-unit::create).
            Очікування: HTTP 200, схема storage-region-response; у response id, name, accessMode,
            recipientStorage.id збігаються з request.
            Cleanup: область у cleanup-черзі regionFixture.
            """ + StorageRegionsAllureDescriptions.ON_FAIL_API)
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateStorageRegion() {
        StorageResponse recipient = storageFixture.createUniqueStorage("reg-rec-");
        StorageRegionRequest request = StorageRegionDataFactory.createRegion(
                recipient, StorageAccessMode.FULL_ACCESS, "reg-create-");

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_REGION_POST_CREATE, UserRole.ADMIN, request);

        assertThat(response.statusCode()).isEqualTo(200);
        AllureHelper.attachSchemaValidationInfo(ApiEndpointDefinition.STORAGE_REGION_POST_CREATE, response);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.STORAGE_REGION_POST_CREATE);

        StorageRegionResponse created = response.as(StorageRegionResponse.class);
        regionFixture.trackForCleanup(created.getId());
        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo(request.getName());
        assertThat(created.getAccessMode()).isEqualTo(StorageAccessMode.FULL_ACCESS.name());
        assertThat(created.getRecipientStorage()).isNotNull();
        assertThat(created.getRecipientStorage().getId()).isEqualTo(recipient.getId());
    }

    @Test(priority = 20)
    @TestCaseId("TC-STR-REG-002")
    @Description("""
            Що перевіряємо: пагінований список областей фільтрується за name (як у UI адмін-форми).
            Тестові дані: створюємо область reg-list- з accessMode=REGIONS; GET /storages/regions?name=
            з префіксом імені (перші 8 символів). Роль: ADMIN.
            Очікування: HTTP 200, створена область присутня у content.
            """ + StorageRegionsAllureDescriptions.ON_FAIL_API)
    @Severity(SeverityLevel.NORMAL)
    public void testFindRegionsByName() {
        StorageResponse recipient = storageFixture.createUniqueStorage("reg-list-");
        StorageRegionResponse created = regionFixture.createRegion(
                recipient, StorageAccessMode.REGIONS, "reg-list-");

        List<StorageRegionResponse> regions = regionFixture.findRegions(
                UserRole.ADMIN, created.getName().substring(0, Math.min(8, created.getName().length())));

        assertThat(regions).anyMatch(r -> Objects.equals(r.getId(), created.getId()));
    }

    @Test(priority = 30)
    @TestCaseId("TC-STR-REG-003")
    @Description("""
            Що перевіряємо: деталі області за id (картка редагування в UI).
            Тестові дані: область reg-get-, recipient reg-get-, accessMode=FULL_ACCESS.
            Очікування: GET /storages/regions/{id} повертає той самий id, name, recipientStorage.id.
            """ + StorageRegionsAllureDescriptions.ON_FAIL_API)
    @Severity(SeverityLevel.CRITICAL)
    public void testGetRegionDetails() {
        StorageResponse recipient = storageFixture.createUniqueStorage("reg-get-");
        StorageRegionResponse created = regionFixture.createRegion(
                recipient, StorageAccessMode.FULL_ACCESS, "reg-get-");

        StorageRegionResponse details = regionFixture.getById(UserRole.ADMIN, created.getId());

        assertThat(details.getId()).isEqualTo(created.getId());
        assertThat(details.getName()).isEqualTo(created.getName());
        assertThat(details.getRecipientStorage().getId()).isEqualTo(recipient.getId());
    }

    @Test(priority = 40)
    @TestCaseId("TC-STR-REG-004")
    @Description("""
            Що перевіряємо: оновлення всіх ключових полів області (name, recipientStorage, accessMode).
            Тестові дані: область на recipientA (FULL_ACCESS) → PUT з новим name, recipientB,
            accessMode=REGIONS. Роль: ADMIN.
            Очікування: HTTP 200, GET підтверджує зміни в усіх трьох полях.
            """ + StorageRegionsAllureDescriptions.ON_FAIL_API)
    @Severity(SeverityLevel.CRITICAL)
    public void testUpdateRegion() {
        StorageResponse recipientA = storageFixture.createUniqueStorage("reg-upd-a-");
        StorageResponse recipientB = storageFixture.createUniqueStorage("reg-upd-b-");
        StorageRegionResponse created = regionFixture.createRegion(
                recipientA, StorageAccessMode.FULL_ACCESS, "reg-upd-");

        StorageRegionRequest update = StorageRegionDataFactory.updateRegion(
                "updated-" + created.getName(),
                StorageAccessMode.REGIONS,
                recipientB.getId());

        StorageRegionResponse updated = regionFixture.updateRegion(created.getId(), update);

        assertThat(updated.getName()).isEqualTo(update.getName());
        assertThat(updated.getAccessMode()).isEqualTo(StorageAccessMode.REGIONS.name());
        assertThat(updated.getRecipientStorage().getId()).isEqualTo(recipientB.getId());
    }

    @Test(priority = 50)
    @TestCaseId("TC-STR-REG-005")
    @Description("""
            Що перевіряємо: видалення області видимості (soft admin action).
            Тестові дані: область reg-del-; перед DELETE знімаємо з cleanup-чергі (видаляємо вручну).
            Очікування: DELETE → 200; повторний GET /regions/{id} → не 200 (404).
            """ + StorageRegionsAllureDescriptions.ON_FAIL_API)
    @Severity(SeverityLevel.CRITICAL)
    public void testDeleteRegion() {
        StorageResponse recipient = storageFixture.createUniqueStorage("reg-del-");
        StorageRegionResponse created = regionFixture.createRegion(
                recipient, StorageAccessMode.FULL_ACCESS, "reg-del-");
        regionFixture.untrackForCleanup(created.getId());

        Response deleteResponse = regionFixture.deleteRegion(UserRole.ADMIN, created.getId());
        assertThat(deleteResponse.statusCode()).isEqualTo(200);

        Response getResponse = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_REGION_GET_BY_ID,
                UserRole.ADMIN,
                null,
                String.valueOf(created.getId()));
        assertThat(getResponse.statusCode()).isNotEqualTo(200);
    }

    @Test(priority = 60)
    @TestCaseId("TC-STR-REG-010")
    @Description("""
            Що перевіряємо: додавання та видалення локацій у складі області видимості.
            Тестові дані: область reg-loc-; locations A, B, C — окремі STORAGE; спочатку PUT A+C,
            потім PUT B (ідемпотентне накопичення); DELETE B+C.
            Очікування: після додавання GET .../locations містить 3 storageId; після видалення — лише A.
            API: PUT/DELETE /storages/regions/{regionId}/locations?locations=
            """ + StorageRegionsAllureDescriptions.ON_FAIL_API)
    @Severity(SeverityLevel.CRITICAL)
    public void testAddAndRemoveRegionLocations() {
        StorageResponse recipient = storageFixture.createUniqueStorage("reg-loc-rec-");
        StorageResponse locationA = storageFixture.createUniqueStorage("reg-loc-a-");
        StorageResponse locationB = storageFixture.createUniqueStorage("reg-loc-b-");
        StorageResponse locationC = storageFixture.createUniqueStorage("reg-loc-c-");

        StorageRegionResponse region = regionFixture.createRegion(
                recipient, StorageAccessMode.FULL_ACCESS, "reg-loc-");
        regionFixture.addRegionLocations(region.getId(), locationA.getId(), locationC.getId());
        regionFixture.addRegionLocations(region.getId(), locationB.getId());

        List<StorageRegionLocationResponse> locations =
                regionFixture.getRegionLocations(UserRole.ADMIN, region.getId());
        assertThat(locations).hasSizeGreaterThanOrEqualTo(3);
        assertThat(locations.stream().map(StorageRegionLocationResponse::getStorageId))
                .contains(locationA.getId(), locationB.getId(), locationC.getId());

        regionFixture.removeRegionLocations(region.getId(), locationB.getId(), locationC.getId());
        locations = regionFixture.getRegionLocations(UserRole.ADMIN, region.getId());
        assertThat(locations).hasSize(1);
        assertThat(locations.getFirst().getStorageId()).isEqualTo(locationA.getId());
    }

    @Test(priority = 70)
    @TestCaseId("TC-STR-REG-012")
    @Description("""
            Що перевіряємо: прив'язка підрозділів-споживачів (members) до області — хто отримує видимість.
            Тестові дані: область reg-mem- (accessMode=REGIONS); members A, B — тестові STORAGE;
            PUT members по одному, DELETE member B.
            Очікування: GET .../members містить обидва id; після DELETE — лише A.
            API: PUT/DELETE /storages/regions/{regionId}/members?members=
            """ + StorageRegionsAllureDescriptions.ON_FAIL_API)
    @Severity(SeverityLevel.CRITICAL)
    public void testAddAndRemoveRegionMembers() {
        StorageResponse recipient = storageFixture.createUniqueStorage("reg-mem-rec-");
        StorageResponse memberA = storageFixture.createUniqueStorage("reg-mem-a-");
        StorageResponse memberB = storageFixture.createUniqueStorage("reg-mem-b-");

        StorageRegionResponse region = regionFixture.createRegion(
                recipient, StorageAccessMode.REGIONS, "reg-mem-");
        regionFixture.addRegionMembers(region.getId(), memberA.getId());
        regionFixture.addRegionMembers(region.getId(), memberB.getId());

        List<StorageRegionMemberResponse> members =
                regionFixture.getRegionMembers(UserRole.ADMIN, region.getId());
        assertThat(members).hasSizeGreaterThanOrEqualTo(2);
        assertThat(members.stream().map(StorageRegionMemberResponse::getStorageId))
                .contains(memberA.getId(), memberB.getId());

        regionFixture.removeRegionMembers(region.getId(), memberB.getId());
        members = regionFixture.getRegionMembers(UserRole.ADMIN, region.getId());
        assertThat(members).hasSize(1);
        assertThat(members.getFirst().getStorageId()).isEqualTo(memberA.getId());
    }

    @Test(priority = 80)
    @TestCaseId("TC-STR-REG-014")
    @Description("""
            Що перевіряємо: одна локація може входити в кілька областей (логіка union на бекенді).
            Тестові дані: sharedLocation у region1 і region2; member прив'язаний до обох областей.
            Очікування: GET locations кожної області окремо містить sharedLocation.storageId.
            Примітка: фактичний union у селекторах перевіряє StorageVisibilityTest (TC-STR-REG-032).
            """ + StorageRegionsAllureDescriptions.ON_FAIL_API)
    @Severity(SeverityLevel.NORMAL)
    public void testLocationInTwoRegionsUnion() {
        StorageResponse sharedLocation = storageFixture.createUniqueStorage("reg-union-loc-");
        StorageResponse recipient1 = storageFixture.createUniqueStorage("reg-union-r1-");
        StorageResponse recipient2 = storageFixture.createUniqueStorage("reg-union-r2-");
        StorageResponse member = storageFixture.createUniqueStorage("reg-union-mem-");

        StorageRegionResponse region1 = regionFixture.createRegion(
                recipient1, StorageAccessMode.FULL_ACCESS, "reg-union-1-");
        StorageRegionResponse region2 = regionFixture.createRegion(
                recipient2, StorageAccessMode.FULL_ACCESS, "reg-union-2-");

        regionFixture.addRegionLocations(region1.getId(), sharedLocation.getId());
        regionFixture.addRegionLocations(region2.getId(), sharedLocation.getId());
        regionFixture.addRegionMembers(region1.getId(), member.getId());
        regionFixture.addRegionMembers(region2.getId(), member.getId());

        List<StorageRegionLocationResponse> r1Locations =
                regionFixture.getRegionLocations(UserRole.ADMIN, region1.getId());
        List<StorageRegionLocationResponse> r2Locations =
                regionFixture.getRegionLocations(UserRole.ADMIN, region2.getId());

        assertThat(r1Locations.stream().map(StorageRegionLocationResponse::getStorageId))
                .contains(sharedLocation.getId());
        assertThat(r2Locations.stream().map(StorageRegionLocationResponse::getStorageId))
                .contains(sharedLocation.getId());
    }

    @Test(priority = 90)
    @TestCaseId("TC-STR-REG-015")
    @Description("""
            Що перевіряємо: autocomplete/suggest для прив'язки локацій до області (адмін UI).
            Тестові дані: STORAGE reg-suggest-st- + область reg-suggest- з цією локацією в locations;
            GET /storages/locations/suggest?name= з унікальним фрагментом імені.
            Очікування: HTTP 200, непорожній content; серед name є збіг з токеном пошуку.
            """ + StorageRegionsAllureDescriptions.ON_FAIL_API)
    @Severity(SeverityLevel.NORMAL)
    public void testSuggestLocations() {
        StorageResponse storage = storageFixture.createUniqueStorage("reg-suggest-st-");
        String uniqueToken = storage.getName().substring(Math.max(0, storage.getName().length() - 10));

        StorageRegionResponse region = regionFixture.createRegion(
                storage, StorageAccessMode.REGIONS, "reg-suggest-");
        regionFixture.addRegionLocations(region.getId(), storage.getId());

        List<StorageLocationSuggestionResponse> suggestions =
                regionFixture.suggestLocations(UserRole.ADMIN, uniqueToken);

        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions.stream().map(StorageLocationSuggestionResponse::getName))
                .anyMatch(name -> name != null && name.contains(uniqueToken.substring(0, 4)));
    }

    @Test(priority = 100)
    @TestCaseId("TC-STR-REG-050")
    @Description("""
            Що перевіряємо: ручний grant/revoke видимості (п.6 вимог) без області — explicit link.
            Модель БД: storage_location.storage_id = видима локація, location_storage_id = viewer (підрозділ).
            Тестові дані: viewer reg-explicit-view-; visibleA, visibleB — окремі локації;
            PUT /storages/{visibleA}/locations?locations={viewer}, аналогічно для B; DELETE grant для B.
            Очікування: GET /storages/{visibleId}/locations показує viewer у locationId; після revoke B — link зник.
            """ + StorageRegionsAllureDescriptions.ON_FAIL_API)
    @Severity(SeverityLevel.CRITICAL)
    public void testExplicitGrantAndRevokeLocations() {
        StorageResponse viewer = storageFixture.createUniqueStorage("reg-explicit-view-");
        StorageResponse visibleA = storageFixture.createUniqueStorage("reg-explicit-a-");
        StorageResponse visibleB = storageFixture.createUniqueStorage("reg-explicit-b-");

        regionFixture.addExplicitLocations(visibleA.getId(), viewer.getId());
        regionFixture.addExplicitLocations(visibleB.getId(), viewer.getId());

        List<StorageLocationLinkResponse> links =
                regionFixture.getStorageLocationLinks(UserRole.ADMIN, visibleA.getId());
        assertThat(links).anyMatch(l -> Objects.equals(l.getLocationId(), viewer.getId()));

        links = regionFixture.getStorageLocationLinks(UserRole.ADMIN, visibleB.getId());
        assertThat(links).anyMatch(l -> Objects.equals(l.getLocationId(), viewer.getId()));

        regionFixture.removeExplicitLocations(visibleB.getId(), viewer.getId());
        links = regionFixture.getStorageLocationLinks(UserRole.ADMIN, visibleB.getId());
        assertThat(links).noneMatch(l -> Objects.equals(l.getLocationId(), viewer.getId()));
        links = regionFixture.getStorageLocationLinks(UserRole.ADMIN, visibleA.getId());
        assertThat(links).anyMatch(l -> Objects.equals(l.getLocationId(), viewer.getId()));
    }

    @Test(priority = 110)
    @TestCaseId("TC-STR-REG-054")
    @Description("""
            Що перевіряємо: адмін може переглянути всі links видимості для конкретної локації.
            Тестові дані: viewer + область (member) + explicit grant viewer←viaExplicit.
            Очікування: GET /storages/{viaExplicit}/locations містить viewer.id у locationId
            (explicit grant з точки зору видимої локації).
            """ + StorageRegionsAllureDescriptions.ON_FAIL_API)
    @Severity(SeverityLevel.NORMAL)
    public void testGetStorageLocationLinks() {
        StorageResponse viewer = storageFixture.createUniqueStorage("reg-links-view-");
        StorageResponse viaRegion = storageFixture.createUniqueStorage("reg-links-reg-");
        StorageResponse viaExplicit = storageFixture.createUniqueStorage("reg-links-exp-");

        StorageRegionResponse region = regionFixture.createRegion(
                viaRegion, StorageAccessMode.FULL_ACCESS, "reg-links-");
        regionFixture.addRegionLocations(region.getId(), viaRegion.getId());
        regionFixture.addRegionMembers(region.getId(), viewer.getId());
        regionFixture.addExplicitLocations(viaExplicit.getId(), viewer.getId());

        List<StorageLocationLinkResponse> explicitLinks =
                regionFixture.getStorageLocationLinks(UserRole.ADMIN, viaExplicit.getId());

        assertThat(explicitLinks).isNotEmpty();
        assertThat(explicitLinks.stream().map(StorageLocationLinkResponse::getLocationId))
                .contains(viewer.getId());
    }
}
