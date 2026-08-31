package com.erp.tests.functional.relocation;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.RelocationState;
import com.erp.enums.StorageRelation;
import com.erp.enums.UnitType;
import com.erp.enums.UserRole;
import com.erp.fixtures.OrderFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.fixtures.UserFixture;
import com.erp.models.request.RelocationOutputEditRequest;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.utils.helpers.RelocationStockAssertions;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Relocation")
@Feature("Edit in-transit send")
public class RelocationInTransitEditTest extends BaseFunctionalTest {

    private RelocationFixture fixture;
    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;
    private Long owner1Storage;
    private Long owner2Storage;
    private Long resourceId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupInTransitEditTests() {
        fixture = new RelocationFixture(testContext, apiExecutor);
        storageFixture = new StorageFixture(testContext, apiExecutor);
        regionFixture = new StorageRegionFixture(testContext, apiExecutor);
        fixture.prepareContext();
        owner1Storage = ConfigProvider.getOwner1StorageId();
        owner2Storage = ConfigProvider.getOwner2StorageId();
        resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);
    }

    @BeforeMethod(alwaysRun = true)
    public void ensureStock() {
        fixture.ensureStock(owner1Storage, resourceId, 200.0);
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupRelationTestStorages() {
        archiveRelationTestStorages();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupRelationTestStoragesAfterClass() {
        archiveRelationTestStorages();
    }

    private void archiveRelationTestStorages() {
        if (TestArtifactCleanup.shouldSkipApiCleanup()) {
            log.warn("Staging mode — skipping storage cleanup (-Dstaging.cleanup=false)");
            storageFixture.clearTrackedStorages();
            if (regionFixture != null) {
                regionFixture.clearTrackedRegions();
            }
            return;
        }
        if (regionFixture != null) {
            regionFixture.deleteTrackedRegions(UserRole.ADMIN);
        }
        storageFixture.deactivateTrackedStorages(UserRole.ADMIN);
    }

    @Test(priority = 75)
    @TestCaseId("TC-REL-075")
    @Story("Overstock in-transit edit rejected")
    @Description("""
            REQ-EDIT_REL-007 AC-07: збільшення CREATED понад залишок відправника дає 400.
            Кількість і залишок не змінюються.
            """)
    public void testEditInTransitOverstockReturns400() {
        String marker = "overstock-" + System.currentTimeMillis();
        Set<Long> tracked = fixture.trackedResource(resourceId);

        RelocationResponse sent = fixture.createSendWithDescription(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, 8.0, marker);
        ProductionStockAssertions.StockSnapshot senderBefore = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ДО overstock edit");

        Response response = fixture.editSendRaw(
                UserRole.ADMIN, sent.getId(), owner1Storage,
                RelocationDataFactory.buildSendEditRequest(resourceId, 5000.0, marker));
        assertThat(response.statusCode()).isEqualTo(400);

        RelocationResponse still = fixture.findInTransitByDescription(
                UserRole.ADMIN, owner1Storage, marker);
        assertThat(still).isNotNull();
        assertThat(still.getItems().getFirst().getAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(8.0));
        RelocationStockAssertions.assertUnchanged(
                senderBefore,
                RelocationStockAssertions.capture(
                        apiExecutor, owner1Storage, UserRole.OWNER_1, tracked, "ПІСЛЯ overstock edit"),
                owner1Storage, resourceId, "sender stock after rejected overstock");
    }

    @Test(priority = 76)
    @TestCaseId("TC-REL-076")
    @Story("Empty items in-transit edit rejected")
    @Description("""
            REQ-EDIT_REL-007 AC-07: порожній items на PUT send CREATED дає 400.
            """)
    public void testEditInTransitEmptyItemsReturns400() {
        String marker = "empty-items-" + System.currentTimeMillis();
        RelocationResponse sent = fixture.createSendWithDescription(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, 8.0, marker);

        RelocationOutputEditRequest empty = RelocationOutputEditRequest.builder()
                .description(marker)
                .date(LocalDate.now())
                .items(List.of())
                .build();
        Response response = fixture.editSendRaw(UserRole.ADMIN, sent.getId(), owner1Storage, empty);
        assertThat(response.statusCode()).isEqualTo(400);

        RelocationResponse still = fixture.findInTransitByDescription(
                UserRole.ADMIN, owner1Storage, marker);
        assertThat(still).isNotNull();
        assertThat(still.getItems()).isNotEmpty();
        assertThat(still.getItems().getFirst().getAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(8.0));
    }

    @Test(priority = 77)
    @TestCaseId("TC-REL-077")
    @Story("Sender is immutable on in-transit edit")
    @Description("""
            REQ-EDIT_REL-007 AC-03: senderId у PUT send для CREATED змінити не можна.
            """)
    public void testEditInTransitSenderImmutableReturns400() {
        String marker = "sender-immutable-" + System.currentTimeMillis();
        RelocationResponse sent = fixture.createSendWithDescription(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, 8.0, marker);

        RelocationOutputEditRequest edit = RelocationDataFactory.buildSendEditRequest(
                resourceId, 8.0, marker).toBuilder()
                .senderId(owner2Storage)
                .build();
        Response response = fixture.editSendRaw(UserRole.ADMIN, sent.getId(), owner1Storage, edit);
        assertThat(response.statusCode()).isEqualTo(400);

        RelocationResponse still = fixture.findInTransitByDescription(
                UserRole.ADMIN, owner1Storage, marker);
        assertThat(still).isNotNull();
        assertThat(still.getSender().getId()).isEqualTo(owner1Storage);
    }

    @Test(priority = 78)
    @TestCaseId("TC-REL-078")
    @Story("Redirect in-transit to another internal storage")
    @Description("""
            REQ-EDIT_REL-007 AC-03: отримувача CREATED можна змінити на інший internal склад
            з підтвердженням доставки.
            """)
    public void testEditInTransitRedirectToInternalStorage() {
        String marker = "redirect-int-" + System.currentTimeMillis();
        StorageResponse parent = storageFixture.resolveParentUnit();
        StorageResponse newRecipient = storageFixture.createChildStorage(parent.getId(), "rel-it-rcv-");

        RelocationResponse sent = fixture.createSendWithDescription(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, 8.0, marker);

        RelocationResponse updated = fixture.editSend(
                UserRole.ADMIN, sent.getId(), owner1Storage,
                RelocationDataFactory.buildSendEditRequest(
                        resourceId, 8.0, marker, newRecipient.getId()));
        assertThat(updated.getState()).isEqualTo(RelocationState.CREATED);
        assertThat(updated.getRecipient().getId()).isEqualTo(newRecipient.getId());

        assertThat(fixture.findInTransitByDescription(UserRole.ADMIN, owner2Storage, marker))
                .as("старий отримувач більше не бачить видачу «В дорозі»")
                .isNull();
        RelocationResponse onNewRecipient = fixture.findInTransitByDescription(
                UserRole.ADMIN, newRecipient.getId(), marker);
        assertThat(onNewRecipient).isNotNull();
        assertThat(onNewRecipient.getId()).isEqualTo(sent.getId());
    }

    @Test(priority = 79)
    @TestCaseId("TC-REL-079")
    @Story("Cannot redirect in-transit to EXTERNAL")
    @Description("""
            REQ-EDIT_REL-007 AC-03: перенаправлення CREATED на EXTERNAL дає 400.
            Отримувач не змінюється.
            """)
    public void testEditInTransitRedirectToExternalReturns400() {
        String marker = "redirect-ext-" + System.currentTimeMillis();
        StorageResponse parent = storageFixture.resolveParentUnit();
        StorageResponse external = storageFixture.createExternalChildStorage(parent.getId(), "rel-it-ext-");
        assertThat(external.getRelation()).isEqualTo(StorageRelation.EXTERNAL.name());

        RelocationResponse sent = fixture.createSendWithDescription(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, 8.0, marker);

        Response response = fixture.editSendRaw(
                UserRole.ADMIN, sent.getId(), owner1Storage,
                RelocationDataFactory.buildSendEditRequest(
                        resourceId, 8.0, marker, external.getId()));
        assertThat(response.statusCode()).isEqualTo(400);

        RelocationResponse still = fixture.findInTransitByDescription(
                UserRole.ADMIN, owner1Storage, marker);
        assertThat(still).isNotNull();
        assertThat(still.getRecipient().getId()).isEqualTo(owner2Storage);
    }

    @Test(priority = 80)
    @TestCaseId("TC-REL-080")
    @Story("Replace nomenclature on in-transit send")
    @Description("""
            REQ-EDIT_REL-007 AC-02: заміна номенклатури CREATED повертає старий ресурс
            і списує новий; отримувач до прийняття без залишку.
            """)
    public void testEditInTransitReplaceResource() {
        List<ResourceResponse> resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        assertThat(resources).as("потрібні два спільні ресурси").hasSizeGreaterThan(1);
        Long resource2 = resources.get(1).getId();
        fixture.ensureStock(owner1Storage, resource2, 50.0);

        Set<Long> tracked1 = fixture.trackedResource(resourceId);
        Set<Long> tracked2 = fixture.trackedResource(resource2);

        ProductionStockAssertions.StockSnapshot senderR1Before = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked1, "ДО send R1");
        ProductionStockAssertions.StockSnapshot senderR2Before = RelocationStockAssertions.capture(
                apiExecutor, owner1Storage, UserRole.OWNER_1, tracked2, "ДО send R2");
        ProductionStockAssertions.StockSnapshot recipientR1Before = RelocationStockAssertions.capture(
                apiExecutor, owner2Storage, UserRole.ADMIN, tracked1, "отримувач R1 ДО");
        ProductionStockAssertions.StockSnapshot recipientR2Before = RelocationStockAssertions.capture(
                apiExecutor, owner2Storage, UserRole.ADMIN, tracked2, "отримувач R2 ДО");

        RelocationResponse sent = fixture.createSend(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, 8.0);
        RelocationResponse updated = fixture.editSend(
                UserRole.ADMIN, sent.getId(), owner1Storage,
                RelocationDataFactory.buildSendEditRequest(resource2, 5.0, "replaced nomenclature"));
        assertThat(updated.getState()).isEqualTo(RelocationState.CREATED);
        assertThat(updated.getItems()).hasSize(1);
        assertThat(updated.getItems().getFirst().getResource().getId()).isEqualTo(resource2);
        assertThat(updated.getItems().getFirst().getAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(5.0));

        RelocationStockAssertions.assertUnchanged(
                senderR1Before,
                RelocationStockAssertions.capture(
                        apiExecutor, owner1Storage, UserRole.OWNER_1, tracked1, "ПІСЛЯ replace R1"),
                owner1Storage, resourceId, "resource 1 returned to sender");
        RelocationStockAssertions.assertDebitedFromSender(
                senderR2Before,
                RelocationStockAssertions.capture(
                        apiExecutor, owner1Storage, UserRole.OWNER_1, tracked2, "ПІСЛЯ replace R2"),
                owner1Storage, resource2, 5.0, "resource 2 taken from sender");
        RelocationStockAssertions.assertUnchanged(
                recipientR1Before,
                RelocationStockAssertions.capture(
                        apiExecutor, owner2Storage, UserRole.ADMIN, tracked1, "отримувач R1 ПІСЛЯ"),
                owner2Storage, resourceId, "recipient has no resource 1");
        RelocationStockAssertions.assertUnchanged(
                recipientR2Before,
                RelocationStockAssertions.capture(
                        apiExecutor, owner2Storage, UserRole.ADMIN, tracked2, "отримувач R2 ПІСЛЯ"),
                owner2Storage, resource2, "recipient has no resource 2");
    }

    @Test(priority = 84)
    @TestCaseId("TC-REL-084")
    @Story("Recipient 403 before stock check")
    @Description("""
            REQ-EDIT_REL-007 AC-07: отримувач з завищеною кількістю отримує 403,
            а не 400 з текстом про залишок / партії відправника.
            """)
    public void testRecipientOverstockEditReturns403Not400() {
        String batch = RelocationDataFactory.uniqueBatchNumber();
        fixture.seedBatchOnStorage(owner1Storage, resourceId, 20.0, batch);

        RelocationResponse sent = fixture.createSend(
                UserRole.OWNER_1, owner1Storage, owner2Storage, resourceId, 8.0);
        RelocationOutputEditRequest edit = RelocationDataFactory.buildSendEditRequest(
                resourceId, 999.0, "recipient leak check");

        Response response = fixture.editSendRaw(
                UserRole.OWNER_2, sent.getId(), owner2Storage, edit);
        assertThat(response.statusCode()).isEqualTo(403);
        String body = response.getBody().asString();
        assertThat(body).doesNotContain("Недостатньо");
        assertThat(body).doesNotContain(batch);
    }

    @Test(priority = 85)
    @TestCaseId("TC-REL-085")
    @Story("Owner STORAGE sender edits own in-transit")
    @Description("""
            REQ-EDIT_REL-007 AC-01: Owner зі складу (STORAGE) зберігає правку видачі,
            яку відправив зі свого складу.
            """)
    public void ownerStorageSenderEditsOwnInTransit() {
        assertOwnerEditsOwnInTransitFrom(UnitType.STORAGE);
    }

    @Test(priority = 86)
    @TestCaseId("TC-REL-086")
    @Story("Owner PRODUCTION sender edits own in-transit")
    @Description("""
            REQ-EDIT_REL-007 AC-01: Owner з виробництва (PRODUCTION) зберігає правку видачі,
            яку відправив зі свого складу.
            """)
    public void ownerProductionSenderEditsOwnInTransit() {
        assertOwnerEditsOwnInTransitFrom(UnitType.PRODUCTION);
    }

    @Test(priority = 87)
    @TestCaseId("TC-REL-087")
    @Story("Owner UNIT sender edits own in-transit")
    @Description("""
            REQ-EDIT_REL-007 AC-01: Owner підрозділу (3bat, order.requester.unit.name)
            зберігає правку видачі, яку відправив зі свого UNIT.
            """)
    public void ownerUnitSenderEditsOwnInTransit() {
        StorageResponse unit = resolveRequesterUnit();
        StorageResponse recipient = storageFixture.createChildStorage(unit.getId(), "rel-it-3bat-rcv-");
        assertOwnerEditsUnitSendTo(unit, recipient, UnitType.STORAGE);
    }

    private void assertOwnerEditsOwnInTransitFrom(UnitType senderType) {
        Allure.parameter("senderType", senderType.name());
        StorageResponse parent = storageFixture.resolveParentUnit();
        StorageResponse sender = createTypedSender(parent.getId(), senderType);
        assertThat(UnitType.valueOf(sender.getType()))
                .as("відправник має бути type=%s", senderType)
                .isEqualTo(senderType);

        StorageResponse recipient = storageFixture.createChildStorage(parent.getId(), "rel-it-type-rcv-");
        fixture.createExternalReceive(
                UserRole.ADMIN, sender.getId(), resourceId, 20.0,
                RelocationDataFactory.uniqueBatchNumber());

        RelocationResponse sent = fixture.createSend(
                UserRole.ADMIN, sender.getId(), recipient.getId(), resourceId, 8.0);
        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);
        assertThat(sent.getSender().getId()).isEqualTo(sender.getId());

        RelocationResponse updated = fixture.editSend(
                UserRole.OWNER_1, sent.getId(), sender.getId(),
                RelocationDataFactory.buildSendEditRequest(
                        resourceId, 3.0, "owner edit from " + senderType.name()));
        assertThat(updated.getState()).isEqualTo(RelocationState.CREATED);
        assertThat(updated.getSender().getId()).isEqualTo(sender.getId());
        assertThat(updated.getItems().getFirst().getAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(3.0));
    }

    private StorageResponse createTypedSender(Long parentId, UnitType senderType) {
        String prefix = "rel-it-" + senderType.name().toLowerCase() + "-snd-";
        return switch (senderType) {
            case STORAGE -> storageFixture.createChildStorage(parentId, prefix);
            case PRODUCTION -> storageFixture.createChildStorage(
                    parentId, prefix, UnitType.PRODUCTION, StorageRelation.INTERNAL);
            default -> throw new IllegalArgumentException("Unsupported sender type: " + senderType);
        };
    }

    @Test(priority = 88)
    @TestCaseId("TC-REL-088")
    @Story("Owner UNIT sender edits in-transit send to CREW")
    @Description("""
            REQ-EDIT_REL-007 AC-01: Owner підрозділу (3bat) править видачу «в дорозі» на екіпаж.
            Підтверджує відправник; до цього екіпаж без залишку.
            """)
    public void ownerUnitSenderEditsInTransitToCrew() {
        StorageResponse unit = resolveRequesterUnit();
        StorageResponse crew = storageFixture.createCrewStorage(unit.getId(), "rel-it-ucrew-");
        assertOwnerEditsUnitSendTo(unit, crew, UnitType.CREW);
    }

    @Test(priority = 89)
    @TestCaseId("TC-REL-089")
    @Story("Owner UNIT sender edits in-transit send to FLY_POINT")
    @Description("""
            REQ-EDIT_REL-007 AC-01: Owner підрозділу (3bat) править видачу «в дорозі» на точку вильоту.
            Підтверджує відправник; до цього точка без залишку.
            """)
    public void ownerUnitSenderEditsInTransitToFlyPoint() {
        StorageResponse unit = resolveRequesterUnit();
        StorageResponse flyPoint = storageFixture.createFlyPointStorage(unit.getId(), "rel-it-ufly-");
        assertOwnerEditsUnitSendTo(unit, flyPoint, UnitType.FLY_POINT);
    }

    private void assertOwnerEditsUnitSendTo(
            StorageResponse unit,
            StorageResponse recipient,
            UnitType recipientType) {
        Allure.parameter("senderType", UnitType.UNIT.name());
        Allure.parameter("recipientType", recipientType.name());
        assertThat(UnitType.valueOf(unit.getType())).isEqualTo(UnitType.UNIT);
        assertThat(UnitType.valueOf(recipient.getType())).isEqualTo(recipientType);

        fixture.createExternalReceive(
                UserRole.ADMIN, unit.getId(), resourceId, 20.0,
                RelocationDataFactory.uniqueBatchNumber());

        RelocationResponse sent = fixture.createSend(
                UserRole.UNIT_ANALYST, unit.getId(), recipient.getId(), resourceId, 8.0);
        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);
        assertThat(sent.getSender().getId()).isEqualTo(unit.getId());
        assertThat(sent.getRecipient().getId()).isEqualTo(recipient.getId());

        RelocationResponse updated = fixture.editSend(
                UserRole.UNIT_ANALYST, sent.getId(), unit.getId(),
                RelocationDataFactory.buildSendEditRequest(
                        resourceId, 3.0, "3bat unit edit to " + recipientType.name()));
        assertThat(updated.getState()).isEqualTo(RelocationState.CREATED);
        assertThat(updated.getSender().getId()).isEqualTo(unit.getId());
        assertThat(updated.getRecipient().getId()).isEqualTo(recipient.getId());
        assertThat(updated.getItems().getFirst().getAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(3.0));
    }

    private StorageResponse resolveRequesterUnit() {
        Long unitId = new OrderFixture(testContext, apiExecutor).resolveRequesterUnitStorageId();
        new UserFixture(testContext, apiExecutor).ensureExistingUserIsUnitOwner(
                UserRole.UNIT_ANALYST.getUsername(), unitId);
        StorageResponse unit = storageFixture.getById(UserRole.ADMIN, unitId);
        Allure.parameter("requesterUnitId", unit.getId());
        Allure.parameter("requesterUnitName", unit.getName());
        Allure.parameter("unitHint", ConfigProvider.getOrderRequesterUnitName());
        assertThat(UnitType.valueOf(unit.getType()))
                .as("order.requester.unit.name має резолвитись у UNIT")
                .isEqualTo(UnitType.UNIT);
        return unit;
    }
}
