package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.StorageRelation;
import com.erp.enums.UnitType;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.IsolatedRestrictedOwnerScope;
import com.erp.fixtures.UserFixture;
import com.erp.models.request.StorageRequest;
import com.erp.models.response.StorageRegionResponse;
import com.erp.models.response.StorageResponse;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Видимість локацій у селекторах ({@code GET /storages/names}, {@code /names/my-units})
 * для підрозділів з {@code accessMode=FULL_ACCESS} vs {@code REGIONS}.
 * <p>Передумова класу: {@link IsolatedRestrictedOwnerScope} створює два leaf UNIT і двох
 * Keycloak-юзерів — REGIONS (сесія {@code OWNER_2}) і FULL_ACCESS (сесія {@code OWNER_1}).
 * Shared {@code bar}/{@code alkatras} не змінюються. Після класу юзери деактивуються,
 * залишок знімається інвентаризацією, UNIT архівуються.
 */
@Slf4j
@Epic("Master Data")
@Feature("Storages")
@Story("Storage Visibility Scope")
public class StorageVisibilityTest extends StorageApiTestBase {

    private Long owner1StorageId;
    private Long owner2StorageId;
    private IsolatedRestrictedOwnerScope isolatedOwnerScope;

    @BeforeClass(alwaysRun = true)
    @Step("Підготовка: isolated REGIONS + FULL_ACCESS UNIT і Keycloak owners")
    public void setupStorageVisibilityTest() {
        SchemaRegistry.logSchemaCoverage();
        isolatedOwnerScope = new IsolatedRestrictedOwnerScope(
                storageFixture,
                new UserFixture(testContext, apiExecutor),
                apiExecutor,
                getPlaywrightSessionProvider());
        owner2StorageId = isolatedOwnerScope.acquire();
        owner1StorageId = isolatedOwnerScope.acquireFullAccessOwner();
        regionFixture.purgeViewerVisibilityScope(UserRole.ADMIN, owner2StorageId, storageFixture);
        regionFixture.purgeRegionsByNamePrefixes(
                UserRole.ADMIN, "str-reg-", "crew-", "rel-vis-", "vis-");
    }

    @AfterClass(alwaysRun = true)
    public void restoreOwner2AndIsolatedUnit() {
        if (isolatedOwnerScope != null) {
            isolatedOwnerScope.release();
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-STR-REG-020")
    @Description("""
            Що перевіряємо: створення підрозділу з рівнем доступу RESTRICTED (бекенд: accessMode=REGIONS).
            Тестові дані: дочірня локація vis-reg- під parent unit; POST як ADMIN.
            Очікування: HTTP 200, accessMode=REGIONS у POST response та GET by id.
            Cleanup: локація архівується через StorageApiTestBase.
            """ + StorageRegionsAllureDescriptions.ON_FAIL_API)
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateRestrictedStorage() {
        StorageResponse parent = storageFixture.resolveParentUnit();
        StorageRequest request = StorageDataFactory.restrictedStorage(parent.getId(), "vis-reg-").build();

        StorageResponse created = storageFixture.createStorage(request);

        assertThat(created.getAccessMode()).isEqualTo(StorageAccessMode.REGIONS.name());
        StorageResponse fetched = storageFixture.getById(UserRole.ADMIN, created.getId());
        assertThat(fetched.getAccessMode()).isEqualTo(StorageAccessMode.REGIONS.name());
    }

    @Test(priority = 15)
    @TestCaseId("TC-STR-REG-021")
    @Description("""
            Бізнес-вимога (п.1): підрозділ з FULL_ACCESS бачить усю організаційну структуру в селекторах;
            підрозділ з REGIONS (RESTRICTED) — лише власний id, доки не відкриють області видимості.
            
            Що перевіряємо: контраст видимості двох ephemeral owner-сесій на
            GET /api/v1/storages/names?isActive=true.
            
            Передумови (@BeforeClass):
            - OWNER_2 — новий Keycloak-юзер на leaf UNIT з accessMode=REGIONS;
            - OWNER_1 — новий Keycloak-юзер на leaf UNIT з accessMode=FULL_ACCESS;
            - stand {@code bar}/{@code alkatras} не використовуються.
            
            Тестові дані: нові області/members для OWNER_2 НЕ створюються — порівняння «as-is».
            
            Кроки перевірки:
            1) ADMIN GET підтверджує accessMode обох isolated UNIT;
            2) OWNER_1 і OWNER_2 окремо викликають /storages/names?isActive=true;
            3) OWNER_2 має рівно 1 локацію — власний підрозділ;
            4) OWNER_1 має суворо більше записів, ніж OWNER_2 (ширша видимість FULL_ACCESS).
            
            Очікування: owner2Names.size()==1 і owner1Names.size() > owner2Names.size().
            Не перевіряємо: точний склад списку OWNER_1 (залежить від оргдерева стенда).
            """ + StorageRegionsAllureDescriptions.ON_FAIL_API)
    @Severity(SeverityLevel.NORMAL)
    public void testFullAccessOwnerSeesMoreThanRestrictedOwner() {
        Allure.step("STEP 1: ADMIN — accessMode isolated owner-storages", () -> {
            StorageResponse owner1Storage = storageFixture.getById(UserRole.ADMIN, owner1StorageId);
            StorageResponse owner2Storage = storageFixture.getById(UserRole.ADMIN, owner2StorageId);

            Allure.parameter("owner1AccessMode", owner1Storage.getAccessMode());
            Allure.parameter("owner1StorageId", owner1StorageId);
            Allure.parameter("owner2StorageId", owner2StorageId);

            assertThat(owner1Storage.getAccessMode())
                    .as("isolated OWNER_1 UNIT (id=%d) створено як FULL_ACCESS", owner1StorageId)
                    .isEqualTo(StorageAccessMode.FULL_ACCESS.name());
            assertThat(owner2Storage.getAccessMode())
                    .as("isolated OWNER_2 UNIT (id=%d) створено як REGIONS", owner2StorageId)
                    .isEqualTo(StorageAccessMode.REGIONS.name());
        });

        List<StorageResponse> owner1Names = Allure.step(
                "STEP 2: OWNER_1 (FULL_ACCESS) — GET /storages/names?isActive=true", () ->
                        storageFixture.getNames(UserRole.OWNER_1, true, null));

        List<StorageResponse> owner2Names = Allure.step(
                "STEP 3: OWNER_2 (REGIONS) — GET /storages/names?isActive=true", () ->
                        storageFixture.getNames(UserRole.OWNER_2, true, null));

        Allure.step("STEP 4: Порівняння розміру списків видимості", () -> {
            log.info("TC-STR-REG-021: isolated FULL_ACCESS names count={}, REGIONS names count={}",
                    owner1Names.size(), owner2Names.size());

            assertThat(owner2Names.stream().map(StorageResponse::getId).toList())
                    .as("REGIONS owner без областей видимості бачить лише власний підрозділ (id=%d)",
                            owner2StorageId)
                    .containsExactly(owner2StorageId);

            assertThat(owner1Names.size())
                    .as("FULL_ACCESS owner бачить ширший перелік локацій у селекторі, ніж REGIONS owner")
                    .isGreaterThan(owner2Names.size());
        });

        Allure.step("STEP 5: OWNER_1 GET by id — власний storage доступний незалежно від пагінації /names", () -> {
            StorageResponse owner1Direct = storageFixture.getById(UserRole.OWNER_1, owner1StorageId);
            assertThat(owner1Direct.getId()).isEqualTo(owner1StorageId);
        });
    }

    @Test(priority = 20)
    @TestCaseId("TC-STR-REG-022")
    @Description("""
            Що перевіряємо: REGIONS-підрозділ без прив'язаних областей бачить лише себе в селекторі.
            Тестові дані: створюємо «чужу» локацію vis-foreign- (не в жодній області OWNER_2);
            GET /storages/names?isActive=true як OWNER_2.
            Очікування: рівно 1 елемент — owner2.storage.id; foreign.id відсутній.
            Примітка: тест має priority=20, до створення областей у наступних тестах.
            """ + StorageRegionsAllureDescriptions.ON_FAIL_API)
    @Severity(SeverityLevel.CRITICAL)
    public void testRestrictedOwnerSeesOnlyOwnUnitWithoutRegions() {
        StorageResponse foreign = storageFixture.createUniqueStorage("vis-foreign-");

        List<StorageResponse> names = storageFixture.getNames(UserRole.OWNER_2, true, null);

        assertThat(names.stream().map(StorageResponse::getId).toList())
                .containsExactly(owner2StorageId);
        assertThat(names.stream().map(StorageResponse::getId)).doesNotContain(foreign.getId());
    }

    @Test(priority = 30)
    @TestCaseId("TC-STR-REG-030")
    @Description("""
            Що перевіряємо: область з accessMode=REGIONS показує recipient під ім'ям області (аліас),
            а не реальною назвою локації; сторонні локації приховані.
            Тестові дані: recipient vis-rec-, shared vis-shared-, outsider vis-out-;
            область vis-reg-r- (REGIONS) з locations=[recipient, shared], member=owner2StorageId.
            Очікування: OWNER_2 /names містить owner2 + region.name; немає outsider.id і recipient.name.
            """ + StorageRegionsAllureDescriptions.ON_FAIL_API)
    @Severity(SeverityLevel.CRITICAL)
    public void testRegionsModeShowsRegionNameInSelector() {
        StorageResponse recipient = storageFixture.createUniqueStorage("vis-rec-");
        StorageResponse shared = storageFixture.createUniqueStorage("vis-shared-");
        StorageResponse outsider = storageFixture.createUniqueStorage("vis-out-");

        StorageRegionResponse region = regionFixture.createRegion(
                recipient, StorageAccessMode.REGIONS, "vis-reg-r-");
        regionFixture.addRegionLocations(region.getId(), recipient.getId(), shared.getId());
        regionFixture.addRegionMembers(region.getId(), owner2StorageId);

        List<StorageResponse> names = storageFixture.getNames(UserRole.OWNER_2, true, null);
        List<String> visibleNames = names.stream().map(StorageResponse::getName).toList();

        assertThat(names.stream().map(StorageResponse::getId))
                .contains(owner2StorageId)
                .doesNotContain(outsider.getId());
        assertThat(visibleNames).contains(region.getName());
        assertThat(visibleNames).doesNotContain(recipient.getName());
    }

    @Test(priority = 40)
    @TestCaseId("TC-STR-REG-031")
    @Description("""
            Що перевіряємо: область з accessMode=FULL_ACCESS відкриває реальні імена локацій з набору
            (або alias, див. TC-STR-REG-040), а не лише ім'я області.
            Тестові дані: recipient vis-far-rec-, location vis-far-loc-, outsider vis-far-out-;
            область vis-far- (FULL_ACCESS), locations=[recipient, location], member=owner2StorageId.
            Очікування: OWNER_2 /names містить owner2, location.id з name=location.name; outsider відсутній.
            """ + StorageRegionsAllureDescriptions.ON_FAIL_API)
    @Severity(SeverityLevel.CRITICAL)
    public void testFullAccessRegionShowsLocationNames() {
        StorageResponse recipient = storageFixture.createUniqueStorage("vis-far-rec-");
        StorageResponse location = storageFixture.createUniqueStorage("vis-far-loc-");
        StorageResponse outsider = storageFixture.createUniqueStorage("vis-far-out-");

        StorageRegionResponse region = regionFixture.createRegion(
                recipient, StorageAccessMode.FULL_ACCESS, "vis-far-");
        regionFixture.addRegionLocations(region.getId(), recipient.getId(), location.getId());
        regionFixture.addRegionMembers(region.getId(), owner2StorageId);

        List<StorageResponse> names = storageFixture.getNames(UserRole.OWNER_2, true, null);
        List<Long> visibleIds = names.stream().map(StorageResponse::getId).toList();

        assertThat(visibleIds)
                .contains(owner2StorageId, location.getId())
                .doesNotContain(outsider.getId());
        assertThat(names.stream().filter(s -> Objects.equals(s.getId(), location.getId()))
                .map(StorageResponse::getName)
                .findFirst()
                .orElseThrow())
                .isEqualTo(location.getName());
    }

    @Test(priority = 50)
    @TestCaseId("TC-STR-REG-032")
    @Description("""
            Що перевіряємо: member у двох областях отримує union видимості (об'єднання наборів).
            Тестові дані: region1→loc1, region2→loc2; OWNER_2 member в обох областях (FULL_ACCESS).
            Очікування: GET /storages/names як OWNER_2 містить loc1.id і loc2.id одночасно.
            """ + StorageRegionsAllureDescriptions.ON_FAIL_API)
    @Severity(SeverityLevel.NORMAL)
    public void testTwoRegionsUnionForMember() {
        StorageResponse recipient1 = storageFixture.createUniqueStorage("vis-uni-r1-");
        StorageResponse recipient2 = storageFixture.createUniqueStorage("vis-uni-r2-");
        StorageResponse loc1 = storageFixture.createUniqueStorage("vis-uni-l1-");
        StorageResponse loc2 = storageFixture.createUniqueStorage("vis-uni-l2-");

        StorageRegionResponse region1 = regionFixture.createRegion(
                recipient1, StorageAccessMode.FULL_ACCESS, "vis-uni-1-");
        StorageRegionResponse region2 = regionFixture.createRegion(
                recipient2, StorageAccessMode.FULL_ACCESS, "vis-uni-2-");

        regionFixture.addRegionLocations(region1.getId(), loc1.getId());
        regionFixture.addRegionLocations(region2.getId(), loc2.getId());
        regionFixture.addRegionMembers(region1.getId(), owner2StorageId);
        regionFixture.addRegionMembers(region2.getId(), owner2StorageId);

        List<Long> visibleIds = storageFixture.getNames(UserRole.OWNER_2, true, null).stream()
                .map(StorageResponse::getId)
                .toList();

        assertThat(visibleIds).contains(loc1.getId(), loc2.getId());
        assertThat(new HashSet<>(visibleIds))
                .as("Union двох областей не має дублювати id у /storages/names")
                .hasSize(visibleIds.size());
    }

    @Test(priority = 54, dataProvider = "threeRegionsSharedLocationDedupScenarios")
    @TestCaseId("TC-STR-REG-035")
    @Description("""
            Розширення TC-STR-REG-034: member у трьох областях з однією спільною локацією —
            GET /storages/names?isActive=true не дублює storage.id незалежно від type локації
            та accessMode областей (FULL_ACCESS, REGIONS, змішані).
            """ + StorageRegionsAllureDescriptions.ON_FAIL_API)
    @Severity(SeverityLevel.CRITICAL)
    public void testMemberInThreeRegionsNoDuplicateIdsAcrossTypesAndAccessModes(
            String scenarioLabel,
            UnitType sharedLocationType,
            StorageAccessMode regionMode1,
            StorageAccessMode regionMode2,
            StorageAccessMode regionMode3) {
        Allure.parameter("scenario", scenarioLabel);
        Allure.parameter("sharedLocationType", sharedLocationType.name());
        Allure.parameter("regionModes", regionMode1 + "/" + regionMode2 + "/" + regionMode3);

        String prefix = "vis-dedup-v2-"
                + sharedLocationType.name().toLowerCase() + "-"
                + regionMode1.name().charAt(0)
                + regionMode2.name().charAt(0)
                + regionMode3.name().charAt(0) + "-";

        StorageResponse sharedLocation = createSharedLocation(sharedLocationType, prefix + "loc-");
        StorageResponse recipient1 = storageFixture.createUniqueStorage(prefix + "r1-");
        StorageResponse recipient2 = storageFixture.createUniqueStorage(prefix + "r2-");
        StorageResponse recipient3 = storageFixture.createUniqueStorage(prefix + "r3-");

        StorageRegionResponse region1 = regionFixture.createRegion(
                recipient1, regionMode1, prefix + "reg1-");
        StorageRegionResponse region2 = regionFixture.createRegion(
                recipient2, regionMode2, prefix + "reg2-");
        StorageRegionResponse region3 = regionFixture.createRegion(
                recipient3, regionMode3, prefix + "reg3-");

        linkSharedLocationToThreeRegions(sharedLocation, region1, region2, region3);

        assertSharedLocationVisibleOnceInActiveNames(
                sharedLocation, scenarioLabel, regionMode1, regionMode2, regionMode3);
    }

    @DataProvider(name = "threeRegionsSharedLocationDedupScenarios")
    public Object[][] threeRegionsSharedLocationDedupScenarios() {
        return new Object[][] {
                {
                        "STORAGE location, усі області FULL_ACCESS",
                        UnitType.STORAGE,
                        StorageAccessMode.FULL_ACCESS,
                        StorageAccessMode.FULL_ACCESS,
                        StorageAccessMode.FULL_ACCESS
                },
                {
                        "UNIT location, усі області FULL_ACCESS",
                        UnitType.UNIT,
                        StorageAccessMode.FULL_ACCESS,
                        StorageAccessMode.FULL_ACCESS,
                        StorageAccessMode.FULL_ACCESS
                },
                {
                        "PRODUCTION location, усі області FULL_ACCESS",
                        UnitType.PRODUCTION,
                        StorageAccessMode.FULL_ACCESS,
                        StorageAccessMode.FULL_ACCESS,
                        StorageAccessMode.FULL_ACCESS
                },
                {
                        "STORAGE location, усі області REGIONS",
                        UnitType.STORAGE,
                        StorageAccessMode.REGIONS,
                        StorageAccessMode.REGIONS,
                        StorageAccessMode.REGIONS
                },
                {
                        "UNIT location, усі області REGIONS",
                        UnitType.UNIT,
                        StorageAccessMode.REGIONS,
                        StorageAccessMode.REGIONS,
                        StorageAccessMode.REGIONS
                },
                {
                        "STORAGE location, змішані FULL_ACCESS + REGIONS",
                        UnitType.STORAGE,
                        StorageAccessMode.FULL_ACCESS,
                        StorageAccessMode.REGIONS,
                        StorageAccessMode.FULL_ACCESS
                },
        };
    }

    @Test(priority = 55)
    @TestCaseId("TC-STR-REG-034")
    @Description("""
            Member у трьох областях з однією спільною локацією — GET /storages/names?isActive=true
            містить локацію рівно один раз (без дублікатів id).
            Регресія: dropdown «Кому відправляю» не показує один підрозділ кілька разів.
            """ + StorageRegionsAllureDescriptions.ON_FAIL_API)
    @Severity(SeverityLevel.CRITICAL)
    public void testMemberInThreeRegionsNoDuplicateIdsInActiveNames() {
        StorageResponse sharedLocation = createSharedLocation(UnitType.STORAGE, "vis-dedup-loc-");
        StorageResponse recipient1 = storageFixture.createUniqueStorage("vis-dedup-r1-");
        StorageResponse recipient2 = storageFixture.createUniqueStorage("vis-dedup-r2-");
        StorageResponse recipient3 = storageFixture.createUniqueStorage("vis-dedup-r3-");

        StorageRegionResponse region1 = regionFixture.createRegion(
                recipient1, StorageAccessMode.FULL_ACCESS, "vis-dedup-1-");
        StorageRegionResponse region2 = regionFixture.createRegion(
                recipient2, StorageAccessMode.FULL_ACCESS, "vis-dedup-2-");
        StorageRegionResponse region3 = regionFixture.createRegion(
                recipient3, StorageAccessMode.FULL_ACCESS, "vis-dedup-3-");

        linkSharedLocationToThreeRegions(sharedLocation, region1, region2, region3);
        assertSharedLocationVisibleOnceInActiveNames(
                sharedLocation,
                "STORAGE + FULL_ACCESS (baseline TC-STR-REG-034)",
                StorageAccessMode.FULL_ACCESS,
                StorageAccessMode.FULL_ACCESS,
                StorageAccessMode.FULL_ACCESS);
    }

    private StorageResponse createSharedLocation(UnitType type, String namePrefix) {
        StorageResponse parent = storageFixture.resolveParentUnit();
        return storageFixture.createChildStorage(
                parent.getId(), namePrefix, type, StorageRelation.INTERNAL);
    }

    private void linkSharedLocationToThreeRegions(
            StorageResponse sharedLocation,
            StorageRegionResponse region1,
            StorageRegionResponse region2,
            StorageRegionResponse region3) {
        regionFixture.addRegionLocations(region1.getId(), sharedLocation.getId());
        regionFixture.addRegionLocations(region2.getId(), sharedLocation.getId());
        regionFixture.addRegionLocations(region3.getId(), sharedLocation.getId());
        regionFixture.addRegionMembers(region1.getId(), owner2StorageId);
        regionFixture.addRegionMembers(region2.getId(), owner2StorageId);
        regionFixture.addRegionMembers(region3.getId(), owner2StorageId);
    }

    private void assertSharedLocationVisibleOnceInActiveNames(
            StorageResponse sharedLocation,
            String scenarioLabel,
            StorageAccessMode regionMode1,
            StorageAccessMode regionMode2,
            StorageAccessMode regionMode3) {
        List<StorageResponse> names = storageFixture.getNames(UserRole.OWNER_2, true, null);
        List<Long> ids = names.stream().map(StorageResponse::getId).toList();

        boolean allRegionsAliasMode = regionMode1 == StorageAccessMode.REGIONS
                && regionMode2 == StorageAccessMode.REGIONS
                && regionMode3 == StorageAccessMode.REGIONS;

        if (allRegionsAliasMode) {
            // TC-STR-REG-030: REGIONS показує ім'я області, не storage.id спільної локації.
            assertThat(ids)
                    .as("[%s] REGIONS: спільна локація id=%d не віддається в /names як storage.id",
                            scenarioLabel, sharedLocation.getId())
                    .doesNotContain(sharedLocation.getId());
        } else {
            assertThat(ids)
                    .as("[%s] Спільна локація id=%d має бути у /names", scenarioLabel, sharedLocation.getId())
                    .contains(sharedLocation.getId());
            assertThat(ids.stream().filter(id -> id.equals(sharedLocation.getId())).count())
                    .as("[%s] Спільна локація з трьох областей — рівно один запис у /names", scenarioLabel)
                    .isEqualTo(1);
        }
        assertThat(new HashSet<>(ids))
                .as("[%s] Жодного дубліката storage.id у /storages/names?isActive=true", scenarioLabel)
                .hasSize(ids.size());
    }

    @Test(priority = 60)
    @TestCaseId("TC-STR-REG-033")
    @Description("""
            Що перевіряємо: селектор «мої підрозділи» для REGIONS owner не розширюється областями.
            Передумова: OWNER_2 JWT — рівно один business unit (owner2.storage.id).
            Тестові дані: OWNER_2 у REGIONS (можуть існувати області з попередніх тестів у класі).
            Очікування: GET /storages/names/my-units — рівно 1 internal unit = owner2.storage.id.
            """ + StorageRegionsAllureDescriptions.ON_FAIL_API)
    @Severity(SeverityLevel.NORMAL)
    public void testMyUnitsForRestrictedOwner() {
        List<StorageResponse> units = storageFixture.getMyUnits(UserRole.OWNER_2);

        assertThat(units).hasSize(1);
        assertThat(units.getFirst().getId()).isEqualTo(owner2StorageId);
    }

    @Test(priority = 70)
    @TestCaseId("TC-STR-REG-040")
    @Description("""
            Що перевіряємо: аліас локації (п.5 вимог) у FULL_ACCESS області відображається в /names
            замість реальної назви (COALESCE(alias, name) на бекенді).
            Тестові дані: STORAGE vis-alias- з явним alias; область FULL_ACCESS, member=OWNER_2.
            Очікування: у /names для aliasedLocation.id поле name дорівнює alias, не storage.name.
            """ + StorageRegionsAllureDescriptions.ON_FAIL_API)
    @Severity(SeverityLevel.CRITICAL)
    public void testAliasShownInFullAccessRegion() {
        StorageResponse parent = storageFixture.resolveParentUnit();
        String alias = StorageDataFactory.shortAlias();
        StorageRequest withAlias = StorageDataFactory.childStorage(parent.getId(), "vis-alias-")
                .alias(alias)
                .build();
        StorageResponse aliasedLocation = storageFixture.createStorage(withAlias);
        StorageResponse recipient = storageFixture.createUniqueStorage("vis-alias-rec-");

        StorageRegionResponse region = regionFixture.createRegion(
                recipient, StorageAccessMode.FULL_ACCESS, "vis-alias-reg-");
        regionFixture.addRegionLocations(region.getId(), aliasedLocation.getId());
        regionFixture.addRegionMembers(region.getId(), owner2StorageId);

        List<StorageResponse> names = storageFixture.getNames(UserRole.OWNER_2, true, null);
        StorageResponse visible = names.stream()
                .filter(s -> Objects.equals(s.getId(), aliasedLocation.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Aliased location not visible in /names"));

        assertThat(visible.getName()).isEqualTo(alias);
    }

    @Test(priority = 80)
    @TestCaseId("TC-STR-REG-052")
    @Description("""
            Що перевіряємо: explicit grant показує реальне ім'я локації (пріоритет над region-alias).
            Тестові дані: granted vis-exp-grant-; PUT /storages/{granted}/locations?locations={owner2StorageId}.
            Очікування: OWNER_2 /names містить granted.id з name=granted.name (не ім'я області).
            Модель: storage_id=granted, location_storage_id=owner2 у storage_location.
            """ + StorageRegionsAllureDescriptions.ON_FAIL_API)
    @Severity(SeverityLevel.CRITICAL)
    public void testExplicitGrantShowsRealName() {
        StorageResponse granted = storageFixture.createUniqueStorage("vis-exp-grant-");

        regionFixture.addExplicitLocations(granted.getId(), owner2StorageId);

        try {
            List<StorageResponse> names = storageFixture.getNames(UserRole.OWNER_2, true, null);
            StorageResponse visible = names.stream()
                    .filter(s -> Objects.equals(s.getId(), granted.getId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Explicitly granted location not in /names"));

            assertThat(visible.getName()).isEqualTo(granted.getName());
        } finally {
            try {
                regionFixture.removeExplicitLocations(granted.getId(), owner2StorageId);
            } catch (Exception e) {
                log.warn("TC-STR-REG-052 cleanup: failed to revoke explicit grant: {}", e.getMessage());
            }
        }
    }
}
