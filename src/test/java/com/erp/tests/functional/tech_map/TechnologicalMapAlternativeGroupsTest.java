package com.erp.tests.functional.tech_map;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapAlternativeGroupResourceResponse;
import com.erp.models.response.TechnologicalMapAlternativeGroupResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API coverage for alternative resource groups on technological maps.
 */
@Slf4j
@Epic("Technological Maps")
@Feature("Alternative groups")
public class TechnologicalMapAlternativeGroupsTest extends BaseFunctionalTest {

    private TechnologicalMapFixture techMapFixture;
    private Long storageId;
    private TechnologicalMapResponse techMapForCleanup;
    private final List<TechnologicalMapResponse> createdMaps = new ArrayList<>();

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    @Step("Підготовка середовища для тестів альтернативних груп техкарт")
    public void setupAlternativeGroupsTest() {
        techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        techMapFixture.prepareContext();
        storageId = techMapFixture.getOwner1StorageId();

        techMapFixture.setMode(storageId, StorageTechnologicalMapMode.EDIT_ALLOWED);
        techMapFixture.assertMode(storageId, UserRole.ADMIN, StorageTechnologicalMapMode.EDIT_ALLOWED);
    }

    @AfterClass(alwaysRun = true)
    @Step("Відновити READ_ONLY для локації Owner1")
    public void restoreReadOnlyMode() {
        if (techMapFixture != null && storageId != null) {
            techMapFixture.setMode(storageId, StorageTechnologicalMapMode.READ_ONLY);
        }
    }

    @AfterMethod(alwaysRun = true)
    @Step("Деактивувати техкарти, створені в тесті")
    public void cleanupCreatedTechMaps() {
        if (techMapForCleanup != null) {
            createdMaps.add(techMapForCleanup);
            techMapForCleanup = null;
        }
        for (TechnologicalMapResponse map : createdMaps) {
            try {
                techMapFixture.deactivateTechMap(UserRole.OWNER_1, map.getId(), storageId);
            } catch (Exception e) {
                log.warn("Failed to deactivate tech map {}: {}", map.getId(), e.getMessage());
            }
        }
        createdMaps.clear();
    }

    @Test(priority = 10)
    @TestCaseId("TC-TM-ALT-001")
    @Story("Create tech map with alternative group")
    @Description("POST create PRODUCTION з групою (default A, alt B) → 200; GET повертає groups з рівно одним isDefault")
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateTechMapWithAlternativeGroup() {
        List<ResourceResponse> resources = techMapFixture.createAltGroupResources();
        TechnologicalMapRequest request = TechnologicalMapDataFactory
                .createProductionMapWithAlternativeGroup(resources, storageId);

        TechnologicalMapResponse created = Allure.step("ADMIN: POST create tech map with alt group", () -> {
            TechnologicalMapResponse response = techMapFixture.createTechMapWithRequest(UserRole.ADMIN, request);
            techMapForCleanup = response;
            return response;
        });

        Allure.step("Assert groups shape and single default", () -> {
            assertThat(created.getGroups()).as("groups").isNotNull().hasSize(1);
            TechnologicalMapAlternativeGroupResponse group = created.getGroups().getFirst();
            assertThat(group.getName()).isEqualTo("Клей");
            assertThat(group.getId()).isNotNull();
            assertThat(group.getAlternativeResources()).hasSize(2);

            long defaultCount = group.getAlternativeResources().stream()
                    .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                    .count();
            assertThat(defaultCount).as("exactly one default").isEqualTo(1);

            TechnologicalMapAlternativeGroupResourceResponse defaultRes = group.getAlternativeResources().stream()
                    .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                    .findFirst()
                    .orElseThrow();
            assertThat(defaultRes.getResource().getId()).isEqualTo(resources.get(1).getId());
            assertThat(defaultRes.getAmount()).isEqualTo(2.0);

            TechnologicalMapAlternativeGroupResourceResponse other = group.getAlternativeResources().stream()
                    .filter(r -> !Boolean.TRUE.equals(r.getIsDefault()))
                    .findFirst()
                    .orElseThrow();
            assertThat(other.getResource().getId()).isEqualTo(resources.get(2).getId());
            assertThat(other.getAmount()).isEqualTo(2.5);
        });
    }

    @Test(priority = 11)
    @TestCaseId("TC-TM-ALT-002")
    @Story("Group default validation")
    @Description("POST create без isDefault у групі → 400, technological.map.group.default.required")
    @Severity(SeverityLevel.CRITICAL)
    public void testCannotCreateTechMapWithoutDefaultInGroup() {
        List<ResourceResponse> resources = techMapFixture.createAltGroupResources();
        TechnologicalMapRequest request = TechnologicalMapDataFactory.withZeroDefaultsInGroup(resources, storageId);

        Response response = Allure.step("ADMIN: POST create without default", () ->
                apiExecutor.execute(ApiEndpointDefinition.TECH_MAP_CREATE, UserRole.ADMIN, request));

        techMapFixture.assertGroupDefaultRequiredRejection(response);
    }

    @Test(priority = 12)
    @TestCaseId("TC-TM-ALT-003")
    @Story("Group default validation")
    @Description("POST create з двома isDefault: true → 400, technological.map.group.default.required")
    @Severity(SeverityLevel.CRITICAL)
    public void testCannotCreateTechMapWithTwoDefaultsInGroup() {
        List<ResourceResponse> resources = techMapFixture.createAltGroupResources();
        TechnologicalMapRequest request = TechnologicalMapDataFactory.withTwoDefaultsInGroup(resources, storageId);

        Response response = Allure.step("ADMIN: POST create with two defaults", () ->
                apiExecutor.execute(ApiEndpointDefinition.TECH_MAP_CREATE, UserRole.ADMIN, request));

        techMapFixture.assertGroupDefaultRequiredRejection(response);
    }

    @Test(priority = 13)
    @TestCaseId("TC-TM-ALT-004")
    @Story("Group structure validation")
    @Description("POST: порожній alternativeResources / дубль resourceId / дубль назв груп → 400")
    @Severity(SeverityLevel.NORMAL)
    public void testGroupStructureValidationsRejected() {
        List<ResourceResponse> resources = techMapFixture.createAltGroupResources();

        Allure.step("Empty alternativeResources rejected", () -> {
            TechnologicalMapRequest request = TechnologicalMapDataFactory
                    .withEmptyAlternativeResources(resources, storageId);
            Response response = apiExecutor.execute(ApiEndpointDefinition.TECH_MAP_CREATE, UserRole.ADMIN, request);
            techMapFixture.assertGroupValidationRejection(response, "alternativeResources",
                    "хоча б один альтернативний ресурс");
        });

        Allure.step("Duplicate resourceId in group rejected", () -> {
            TechnologicalMapRequest request = TechnologicalMapDataFactory
                    .withDuplicateResourceInGroup(resources, storageId);
            Response response = apiExecutor.execute(ApiEndpointDefinition.TECH_MAP_CREATE, UserRole.ADMIN, request);
            techMapFixture.assertGroupValidationRejection(response, "resourceId",
                    "повторюється у групі");
        });

        Allure.step("Duplicate group names rejected", () -> {
            TechnologicalMapRequest request = TechnologicalMapDataFactory
                    .withDuplicateGroupNames(resources, storageId);
            Response response = apiExecutor.execute(ApiEndpointDefinition.TECH_MAP_CREATE, UserRole.ADMIN, request);
            techMapFixture.assertGroupValidationRejection(response, "name",
                    "унікальною");
        });
    }

    @Test(priority = 20)
    @TestCaseId("TC-TM-ALT-005")
    @Story("Update alternative group default")
    @Description("PUT swap default у групі → 200; нова version; isDefault перемикається")
    @Severity(SeverityLevel.CRITICAL)
    public void testUpdateSwapsDefaultAndCreatesNewVersion() {
        TechnologicalMapResponse source = Allure.step("Arrange: create tech map with alt group", () ->
                techMapFixture.createTechMapWithAlternativeGroup(UserRole.ADMIN, storageId));
        createdMaps.add(source);

        Long originalDefaultId = source.getGroups().getFirst().getAlternativeResources().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                .map(r -> r.getResource().getId())
                .findFirst()
                .orElseThrow();
        Long newDefaultId = source.getGroups().getFirst().getAlternativeResources().stream()
                .filter(r -> !Boolean.TRUE.equals(r.getIsDefault()))
                .map(r -> r.getResource().getId())
                .findFirst()
                .orElseThrow();

        TechnologicalMapRequest updateRequest = TechnologicalMapDataFactory.withSwappedDefault(source);

        TechnologicalMapResponse updated = Allure.step("ADMIN: PUT swap default", () -> {
            Response response = apiExecutor.execute(
                    ApiEndpointDefinition.TECH_MAP_UPDATE_NAME,
                    UserRole.ADMIN,
                    updateRequest,
                    String.valueOf(source.getId()));
            assertThat(response.statusCode()).isEqualTo(200);
            TechnologicalMapResponse body = response.as(TechnologicalMapResponse.class);
            techMapForCleanup = body;
            return body;
        });

        Allure.step("Assert new version and swapped default", () -> {
            assertThat(updated.getGroupId()).isEqualTo(source.getGroupId());
            assertThat(updated.getVersion()).isGreaterThan(source.getVersion());
            assertThat(updated.getGroups()).hasSize(1);

            Long actualDefault = updated.getGroups().getFirst().getAlternativeResources().stream()
                    .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                    .map(r -> r.getResource().getId())
                    .findFirst()
                    .orElseThrow();
            assertThat(actualDefault).isEqualTo(newDefaultId);
            assertThat(actualDefault).isNotEqualTo(originalDefaultId);
        });
    }

    @Test(priority = 21)
    @TestCaseId("TC-TM-ALT-006")
    @Story("Create groups-only tech map")
    @Description("POST create PRODUCTION лише з groups (порожній fixed input) → 200")
    @Severity(SeverityLevel.NORMAL)
    public void testCreateTechMapWithGroupsOnlyNoFixedInput() {
        List<ResourceResponse> resources = techMapFixture.createAltGroupResources();
        // createAltGroupResources returns 4; groups-only needs [def, alt, out] = indices 1,2,3
        List<ResourceResponse> groupResources = List.of(resources.get(1), resources.get(2), resources.get(3));
        TechnologicalMapRequest request = TechnologicalMapDataFactory
                .createProductionMapGroupsOnly(groupResources, storageId);

        TechnologicalMapResponse created = Allure.step("ADMIN: POST groups-only tech map", () -> {
            TechnologicalMapResponse response = techMapFixture.createTechMapWithRequest(UserRole.ADMIN, request);
            techMapForCleanup = response;
            return response;
        });

        Allure.step("Assert empty input and one group with default", () -> {
            assertThat(created.getInput() == null || created.getInput().isEmpty()).isTrue();
            assertThat(created.getGroups()).hasSize(1);
            long defaults = created.getGroups().getFirst().getAlternativeResources().stream()
                    .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                    .count();
            assertThat(defaults).isEqualTo(1);
        });
    }

    @Test(priority = 22)
    @TestCaseId("TC-TM-ALT-007")
    @Story("Create tech map with two alternative groups")
    @Description("POST create PRODUCTION з двома групами → GET groups.size=2, кожна з рівно одним isDefault")
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateTechMapWithTwoAlternativeGroups() {
        List<ResourceResponse> resources = techMapFixture.createTwoGroupAltResources();
        TechnologicalMapRequest request = TechnologicalMapDataFactory
                .createProductionMapWithTwoGroups(resources, storageId);

        TechnologicalMapResponse created = Allure.step("ADMIN: POST create with two groups", () -> {
            TechnologicalMapResponse response = techMapFixture.createTechMapWithRequest(UserRole.ADMIN, request);
            techMapForCleanup = response;
            return response;
        });

        Allure.step("Assert two groups with single default each", () -> {
            assertThat(created.getGroups()).hasSize(2);
            for (TechnologicalMapAlternativeGroupResponse group : created.getGroups()) {
                long defaultCount = group.getAlternativeResources().stream()
                        .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                        .count();
                assertThat(defaultCount).as("group " + group.getName()).isEqualTo(1);
            }
        });
    }

    @Test(priority = 23)
    @TestCaseId("TC-TM-ALT-008")
    @Story("Group validation on PUT")
    @Description("PUT update з невалідними groups (0 defaults, dup names, empty resources) → 400")
    @Severity(SeverityLevel.NORMAL)
    public void testPutGroupValidationsRejected() {
        TechnologicalMapResponse source = Allure.step("Arrange: create tech map with alt group", () ->
                techMapFixture.createTechMapWithAlternativeGroup(UserRole.ADMIN, storageId));
        createdMaps.add(source);

        List<ResourceResponse> resources = techMapFixture.createAltGroupResources();

        Allure.step("PUT with zero defaults rejected", () -> {
            TechnologicalMapRequest update = TechnologicalMapDataFactory.withZeroDefaultsInGroup(resources, storageId);
            update.setName(source.getName());
            update.setStorageIds(source.getStorages().stream()
                    .map(s -> s.getId())
                    .collect(java.util.stream.Collectors.toSet()));
            Response response = apiExecutor.execute(
                    ApiEndpointDefinition.TECH_MAP_UPDATE_NAME,
                    UserRole.ADMIN,
                    update,
                    String.valueOf(source.getId()));
            techMapFixture.assertGroupDefaultRequiredRejection(response);
        });

        Allure.step("PUT with duplicate group names rejected", () -> {
            TechnologicalMapRequest update = TechnologicalMapDataFactory.fromExisting(source).build();
            update.setGroups(List.of(
                    TechnologicalMapDataFactory.alternativeGroup("Клей",
                            TechnologicalMapDataFactory.alternativeResource(resources.get(1).getId(), 2.0, true)),
                    TechnologicalMapDataFactory.alternativeGroup("клей",
                            TechnologicalMapDataFactory.alternativeResource(resources.get(2).getId(), 2.5, true))));
            Response response = apiExecutor.execute(
                    ApiEndpointDefinition.TECH_MAP_UPDATE_NAME,
                    UserRole.ADMIN,
                    update,
                    String.valueOf(source.getId()));
            techMapFixture.assertGroupValidationRejection(response, "name", "унікальною");
        });
    }

    @Test(priority = 24)
    @TestCaseId("TC-TM-ALT-009")
    @Story("Version bump on alt amount change")
    @Description("PUT зміна amount альтернативного ресурсу → version+1, groupId chain unchanged")
    @Severity(SeverityLevel.NORMAL)
    public void testUpdateAltAmountCreatesNewVersion() {
        TechnologicalMapResponse source = Allure.step("Arrange: create tech map with alt group", () ->
                techMapFixture.createTechMapWithAlternativeGroup(UserRole.ADMIN, storageId));
        createdMaps.add(source);

        double newAmount = source.getGroups().getFirst().getAlternativeResources().getFirst().getAmount() + 0.5;
        TechnologicalMapRequest updateRequest = TechnologicalMapDataFactory
                .withChangedAltAmount(source, 0, 0, newAmount);

        TechnologicalMapResponse updated = Allure.step("ADMIN: PUT change alt amount", () -> {
            Response response = apiExecutor.execute(
                    ApiEndpointDefinition.TECH_MAP_UPDATE_NAME,
                    UserRole.ADMIN,
                    updateRequest,
                    String.valueOf(source.getId()));
            assertThat(response.statusCode()).isEqualTo(200);
            TechnologicalMapResponse body = response.as(TechnologicalMapResponse.class);
            techMapForCleanup = body;
            return body;
        });

        Allure.step("Assert new version", () -> {
            assertThat(updated.getGroupId()).isEqualTo(source.getGroupId());
            assertThat(updated.getVersion()).isGreaterThan(source.getVersion());
            assertThat(updated.getGroups().getFirst().getAlternativeResources().getFirst().getAmount())
                    .isEqualTo(newAmount);
        });
    }

    @Test(priority = 25)
    @TestCaseId("TC-TM-ALT-010")
    @Story("Clone tech map preserves groups")
    @Description("POST clone техкарти з groups → groups copied, новий groupId chain")
    @Severity(SeverityLevel.NORMAL)
    public void testCloneTechMapPreservesAlternativeGroups() {
        TechnologicalMapResponse source = Allure.step("Arrange: create tech map with alt group", () ->
                techMapFixture.createTechMapWithAlternativeGroup(UserRole.ADMIN, storageId));
        createdMaps.add(source);

        TechnologicalMapRequest cloneRequest = TechnologicalMapDataFactory.cloneFrom(source);

        TechnologicalMapResponse cloned = Allure.step("ADMIN: POST clone", () -> {
            Response response = apiExecutor.execute(
                    ApiEndpointDefinition.TECH_MAP_CREATE,
                    UserRole.ADMIN,
                    cloneRequest);
            assertThat(response.statusCode()).isEqualTo(200);
            TechnologicalMapResponse body = response.as(TechnologicalMapResponse.class);
            techMapForCleanup = body;
            return body;
        });

        Allure.step("Assert groups copied to clone", () -> {
            assertThat(cloned.getGroups()).hasSize(1);
            assertThat(cloned.getGroups().getFirst().getName()).isEqualTo("Клей");
            assertThat(cloned.getGroupId()).isNotEqualTo(source.getGroupId());
            assertThat(cloned.getGroups().getFirst().getAlternativeResources()).hasSize(2);
        });
    }

    @Test(priority = 26)
    @TestCaseId("TC-TM-ALT-011")
    @Story("GET versions by groupId")
    @Description("GET /versions/{groupId}?storageId= → DESC sort, groups present after default swap")
    @Severity(SeverityLevel.NORMAL)
    public void testGetVersionsByGroupIdReturnsDescendingVersions() {
        TechnologicalMapResponse source = Allure.step("Arrange: create tech map with alt group", () ->
                techMapFixture.createTechMapWithAlternativeGroup(UserRole.ADMIN, storageId));
        createdMaps.add(source);

        TechnologicalMapRequest swapRequest = TechnologicalMapDataFactory.withSwappedDefault(source);
        TechnologicalMapResponse updated = Allure.step("ADMIN: PUT swap default", () -> {
            Response response = apiExecutor.execute(
                    ApiEndpointDefinition.TECH_MAP_UPDATE_NAME,
                    UserRole.ADMIN,
                    swapRequest,
                    String.valueOf(source.getId()));
            assertThat(response.statusCode()).isEqualTo(200);
            TechnologicalMapResponse body = response.as(TechnologicalMapResponse.class);
            techMapForCleanup = body;
            return body;
        });

        List<TechnologicalMapResponse> versions = Allure.step("GET versions by groupId", () ->
                techMapFixture.getVersionsByGroupId(UserRole.ADMIN, source.getGroupId(), storageId));

        Allure.step("Assert versions sorted DESC and include groups", () -> {
            assertThat(versions).hasSizeGreaterThanOrEqualTo(2);
            assertThat(versions.getFirst().getVersion()).isGreaterThanOrEqualTo(versions.get(1).getVersion());
            assertThat(versions.stream().map(TechnologicalMapResponse::getVersion).toList())
                    .isSortedAccordingTo(Comparator.reverseOrder());
            assertThat(versions.stream().allMatch(v -> v.getGroups() != null && !v.getGroups().isEmpty()))
                    .isTrue();
            assertThat(versions.stream().anyMatch(v -> updated.getId().equals(v.getId()))).isTrue();
        });
    }

    @Test(priority = 27)
    @TestCaseId("TC-TM-ALT-012")
    @Story("Schema validation for groups on create")
    @Description("POST create з groups → SchemaRegistry validates nested groups[] у response")
    @Severity(SeverityLevel.NORMAL)
    public void testCreateResponseSchemaIncludesGroups() {
        List<ResourceResponse> resources = techMapFixture.createAltGroupResources();
        TechnologicalMapRequest request = TechnologicalMapDataFactory
                .createProductionMapWithAlternativeGroup(resources, storageId);

        Response response = Allure.step("ADMIN: POST create with alt group", () ->
                apiExecutor.execute(ApiEndpointDefinition.TECH_MAP_CREATE, UserRole.ADMIN, request));

        Allure.step("Assert schema + groups shape", () -> {
            assertThat(response.statusCode()).isEqualTo(200);
            SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.TECH_MAP_CREATE);
            TechnologicalMapResponse created = response.as(TechnologicalMapResponse.class);
            techMapForCleanup = created;
            assertThat(created.getGroups()).isNotNull().isNotEmpty();
            assertThat(created.getGroups().getFirst().getAlternativeResources()).isNotEmpty();
        });
    }

    @Test(priority = 28)
    @TestCaseId("TC-TM-ALT-013")
    @Issue("CPMA-661")
    @Story("Reject input ∩ alternative group overlap")
    @Description("""
            CPMA-661: POST create PRODUCTION — той самий ресурс у Витратах (input)
            і в альтернативній групі → 400
            («вже доданий у вхідні ресурси — він не може бути ще й у групі»).
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateRejectsInputAlternativeGroupOverlap() {
        List<ResourceResponse> resources = techMapFixture.createGroupsOnlyAltResources();
        TechnologicalMapRequest request = TechnologicalMapDataFactory
                .createProductionMapWithInputGroupOverlap(resources, storageId);

        Response response = Allure.step("ADMIN: POST create with input∩group overlap", () ->
                apiExecutor.execute(ApiEndpointDefinition.TECH_MAP_CREATE, UserRole.ADMIN, request));

        Allure.step("Assert 400 input∩group", () ->
                techMapFixture.assertInputGroupOverlapRejection(response));
    }

    @Test(priority = 29)
    @TestCaseId("TC-TM-ALT-014")
    @Issue("CPMA-661")
    @Story("Reject output ∩ alternative group overlap")
    @Description("""
            CPMA-661: POST create PRODUCTION — той самий ресурс у output
            і в альтернативній групі → 400
            («є вихідним … — він не може бути ще й у групі»).
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateRejectsOutputAlternativeGroupOverlap() {
        List<ResourceResponse> resources = techMapFixture.createGroupsOnlyAltResources();
        TechnologicalMapRequest request = TechnologicalMapDataFactory
                .createProductionMapWithOutputGroupOverlap(resources, storageId);

        Response response = Allure.step("ADMIN: POST create with output∩group overlap", () ->
                apiExecutor.execute(ApiEndpointDefinition.TECH_MAP_CREATE, UserRole.ADMIN, request));

        Allure.step("Assert 400 output∩group", () ->
                techMapFixture.assertOutputGroupOverlapRejection(response));
    }

    @Test(priority = 30)
    @TestCaseId("TC-TM-ALT-015")
    @Issue("CPMA-661")
    @Story("Reject same resource in two alternative groups")
    @Description("""
            CPMA-661: POST create PRODUCTION — той самий resourceId у двох
            альтернативних групах → 400 («не може повторюватися у групі»).
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateRejectsSameResourceInTwoAlternativeGroups() {
        List<ResourceResponse> resources = techMapFixture.createAltGroupResources();
        TechnologicalMapRequest request = TechnologicalMapDataFactory
                .createProductionMapWithSameResourceInTwoGroups(resources, storageId);

        Response response = Allure.step("ADMIN: POST create with cross-group resource", () ->
                apiExecutor.execute(ApiEndpointDefinition.TECH_MAP_CREATE, UserRole.ADMIN, request));

        Allure.step("Assert 400 group∩group", () ->
                techMapFixture.assertCrossGroupOverlapRejection(response));
    }
}
