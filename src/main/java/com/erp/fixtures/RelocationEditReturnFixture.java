package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.UserRole;
import com.erp.enums.EquipmentStatus;
import com.erp.models.request.*;
import com.erp.models.response.*;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Editable external records; the parent owns catalog data, stock and location cleanup. */
public class RelocationEditReturnFixture extends RelocationSupplierFiltersFixture {
    private final ApiExecutor executor;
    private final TestContext context;
    private final List<RelocationResponse> receipts = new ArrayList<>(), sends = new ArrayList<>();
    private final List<Long> equipmentReceipts = new ArrayList<>(), equipmentSends = new ArrayList<>();
    private final List<Long> equipmentOwned = new ArrayList<>();
    public StorageResponse external;
    public Long equipmentReceipt, equipmentSend;

    public RelocationEditReturnFixture(TestContext context, ApiExecutor executor) {
        super(context, executor); this.executor = executor; this.context = context;
    }

    @Override public void prepare() {
        super.prepare();
        external = storages.createStorage(StorageDataFactory.externalStorage(fromParent.getId(), marker + "-external-").build());
        for (LocalDate date : List.of(today, today.minusDays(2))) {
            var items = List.of(RelocationDataFactory.usageForExternalBatch(first.getId(), 20,
                            marker + "-edit-" + date + "-first", false));
            var response = executor.executeRelocationReceive(RelocationDataFactory.buildReceiveRequest(
                    external.getId(), source.getId(), items, marker + "-edit-received-" + date)
                    .toBuilder().date(date).build(), UserRole.ADMIN);
            validateSuccess(response, "Editable external receipt");
            receipts.add(response.as(RelocationResponse.class));
            response = relocations.sendRaw(UserRole.ADMIN, RelocationDataFactory.buildSendMultiItem(
                    source.getId(), external.getId(), List.of(RelocationDataFactory.usage(first.getId(), 2)), marker + "-edit-sent-" + date)
                    .toBuilder().date(date).sendingPersonName("Test issuer").build());
            validateSuccess(response, "Editable external send");
            sends.add(response.as(RelocationResponse.class));
        }
        var equipment = new EquipmentFixture(context, executor);
        equipment.prepareCategoryContext();
        modelNameA = marker + "-edit-incoming-model";
        modelNameB = marker + "-edit-outgoing-model";
        var incoming = equipment.createEquipmentGroup(UserRole.ADMIN, source.getId(),
                context.get(ContextKey.EQUIPMENT_CATEGORY_ID), modelNameA,
                List.of(marker + "-eq-in"));
        incoming.forEach(e -> equipmentOwned.add(e.getId()));
        equipmentReceipt = equipment.findEquipmentReceiveRelocationId(UserRole.ADMIN, source.getId(), incoming.getFirst().getId());
        equipmentReceipts.add(equipmentReceipt);
        var outgoing = equipment.createEquipmentGroup(UserRole.ADMIN, source.getId(),
                context.get(ContextKey.EQUIPMENT_CATEGORY_ID), modelNameB,
                List.of(marker + "-eq-out"));
        outgoing.forEach(e -> equipmentOwned.add(e.getId()));
        equipmentReceipts.add(equipment.findEquipmentReceiveRelocationId(UserRole.ADMIN, source.getId(), outgoing.getFirst().getId()));
        var response = executor.executeWithQueryParams(ApiEndpointDefinition.EQUIPMENT_MODELS_GET_PAGE,
                UserRole.ADMIN, Map.of("modelName", modelNameA, "size", 50));
        validateSuccess(response, "Owned model");
        modelA = response.jsonPath().getList("content", EquipmentModelResponse.class).stream()
                .filter(m -> modelNameA.equals(m.getName())).findFirst().orElseThrow().getId();
        response = executor.executeWithQueryParams(ApiEndpointDefinition.EQUIPMENT_MODELS_GET_PAGE,
                UserRole.ADMIN, Map.of("modelName", modelNameB, "size", 50));
        validateSuccess(response, "Owned outgoing model");
        modelB = response.jsonPath().getList("content", EquipmentModelResponse.class).stream()
                .filter(m -> modelNameB.equals(m.getName())).findFirst().orElseThrow().getId();
        response = equipment.sendEquipmentRaw(UserRole.ADMIN, EquipmentRelocationSendRequest.builder()
                .fromStorageId(source.getId()).toStorageId(external.getId()).date(today)
                .equipmentIds(List.of(outgoing.getFirst().getId())).description(marker + "-equipment-send")
                .sendingPersonName("Test issuer").build());
        validateSuccess(response, "External equipment send");
        equipmentSend = response.as(RelocationResponse.class).getId();
        equipmentSends.add(equipmentSend);
    }

    public RelocationResponse record(String tab, boolean old) {
        return (tab.equals("sent") ? sends : receipts).get(old ? 1 : 0);
    }

    public Map<String, Object> scope(String tab) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("states", List.of("FINISHED", "AUTO_FINISHED", "RETURNED"));
        query.put("fromIds", List.of(tab.equals("sent") ? source.getId() : external.getId()));
        query.put("toIds", List.of(tab.equals("sent") ? external.getId() : source.getId()));
        query.put("type", "RESOURCE");
        return query;
    }

    public RelocationResponse saved(String tab, Long id) {
        var query = new LinkedHashMap<String, Object>();
        query.put(tab.equals("sent") ? "senderIds" : "receiverIds", source.getId());
        query.put("size", 100);
        return rows(query).stream().filter(r -> id.equals(r.getId())).findFirst().orElseThrow();
    }

    @Override public void cleanup() {
        List<Throwable> errors = new ArrayList<>();
        // Edited equipment history may no longer be deletable. Retire owned items,
        // as the parent fixture does, and retain history on archived locations.
        for (Long id : equipmentOwned) attempt(errors, () -> {
            new EquipmentFixture(context, executor).changeEquipmentStatus(UserRole.ADMIN, id, EquipmentStatus.RETIRED);
            var response = executor.execute(ApiEndpointDefinition.EQUIPMENT_GET_BY_ID, UserRole.ADMIN, null, id);
            validateSuccess(response, "Verify owned equipment retirement");
            assertThat(response.jsonPath().getString("status")).isEqualTo("RETIRED");
        });
        io.qameta.allure.Allure.addAttachment("Retained owned equipment history", "Equipment=" + equipmentOwned
                + "; receipts=" + equipmentReceipts + "; sends=" + equipmentSends + "; owned equipment retired before locations are archived");
        for (RelocationResponse r : sends) attempt(errors, () -> relocations.deleteRelocation(UserRole.ADMIN, r.getId(), source.getId()));
        for (RelocationResponse r : receipts) attempt(errors, () -> relocations.deleteRelocation(UserRole.ADMIN, r.getId(), source.getId()));
        if (external != null) attempt(errors, () -> assertThat(storages.archiveStorage(UserRole.ADMIN, external.getId())).isTrue());
        attempt(errors, super::cleanup);
        if (!errors.isEmpty()) {
            AssertionError error = new AssertionError("Edit-return fixture cleanup failed");
            errors.forEach(error::addSuppressed); throw error;
        }
    }
    private void attempt(List<Throwable> errors, Runnable action) {
        try { action.run(); } catch (Exception | AssertionError e) { errors.add(e); }
    }
}
