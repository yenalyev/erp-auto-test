package tcm.util;

import org.junit.jupiter.api.Test;
import tcm.dto.AutotestResultDto;
import tcm.enums.ExecutionStatus;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InfrastructureAuditProbeTest {
    @Test void skippedReasonMustSurviveAggregation() {
        var result = AutotestStatusAggregator.aggregateByTestCaseId(List.of(
                AutotestResultDto.builder().testCaseId("TC-AUDIT-001")
                        .status(ExecutionStatus.SKIPPED).errorMessage("JDBC unavailable").build()
        )).getFirst();
        assertEquals("JDBC unavailable", result.errorMessage());
    }
}
