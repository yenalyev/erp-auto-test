package com.erp.tests.framework;

import com.erp.data.factories.defect.DefectDataFactory;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.models.response.DefectResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

public class BatchIdentityContractTest {
    @Test
    public void sendPreservesExistingBatchUuidAndRejectsMissingIdentity() throws Exception {
        UUID uuid = UUID.randomUUID();
        var request = RelocationDataFactory.buildSendWithBatch(1L, 2L, 3L, 4, uuid, "same-name", false);
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(request);
        assertThat(json.at("/items/0/batches/0/batchUuid").asText()).isEqualTo(uuid.toString());
        assertThatThrownBy(() -> RelocationDataFactory.buildSendWithBatch(
                1L, 2L, 3L, 4, null, "same-name", false)).isInstanceOf(NullPointerException.class);
        assertThat(RelocationDataFactory.usageForExternalBatch(3L, 4, "new-batch", false)
                .getBatches().getFirst().getBatchUuid()).isNull();
    }

    @Test
    public void writeOffPreservesDistinctUuidsForBatchesWithTheSameName() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ObjectMapper mapper = new ObjectMapper();
        DefectResponse defect = mapper.readValue("""
                {"id": 7, "defectBatches": [
                  {"batchUuid": "%s", "batchNumber": "same-name", "amount": 2, "isProduced": false},
                  {"batchUuid": "%s", "batchNumber": "same-name", "amount": 3, "isProduced": false}
                ]}
                """.formatted(first, second), DefectResponse.class);
        var request = DefectDataFactory.buildWriteOffForDefect(defect, 1L, 5, "audit");
        var json = mapper.valueToTree(request);
        assertThat(json.at("/batches/0/batchUuid").asText()).isEqualTo(first.toString());
        assertThat(json.at("/batches/1/batchUuid").asText()).isEqualTo(second.toString());
        assertThat(json.at("/batches/0/amount").decimalValue()).isEqualByComparingTo("2");
        assertThat(json.at("/batches/1/amount").decimalValue()).isEqualByComparingTo("3");
    }

    @Test
    public void writeOffRejectsResponseThatLostBatchIdentity() throws Exception {
        DefectResponse defect = new ObjectMapper().readValue(
                "{\"id\":7,\"defectBatches\":[{\"batchNumber\":\"batch\",\"amount\":2}]}",
                DefectResponse.class);
        assertThatThrownBy(() -> DefectDataFactory.buildWriteOffForDefect(defect, 1L, 1, "audit"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("UUID");
    }
}
