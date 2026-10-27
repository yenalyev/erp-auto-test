package com.erp.tests.functional.tech_map;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.TechMapCukrarniaEditFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.query.TechnologicalMapListQuery;
import com.erp.models.response.SimpleEntityResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.annotations.*;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Technological Maps")
@Feature("REQ-MFG-CUK-EDIT — Редагування під Цукрарнею")
public class TechMapCukrarniaEditApiTest extends BaseFunctionalTest {
    private TechMapCukrarniaEditFixture data;

    @BeforeMethod(alwaysRun = true)
    public void arrangeCukrarnia() {
        data = new TechMapCukrarniaEditFixture(testContext, apiExecutor);
        data.prepare(false);
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupCukrarnia() { if (data != null) data.cleanup(); }

    @Test
    @TestCaseId("TC-TM-CUK-API-001")
    @Description("""
            ADMIN: список з storageIds=Цукрарня містить карту внучатої локації.
            GET /{id}?storageId=Цукрарня повинен завантажити ту саму карту для форми.
            Перевірка доступу форми в контексті батьківського workspace: очікується 200 без переходу.
            """)
    public void parentContextLoadsDescendantMap() {
        assertThat(data.maps.listByQuery(UserRole.ADMIN, TechnologicalMapListQuery.forStorage(data.root.getId()).toBuilder()
                .name(data.marker).isActive(true).pageSize(100).build()))
                .extracting(TechnologicalMapResponse::getId).contains(data.source.getId(), data.sibling.getId());
        Response response = apiExecutor.execute(ApiEndpointDefinition.TECH_MAP_GET_BY_ID,
                UserRole.ADMIN, null, data.source.getId(), data.root.getId());
        assertThat(response.statusCode()).as("Parent workspace must load descendant map: %s", response.asString())
                .isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.TECH_MAP_GET_BY_ID);
        assertThat(response.as(TechnologicalMapResponse.class)).usingRecursiveComparison().isEqualTo(data.source);
        data.assertSiblingUnchanged();
    }

    @Test
    @TestCaseId("TC-TM-CUK-API-002")
    @Description("PUT як у useTechnologicalMapForm: повні input/output/groups/type і початкові storageIds; змінити лише name. Контракт збереження незалежний від GET форми.")
    public void renamePreservesVersionAndLocation() {
        var request = TechnologicalMapDataFactory.withRenamed(data.source, data.marker + "-renamed");
        var updated = save(data.updateAsForm(UserRole.ADMIN, data.source.getId(), request));
        assertThat(updated).usingRecursiveComparison().ignoringFields("name").isEqualTo(data.source);
        assertThat(updated.getName()).isEqualTo(request.getName());
        assertThat(data.maps.getVersionsByGroupId(UserRole.ADMIN, updated.getGroupId(), data.grandchild.getId()))
                .extracting(TechnologicalMapResponse::getId).containsExactly(updated.getId());
        data.assertSiblingUnchanged();
    }

    @DataProvider(name = "bindings")
    public Object[][] bindings() { return new Object[][]{{false}, {true}}; }

    @Test(dataProvider = "bindings")
    @TestCaseId("TC-TM-CUK-API-003")
    @Description("PUT як у useTechnologicalMapForm: input/output resourceId+amount, groups без id груп і варіантів, початкові storageIds. Структурна відповідь містить посилання на ресурси за id; display metadata можуть бути null. Повноту даних і збережені альтернативи перевіряє наступний GET за суворою схемою читання.")
    public void structuralEditPreservesAllBindings(boolean shared) {
        var source = shared ? data.create(TechnologicalMapDataFactory.fromExisting(data.source)
                .name(data.marker + "-shared").storageIds(Set.of(data.child.getId(), data.grandchild.getId())).build())
                : data.source;
        var request = TechnologicalMapDataFactory.withFirstInputAmount(source, 3.0);
        Response response = data.updateAsForm(UserRole.ADMIN, source.getId(), request);
        var updated = save(response);
        assertThat(updated.getId()).isNotEqualTo(source.getId());
        assertThat(updated.getGroupId()).isEqualTo(source.getGroupId());
        assertThat(updated.getVersion()).isEqualTo(source.getVersion() + 1);
        assertThat(updated.getStorages()).extracting(SimpleEntityResponse::getId)
                .containsExactlyInAnyOrderElementsOf(request.getStorageIds());
        assertThat(updated.getInput().getFirst().getAmount()).isEqualTo(3.0);
        assertThat(updated.getOutput()).usingRecursiveComparison().isEqualTo(source.getOutput());
        assertThat(TechnologicalMapDataFactory.fromExisting(updated).build().getGroups())
                .usingRecursiveComparison().ignoringFieldsMatchingRegexes("id", ".*\\.id").isEqualTo(request.getGroups());
        for (long storageId : request.getStorageIds()) {
            assertThat(data.maps.getActiveTechMapsByName(storageId, UserRole.ADMIN, source.getName()))
                    .extracting(TechnologicalMapResponse::getId).containsExactly(updated.getId());
            assertThat(data.maps.getVersionsByGroupId(UserRole.ADMIN, source.getGroupId(), storageId))
                    .extracting(TechnologicalMapResponse::getId).containsExactly(updated.getId(), source.getId());
        }
        data.assertSiblingUnchanged();
    }

    @DataProvider(name = "invalidPayloads")
    public Object[][] invalidPayloads() { return new Object[][]{{"zero"}, {"overlap"}}; }

    @Test(dataProvider = "invalidPayloads")
    @TestCaseId("TC-TM-CUK-API-004")
    @Description("Повний PUT із нормою 0 або input∩output відхиляється 400; оригінал, версії та сусідня карта незмінні.")
    public void validationDoesNotCreateVersion(String variant) {
        var request = TechnologicalMapDataFactory.fromExisting(data.source).build();
        if (variant.equals("zero")) request = TechnologicalMapDataFactory.withFirstInputAmount(data.source, 0);
        else request.getOutput().getFirst().setResourceId(request.getInput().getFirst().getResourceId());
        Response response = data.updateAsForm(UserRole.ADMIN, data.source.getId(), request);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().getList("errors")).isNotEmpty();
        data.assertUnchanged(data.source);
        data.assertSiblingUnchanged();
    }

    @Test
    @TestCaseId("TC-TM-CUK-API-005")
    @Description("RESOURCE_VIEWER без права tech-map:update надсилає валідний повний PUT. Очікується 403 без зміни карт/історії.")
    public void readOnlyActorCannotUpdate() {
        Response response = data.updateAsForm(UserRole.RESOURCE_VIEWER, data.source.getId(),
                TechnologicalMapDataFactory.withRenamed(data.source, data.marker + "-forbidden"));
        assertThat(response.statusCode()).isEqualTo(403);
        data.assertUnchanged(data.source);
        data.assertSiblingUnchanged();
    }

    @Test
    @TestCaseId("TC-TM-CUK-API-006")
    @Description("Ізольований керівник локації завантажує карту при EDIT_ALLOWED. ADMIN закриває режим; replay повного PUT керівника з новою нормою повертає 400, версії й сусідня карта незмінні. ADMIN на dev має окремий привілей обходу режиму.")
    public void closedLocationModeRejectsStaleFormSave() {
        var users = new UserFixture(testContext, apiExecutor);
        try {
            var owner = users.createRestrictedOwner(getPlaywrightSessionProvider(), data.grandchild);
            apiExecutor.setSessionForRole(UserRole.DYNAMIC_LOCATION_OWNER, owner.username(), owner.password());
            var loaded = data.maps.getById(UserRole.DYNAMIC_LOCATION_OWNER, data.source.getId(), data.grandchild.getId());
            var request = TechnologicalMapDataFactory.withFirstInputAmount(loaded, 3);
            data.maps.setMode(data.grandchild.getId(), StorageTechnologicalMapMode.READ_ONLY);
            var response = data.updateAsForm(UserRole.DYNAMIC_LOCATION_OWNER, loaded.getId(), request);
            data.maps.assertEditForbidden(response, data.grandchild.getId());
            data.assertUnchanged(data.source);
            data.assertSiblingUnchanged();
        } finally {
            apiExecutor.restoreDefaultSessionForRole(UserRole.DYNAMIC_LOCATION_OWNER);
            users.deactivateTrackedUsers();
        }
    }

    private TechnologicalMapResponse save(Response response) {
        if (response.statusCode() == 200) data.track(response.jsonPath().getLong("id"), data.grandchild.getId());
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.TECH_MAP_UPDATE_NAME);
        var result = response.as(TechnologicalMapResponse.class);
        var persisted = data.maps.getById(UserRole.ADMIN, result.getId(), data.grandchild.getId());
        assertThat(persisted).usingRecursiveComparison().comparingOnlyFields("id", "name", "version", "groupId", "type")
                .isEqualTo(result);
        assertThat(TechnologicalMapDataFactory.fromExisting(persisted).build()).usingRecursiveComparison()
                .isEqualTo(TechnologicalMapDataFactory.fromExisting(result).build());
        return persisted;
    }
}
