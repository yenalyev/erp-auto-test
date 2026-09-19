package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.LocationFeature;
import com.erp.enums.StorageRelation;
import com.erp.enums.UserRole;
import com.erp.models.request.StorageRequest;
import com.erp.models.response.StorageResponse;
import com.erp.utils.helpers.AllureHelper;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRUD і фільтри StorageRelation. Тестові локації архівуються після кожного тесту та класу (StorageApiTestBase).
 */
@Slf4j
@Epic("Master Data")
@Feature("Storages")
@Story("Storage Relation INTERNAL / EXTERNAL")
public class StorageRelationTest extends StorageApiTestBase {

    private static final List<Set<LocationFeature>> RELATION_TEST_FEATURES = List.of(
            Set.of(LocationFeature.RELOCATIONS),
            Set.of(LocationFeature.RELOCATIONS, LocationFeature.EQUIPMENT),
            Set.of(LocationFeature.RELOCATIONS, LocationFeature.CREWS));

    @BeforeClass(alwaysRun = true)
    @Step("Підготовка середовища для тестів relation")
    public void setupStorageRelationTest() {
        storageFixture.prepareContext();
        SchemaRegistry.logSchemaCoverage();
    }

    @Test(priority = 10)
    @TestCaseId("TC-STR-013")
    @Description("""
            Що перевіряємо: POST створює локацію з relation=EXTERNAL (default форми створення в UI).
            Тестові дані: дочірня локація kind=LOCATION, parentId=owner1/parent unit, accessMode=FULL_ACCESS,
            унікальне ім'я з префіксом ext-. Очікування: HTTP 200, relation=EXTERNAL у response та GET by id.
            Cleanup: локація в cleanup-черзі, архівується після тесту (StorageApiTestBase).
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateExternalStorage() {
        StorageResponse parent = storageFixture.resolveParentUnit();
        StorageRequest requestBody = StorageDataFactory.externalStorage(parent.getId(), "ext-").build();

        StorageResponse created = storageFixture.createStorage(requestBody);

        Allure.step("STEP 2: Валідація relation та схеми GET", () -> {
            assertThat(created.getRelation()).isEqualTo(StorageRelation.EXTERNAL.name());
            assertThat(created.getKind()).isEqualTo(requestBody.getKind());
            assertThat(created.getFeatures()).containsExactlyInAnyOrderElementsOf(requestBody.getFeatures());
            assertThat(created.getParent()).isNotNull();
            assertThat(created.getParent().getId()).isEqualTo(parent.getId());
            assertThat(created.getActive()).isTrue();
        });

        StorageResponse fetched = storageFixture.getById(UserRole.ADMIN, created.getId());
        assertThat(fetched.getRelation()).isEqualTo(StorageRelation.EXTERNAL.name());
        assertThat(fetched.getName()).isEqualTo(requestBody.getName());
    }

    @Test(priority = 20)
    @TestCaseId("TC-STR-014")
    @Description("""
            Що перевіряємо: односторонній перехід EXTERNAL→INTERNAL дозволений при PUT (імітація confirm у UI).
            Тестові дані: спочатку create EXTERNAL kind=LOCATION, потім PUT з relation=INTERNAL, решта полів без змін.
            Очікування: HTTP 200, GET підтверджує relation=INTERNAL.
            """)
    @Severity(SeverityLevel.NORMAL)
    public void testUpdateRelationExternalToInternalAllowed() {
        StorageResponse parent = storageFixture.resolveParentUnit();
        StorageResponse external = storageFixture.createExternalChildStorage(parent.getId(), "ext2int-");
        assertThat(external.getRelation()).isEqualTo(StorageRelation.EXTERNAL.name());

        StorageRequest update = StorageDataFactory.updateFromExisting(external, builder ->
                builder.relation(StorageRelation.INTERNAL));

        Response response = storageFixture.update(UserRole.ADMIN, external.getId(), update);
        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.STORAGE_PUT_UPDATE);

        StorageResponse updated = storageFixture.getById(UserRole.ADMIN, external.getId());
        assertThat(updated.getRelation()).isEqualTo(StorageRelation.INTERNAL.name());
    }

    @Test(priority = 30)
    @TestCaseId("TC-STR-015")
    @Description("""
            Що перевіряємо: query-параметр relation=INTERNAL на GET /storages фільтрує за відношенням.
            Тестові дані: пара child STORAGE — INTERNAL (int-filter-) та EXTERNAL (ext-filter-) під тим самим parent.
            Очікування: INTERNAL id є у відповіді ?relation=INTERNAL, EXTERNAL id — відсутній.
            """)
    @Severity(SeverityLevel.NORMAL)
    public void testGetStoragesFilterByInternalRelation() {
        StorageResponse parent = storageFixture.resolveParentUnit();
        StorageResponse internalStorage = storageFixture.createChildStorage(parent.getId(), "int-filter-");
        StorageResponse externalStorage = storageFixture.createExternalChildStorage(parent.getId(), "ext-filter-");

        List<StorageResponse> internalPage = storageFixture.getPageContent(
                UserRole.ADMIN, Map.of(
                        "relation", StorageRelation.INTERNAL.name(),
                        "name", internalStorage.getName()));

        assertThat(internalPage.stream().map(StorageResponse::getId))
                .contains(internalStorage.getId())
                .doesNotContain(externalStorage.getId());
    }

    @Test(priority = 40)
    @TestCaseId("TC-STR-016")
    @Description("""
            Що перевіряємо: контракт API для селектора «Звідки» на /equipment — лише EXTERNAL локації.
            Тестові дані: GET /storages/names?isActive=true&relation=EXTERNAL; порівняння з двома child STORAGE
            (INTERNAL int-names- / EXTERNAL ext-names-).
            Очікування: EXTERNAL child присутній, INTERNAL child відсутній у списку.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testGetNamesExternalRelationContract() {
        StorageResponse parent = storageFixture.resolveParentUnit();
        StorageResponse internalStorage = storageFixture.createChildStorage(parent.getId(), "int-names-");
        StorageResponse externalStorage = storageFixture.createExternalChildStorage(parent.getId(), "ext-names-");

        List<Long> externalById = storageFixture.getNames(
                        UserRole.ADMIN, true, StorageRelation.EXTERNAL, null, null, externalStorage.getId())
                .stream().map(StorageResponse::getId).toList();
        List<Long> internalInExternal = storageFixture.getNames(
                        UserRole.ADMIN, true, StorageRelation.EXTERNAL, null, null, internalStorage.getId())
                .stream().map(StorageResponse::getId).toList();

        assertThat(externalById).contains(externalStorage.getId());
        assertThat(internalInExternal).doesNotContain(internalStorage.getId());
    }

    @Test(priority = 50)
    @TestCaseId("TC-STR-017")
    @Description("""
            Що перевіряємо: GET /storages?relation=EXTERNAL повертає лише EXTERNAL і проходить JSON schema.
            Тестові дані: створюємо EXTERNAL child (ext-page-), запит page size=500, relation=EXTERNAL.
            Очікування: HTTP 200, schema valid, усі елементи content мають relation=EXTERNAL.
            """)
    @Severity(SeverityLevel.NORMAL)
    public void testGetStoragesFilterByExternalRelationWithSchema() {
        StorageResponse parent = storageFixture.resolveParentUnit();
        storageFixture.createExternalChildStorage(parent.getId(), "ext-page-");

        Response response = storageFixture.getPage(
                UserRole.ADMIN, Map.of("relation", StorageRelation.EXTERNAL.name()));

        assertThat(response.statusCode()).isEqualTo(200);
        AllureHelper.attachSchemaValidationInfo(ApiEndpointDefinition.STORAGE_GET_ALL, response);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.STORAGE_GET_ALL);

        List<StorageResponse> content = storageFixture.getPageContent(
                UserRole.ADMIN, Map.of("relation", StorageRelation.EXTERNAL.name()));
        assertThat(content).isNotEmpty();
        assertThat(content).allMatch(s -> StorageRelation.EXTERNAL.name().equals(s.getRelation()));
    }

    @Test(priority = 60)
    @TestCaseId("TC-STR-018")
    @Description("""
            Що перевіряємо: поведінка relation (INTERNAL/EXTERNAL) не залежить від набору функцій локації.
            Тестові дані: для наборів функцій, дозволених зовнішнім локаціям, створюємо пару child:
            INTERNAL та EXTERNAL під одним parent.
            Очікування для кожного набору: relation у GET збігається з заданим; INTERNAL є лише у ?relation=INTERNAL;
            EXTERNAL — лише у ?relation=EXTERNAL; перехресна присутність відсутня.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testRelationBehaviorIndependentOfLocationFeatures() {
        StorageResponse parent = storageFixture.resolveParentUnit();

        for (Set<LocationFeature> features : RELATION_TEST_FEATURES) {
            String featureKey = features.contains(LocationFeature.CREWS) ? "crews"
                    : features.contains(LocationFeature.EQUIPMENT) ? "equipment" : "relocations";
            StorageResponse internalLoc = storageFixture.createStorage(StorageDataFactory
                    .childStorage(parent.getId(), "feature-" + featureKey + "-int-")
                    .features(features).relation(StorageRelation.INTERNAL).build());
            StorageResponse externalLoc = storageFixture.createStorage(StorageDataFactory
                    .childStorage(parent.getId(), "feature-" + featureKey + "-ext-")
                    .features(features).relation(StorageRelation.EXTERNAL).build());

            Allure.step("Assert relation round-trip for features=" + featureKey, () -> {
                StorageResponse internalFetched = storageFixture.getById(UserRole.ADMIN, internalLoc.getId());
                StorageResponse externalFetched = storageFixture.getById(UserRole.ADMIN, externalLoc.getId());

                assertThat(internalFetched.getKind()).isEqualTo(internalLoc.getKind());
                assertThat(externalFetched.getKind()).isEqualTo(externalLoc.getKind());
                assertThat(internalFetched.getFeatures()).containsExactlyInAnyOrderElementsOf(features);
                assertThat(externalFetched.getFeatures()).containsExactlyInAnyOrderElementsOf(features);
                assertThat(internalFetched.getRelation()).isEqualTo(StorageRelation.INTERNAL.name());
                assertThat(externalFetched.getRelation()).isEqualTo(StorageRelation.EXTERNAL.name());
            });

            Allure.step("Assert list filters for features=" + featureKey, () -> {
                List<Long> internalIds = storageFixture.getPageContent(
                                UserRole.ADMIN, Map.of(
                                        "relation", StorageRelation.INTERNAL.name(),
                                        "name", internalLoc.getName()))
                        .stream().map(StorageResponse::getId).toList();
                List<Long> externalIds = storageFixture.getPageContent(
                                UserRole.ADMIN, Map.of(
                                        "relation", StorageRelation.EXTERNAL.name(),
                                        "name", externalLoc.getName()))
                        .stream().map(StorageResponse::getId).toList();

                assertThat(internalIds)
                        .as("INTERNAL filter for features=%s", featureKey)
                        .contains(internalLoc.getId())
                        .doesNotContain(externalLoc.getId());
                assertThat(externalIds)
                        .as("EXTERNAL filter for features=%s", featureKey)
                        .contains(externalLoc.getId())
                        .doesNotContain(internalLoc.getId());
            });

            Allure.step("Assert /names filter for features=" + featureKey, () -> {
                List<Long> externalNameIds = storageFixture.getNames(
                                UserRole.ADMIN, true, StorageRelation.EXTERNAL, null, null, externalLoc.getId())
                        .stream().map(StorageResponse::getId).toList();
                List<Long> internalInExternalNames = storageFixture.getNames(
                                UserRole.ADMIN, true, StorageRelation.EXTERNAL, null, null, internalLoc.getId())
                        .stream().map(StorageResponse::getId).toList();

                assertThat(externalNameIds)
                        .as("EXTERNAL names for features=%s", featureKey)
                        .contains(externalLoc.getId());
                assertThat(internalInExternalNames)
                        .as("INTERNAL must be absent from EXTERNAL names for features=%s", featureKey)
                        .doesNotContain(internalLoc.getId());
            });
        }
    }
}
