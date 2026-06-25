package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.StorageRelation;
import com.erp.enums.UnitType;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRUD і фільтри StorageRelation. Тестові локації архівуються після кожного тесту та класу (StorageApiTestBase).
 */
@Slf4j
@Epic("Master Data")
@Feature("Storages")
@Story("Storage Relation INTERNAL / EXTERNAL")
public class StorageRelationTest extends StorageApiTestBase {

    private static final List<UnitType> RELATION_TEST_TYPES = List.of(
            UnitType.STORAGE, UnitType.UNIT, UnitType.PRODUCTION);

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
            Тестові дані: дочірня локація type=STORAGE, parentId=owner1/parent unit, accessMode=FULL_ACCESS,
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
            assertThat(created.getType()).isEqualTo(UnitType.STORAGE.name());
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
            Тестові дані: спочатку create EXTERNAL type=STORAGE, потім PUT з relation=INTERNAL, решта полів без змін.
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
            Що перевіряємо: query-параметр relation=INTERNAL на GET /storages фільтрує за відношенням, не за type.
            Тестові дані: пара child STORAGE — INTERNAL (int-filter-) та EXTERNAL (ext-filter-) під тим самим parent.
            Очікування: INTERNAL id є у відповіді ?relation=INTERNAL, EXTERNAL id — відсутній.
            """)
    @Severity(SeverityLevel.NORMAL)
    public void testGetStoragesFilterByInternalRelation() {
        StorageResponse parent = storageFixture.resolveParentUnit();
        StorageResponse internalStorage = storageFixture.createChildStorage(parent.getId(), "int-filter-");
        StorageResponse externalStorage = storageFixture.createExternalChildStorage(parent.getId(), "ext-filter-");

        List<StorageResponse> internalPage = storageFixture.getPageContent(
                UserRole.ADMIN, Map.of("relation", StorageRelation.INTERNAL.name()));

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

        List<StorageResponse> externalNames = storageFixture.getNames(
                UserRole.ADMIN, true, StorageRelation.EXTERNAL, null, null, null);

        List<Long> externalIds = externalNames.stream().map(StorageResponse::getId).toList();
        assertThat(externalIds).contains(externalStorage.getId());
        assertThat(externalIds).doesNotContain(internalStorage.getId());
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
            Що перевіряємо: поведінка relation (INTERNAL/EXTERNAL) не залежить від UnitType.
            Тестові дані: для кожного type з {STORAGE, UNIT, PRODUCTION} створюємо пару child під одним parent:
            INTERNAL (typ-<type>-int-) та EXTERNAL (typ-<type>-ext-). CREW/SUPPLIER не тестуємо — інша бізнес-семантика.
            Очікування для кожного type: relation у GET збігається з заданим; INTERNAL є лише у ?relation=INTERNAL;
            EXTERNAL — лише у ?relation=EXTERNAL; перехресна присутність відсутня.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testRelationBehaviorIndependentOfUnitType() {
        StorageResponse parent = storageFixture.resolveParentUnit();

        for (UnitType type : RELATION_TEST_TYPES) {
            String typeKey = type.name().toLowerCase();
            StorageResponse internalLoc = storageFixture.createChildStorage(
                    parent.getId(), "typ-" + typeKey + "-int-", type, StorageRelation.INTERNAL);
            StorageResponse externalLoc = storageFixture.createChildStorage(
                    parent.getId(), "typ-" + typeKey + "-ext-", type, StorageRelation.EXTERNAL);

            Allure.step("Assert relation round-trip for type=" + type, () -> {
                StorageResponse internalFetched = storageFixture.getById(UserRole.ADMIN, internalLoc.getId());
                StorageResponse externalFetched = storageFixture.getById(UserRole.ADMIN, externalLoc.getId());

                assertThat(internalFetched.getType()).isEqualTo(type.name());
                assertThat(externalFetched.getType()).isEqualTo(type.name());
                assertThat(internalFetched.getRelation()).isEqualTo(StorageRelation.INTERNAL.name());
                assertThat(externalFetched.getRelation()).isEqualTo(StorageRelation.EXTERNAL.name());
            });

            Allure.step("Assert list filters for type=" + type, () -> {
                List<Long> internalIds = storageFixture.getPageContent(
                                UserRole.ADMIN, Map.of("relation", StorageRelation.INTERNAL.name()))
                        .stream().map(StorageResponse::getId).toList();
                List<Long> externalIds = storageFixture.getPageContent(
                                UserRole.ADMIN, Map.of("relation", StorageRelation.EXTERNAL.name()))
                        .stream().map(StorageResponse::getId).toList();

                assertThat(internalIds)
                        .as("INTERNAL filter for type=%s", type)
                        .contains(internalLoc.getId())
                        .doesNotContain(externalLoc.getId());
                assertThat(externalIds)
                        .as("EXTERNAL filter for type=%s", type)
                        .contains(externalLoc.getId())
                        .doesNotContain(internalLoc.getId());
            });

            Allure.step("Assert /names filter for type=" + type, () -> {
                List<Long> externalNameIds = storageFixture.getNames(
                                UserRole.ADMIN, true, StorageRelation.EXTERNAL, null, null, null)
                        .stream().map(StorageResponse::getId).toList();

                assertThat(externalNameIds)
                        .as("EXTERNAL names for type=%s", type)
                        .contains(externalLoc.getId())
                        .doesNotContain(internalLoc.getId());
            });
        }
    }
}
