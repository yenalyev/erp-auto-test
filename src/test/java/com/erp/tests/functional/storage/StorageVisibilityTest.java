package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.UserRole;
import com.erp.models.request.StorageRequest;
import com.erp.models.response.StorageRegionResponse;
import com.erp.models.response.StorageResponse;
import com.erp.utils.config.ConfigProvider;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Видимість локацій у селекторах ({@code GET /storages/names}, {@code /names/my-units})
 * для підрозділів з {@code accessMode=FULL_ACCESS} vs {@code REGIONS}.
 * <p>Передумова класу: OWNER_2 storage ({@code owner2.storage.id} у config) тимчасово переводиться
 * в {@code REGIONS} у {@link #ensureOwner2RestrictedAccess()} і відновлюється в {@link #restoreOwner2AccessMode()}.
 * OWNER_2 використовується як «обмежений» owner; OWNER_1 — як порівняння з ширшою видимістю.
 */
@Slf4j
@Epic("Master Data")
@Feature("Storages")
@Story("Storage Visibility Scope")
public class StorageVisibilityTest extends StorageApiTestBase {

    private static final Object OWNER2_ACCESS_LOCK = new Object();

    private Long owner2StorageId;
    private String originalOwner2AccessMode;
    private boolean owner2AccessModeChanged;

    @BeforeClass(alwaysRun = true)
    @Step("Підготовка: OWNER_2 → REGIONS для visibility-тестів")
    public void setupStorageVisibilityTest() {
        SchemaRegistry.logSchemaCoverage();
        owner2StorageId = ConfigProvider.getOwner2StorageId();
        ensureOwner2RestrictedAccess();
    }

    @AfterClass(alwaysRun = true)
    public void restoreOwner2AccessMode() {
        restoreOwner2AccessIfChanged();
    }

    private void ensureOwner2RestrictedAccess() {
        synchronized (OWNER2_ACCESS_LOCK) {
            StorageResponse owner2Storage = storageFixture.getById(UserRole.ADMIN, owner2StorageId);
            originalOwner2AccessMode = owner2Storage.getAccessMode();
            if (!StorageAccessMode.REGIONS.name().equals(originalOwner2AccessMode)) {
                StorageRequest update = StorageDataFactory.withAccessMode(
                        owner2Storage, StorageAccessMode.REGIONS);
                storageFixture.update(UserRole.ADMIN, owner2StorageId, update);
                owner2AccessModeChanged = true;
                log.info("OWNER_2 storage {} temporarily set to REGIONS for visibility tests", owner2StorageId);
            }
        }
    }

    private void restoreOwner2AccessIfChanged() {
        if (!owner2AccessModeChanged || originalOwner2AccessMode == null) {
            return;
        }
        synchronized (OWNER2_ACCESS_LOCK) {
            try {
                StorageResponse current = storageFixture.getById(UserRole.ADMIN, owner2StorageId);
                StorageRequest restore = StorageDataFactory.withAccessMode(
                        current, StorageAccessMode.valueOf(originalOwner2AccessMode));
                storageFixture.update(UserRole.ADMIN, owner2StorageId, restore);
                log.info("OWNER_2 storage {} accessMode restored to {}", owner2StorageId, originalOwner2AccessMode);
            } catch (Exception e) {
                log.warn("Failed to restore OWNER_2 storage accessMode: {}", e.getMessage());
            }
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-STR-REG-020")
    @Description("""
            Що перевіряємо: створення підрозділу з рівнем доступу RESTRICTED (бекенд: accessMode=REGIONS).
            Тестові дані: дочірня локація vis-reg- під parent unit; POST як ADMIN.
            Очікування: HTTP 200, accessMode=REGIONS у POST response та GET by id.
            Cleanup: локація архівується через StorageApiTestBase.
            """)
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
            
            Що перевіряємо: контраст видимості двох реальних owner-сесій на одному ендпоінті
            GET /api/v1/storages/names?isActive=true (той самий, що живить dropdown локацій у UI).
            
            Передумови (@BeforeClass):
            - owner2.storage.id (config) тимчасово переведено в accessMode=REGIONS;
            - owner1.storage.id залишається з поточним accessMode середовища (очікується FULL_ACCESS).
            
            Тестові дані: нові області/members для OWNER_2 НЕ створюються — порівняння «as-is».
            Ролі: OWNER_1 (alkatras, storage id=1) vs OWNER_2 (bar, storage id=13).
            
            Кроки перевірки:
            1) ADMIN GET підтверджує accessMode обох owner-storages;
            2) OWNER_1 і OWNER_2 окремо викликають /storages/names?isActive=true;
            3) OWNER_2 має рівно 1 локацію — власний підрозділ;
            4) OWNER_1 має суворо більше записів, ніж OWNER_2 (ширша видимість).
            
            Очікування: owner2Names.size()==1 і owner1Names.size() > owner2Names.size().
            Не перевіряємо: точний склад списку OWNER_1 (залежить від даних dev/staging).
            """)
    @Severity(SeverityLevel.NORMAL)
    public void testFullAccessOwnerSeesMoreThanRestrictedOwner() {
        Long owner1StorageId = ConfigProvider.getOwner1StorageId();

        Allure.step("STEP 1: ADMIN — accessMode owner-storages", () -> {
            StorageResponse owner1Storage = storageFixture.getById(UserRole.ADMIN, owner1StorageId);
            StorageResponse owner2Storage = storageFixture.getById(UserRole.ADMIN, owner2StorageId);

            assertThat(owner1Storage.getAccessMode())
                    .as("OWNER_1 storage (id=%d) має FULL_ACCESS на dev/staging", owner1StorageId)
                    .isEqualTo(StorageAccessMode.FULL_ACCESS.name());
            assertThat(owner2Storage.getAccessMode())
                    .as("OWNER_2 storage (id=%d) підготовлено як REGIONS у @BeforeClass", owner2StorageId)
                    .isEqualTo(StorageAccessMode.REGIONS.name());
        });

        List<StorageResponse> owner1Names = Allure.step(
                "STEP 2: OWNER_1 — GET /storages/names?isActive=true", () ->
                        storageFixture.getNames(UserRole.OWNER_1, true, null));

        List<StorageResponse> owner2Names = Allure.step(
                "STEP 3: OWNER_2 (REGIONS) — GET /storages/names?isActive=true", () ->
                        storageFixture.getNames(UserRole.OWNER_2, true, null));

        Allure.step("STEP 4: Порівняння розміру списків видимості", () -> {
            log.info("TC-STR-REG-021: OWNER_1 names count={}, OWNER_2 names count={}",
                    owner1Names.size(), owner2Names.size());

            assertThat(owner2Names)
                    .as("REGIONS owner без областей видимості бачить лише власний підрозділ (id=%d)",
                            owner2StorageId)
                    .hasSize(1);
            assertThat(owner2Names.getFirst().getId())
                    .as("Єдиний елемент OWNER_2 /names — його business unit")
                    .isEqualTo(owner2StorageId);

            assertThat(owner1Names.size())
                    .as("FULL_ACCESS owner бачить ширший перелік локацій у селекторі, ніж REGIONS owner")
                    .isGreaterThan(owner2Names.size());

            assertThat(owner1Names.stream().map(StorageResponse::getId))
                    .as("OWNER_1 бачить принаймні власний storage id=%d", owner1StorageId)
                    .contains(owner1StorageId);
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
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testRestrictedOwnerSeesOnlyOwnUnitWithoutRegions() {
        StorageResponse foreign = storageFixture.createUniqueStorage("vis-foreign-");

        List<StorageResponse> names = storageFixture.getNames(UserRole.OWNER_2, true, null);

        assertThat(names).hasSize(1);
        assertThat(names.getFirst().getId()).isEqualTo(owner2StorageId);
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
            """)
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
            """)
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
            """)
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
    }

    @Test(priority = 60)
    @TestCaseId("TC-STR-REG-033")
    @Description("""
            Що перевіряємо: селектор «мої підрозділи» для REGIONS owner не розширюється областями.
            Тестові дані: OWNER_2 у REGIONS (можуть існувати області з попередніх тестів у класі).
            Очікування: GET /storages/names/my-units — рівно 1 internal unit = owner2.storage.id.
            """)
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
            """)
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
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testExplicitGrantShowsRealName() {
        StorageResponse granted = storageFixture.createUniqueStorage("vis-exp-grant-");

        regionFixture.addExplicitLocations(granted.getId(), owner2StorageId);

        List<StorageResponse> names = storageFixture.getNames(UserRole.OWNER_2, true, null);
        StorageResponse visible = names.stream()
                .filter(s -> Objects.equals(s.getId(), granted.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Explicitly granted location not in /names"));

        assertThat(visible.getName()).isEqualTo(granted.getName());
    }
}
