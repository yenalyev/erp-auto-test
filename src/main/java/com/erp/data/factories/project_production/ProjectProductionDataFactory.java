package com.erp.data.factories.project_production;

import com.erp.data.FakerProvider;
import com.erp.enums.ProjectProductionState;
import com.erp.enums.ProjectProductionType;
import com.erp.models.request.ProjectProductionParameterRequest;
import com.erp.models.request.ProjectProductionRequest;
import com.erp.models.request.ProjectProductionStageRequest;
import com.erp.models.request.ProjectProductionStageResourceUsageRequest;
import com.erp.models.request.ProjectProductionTemplateRequest;
import com.erp.models.request.ResourceToRollbackRequest;
import com.erp.models.response.ProjectProductionResponse;
import com.erp.models.response.ProjectProductionStageResourceUsageResponse;
import com.erp.models.response.ProjectProductionStageResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Test data builders for the Project Production domain
 * ({@code /api/v1/project-production}, {@code /api/v1/project-production-template}).
 * <p>
 * Mirrors {@code NonSeriesProductionDataFactory} conventions: unique names/serial numbers
 * via {@link UUID}, builder-based request assembly, small composable helpers for stages
 * and resource usages.
 */
public class ProjectProductionDataFactory {

    private ProjectProductionDataFactory() {
    }

    // ═══════════════════════════════════════════════════════════════
    // UNIQUE IDENTIFIERS
    // ═══════════════════════════════════════════════════════════════

    public static String uniqueName() {
        return "PP-" + FakerProvider.english().commerce().productName()
                + "-" + UUID.randomUUID().toString().substring(0, 6);
    }

    public static String uniqueSerialNumber() {
        return "SN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public static String uniqueModelName() {
        return "PP-MODEL-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static String uniqueTemplateName() {
        return "PP-TPL-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ═══════════════════════════════════════════════════════════════
    // STAGE / RESOURCE USAGE / ROLLBACK BUILDERS
    // ═══════════════════════════════════════════════════════════════

    public static ProjectProductionStageResourceUsageRequest usage(Long resourceId,
                                                                    double amountNeeded,
                                                                    double amountUsed) {
        return ProjectProductionStageResourceUsageRequest.builder()
                .resourceId(resourceId)
                .amountNeeded(BigDecimal.valueOf(amountNeeded))
                .amountUsed(BigDecimal.valueOf(amountUsed))
                .build();
    }

    public static ProjectProductionStageResourceUsageRequest usageNeeded(Long resourceId, double amountNeeded) {
        return usage(resourceId, amountNeeded, 0.0);
    }

    public static ProjectProductionStageRequest stage(String name,
                                                       int order,
                                                       ProjectProductionState state,
                                                       List<ProjectProductionStageResourceUsageRequest> usages) {
        return ProjectProductionStageRequest.builder()
                .name(name)
                .description("erp-auto-test project production stage")
                .state(state != null ? state : ProjectProductionState.CREATED)
                .stageOrder(order)
                .executionPercentage(100)
                .projectProductionStageResourceUsages(usages != null ? usages : List.of())
                .build();
    }

    /** Single stage with a single resource usage — the common case for functional/RBAC tests. */
    public static ProjectProductionStageRequest singleResourceStage(Long resourceId,
                                                                    double amountNeeded,
                                                                    double amountUsed) {
        return stage("Stage-1", 1, ProjectProductionState.CREATED,
                List.of(usage(resourceId, amountNeeded, amountUsed)));
    }

    public static ResourceToRollbackRequest rollback(Long stageId, Long resourceId, double amount) {
        return ResourceToRollbackRequest.builder()
                .stageId(stageId)
                .resourceId(resourceId)
                .amount(BigDecimal.valueOf(amount))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    // PROJECT PRODUCTION REQUEST BUILDERS
    // ═══════════════════════════════════════════════════════════════

    public static ProjectProductionRequest buildCreateRequest(Long storageId,
                                                               Long equipmentCategoryId,
                                                               Long equipmentModelId,
                                                               ProjectProductionState state,
                                                               ProjectProductionType type,
                                                               List<ProjectProductionStageRequest> stages) {
        LocalDate today = LocalDate.now();
        return ProjectProductionRequest.builder()
                .name(uniqueName())
                .start(today)
                .deadlineTo(today.plusDays(14))
                .state(state != null ? state : ProjectProductionState.CREATED)
                .type(type != null ? type : ProjectProductionType.CREATION)
                .serialNumber(uniqueSerialNumber())
                .description("erp-auto-test project production")
                .storageId(storageId)
                .equipmentCategoryId(equipmentCategoryId)
                .equipmentModelId(equipmentModelId)
                .projectProductionStages(stages != null ? stages : List.of())
                .build();
    }

    /** Convenience: single stage with a single resource usage (needed == used, i.e. fully consumed). */
    public static ProjectProductionRequest buildCreateRequestWithStage(Long storageId,
                                                                       Long equipmentCategoryId,
                                                                       Long equipmentModelId,
                                                                       Long resourceId,
                                                                       double resourceAmountNeeded,
                                                                       double resourceAmountUsed) {
        return buildCreateRequest(
                storageId,
                equipmentCategoryId,
                equipmentModelId,
                ProjectProductionState.IN_PROGRESS,
                ProjectProductionType.CREATION,
                List.of(singleResourceStage(resourceId, resourceAmountNeeded, resourceAmountUsed)));
    }

    public static ProjectProductionRequest withState(ProjectProductionRequest base, ProjectProductionState state) {
        return base.toBuilder().state(state).build();
    }

    /** Builds a PUT body from a GET/create response (keeps stages + usages). */
    public static ProjectProductionRequest toUpdateRequest(ProjectProductionResponse source) {
        List<ProjectProductionStageRequest> stages = source.getProjectProductionStages() == null
                ? List.of()
                : source.getProjectProductionStages().stream()
                .map(ProjectProductionDataFactory::toStageRequest)
                .toList();

        return ProjectProductionRequest.builder()
                .start(source.getStart())
                .deadlineTo(source.getDeadlineTo())
                .state(source.getState())
                .type(source.getType())
                .serialNumber(source.getSerialNumber())
                .description(source.getDescription())
                .storageId(source.getStorage() != null ? source.getStorage().getId() : null)
                .equipmentCategoryId(source.getEquipmentCategory() != null ? source.getEquipmentCategory().getId() : null)
                .equipmentModelId(source.getEquipmentModel() != null ? source.getEquipmentModel().getId() : null)
                .equipmentId(source.getEquipment() != null ? source.getEquipment().getId() : null)
                .parameters(source.getParameters() == null ? null : source.getParameters().stream()
                        .map(parameter -> ProjectProductionParameterRequest.builder()
                                .name(parameter.getName())
                                .value(parameter.getValue())
                                .build())
                        .toList())
                .projectProductionStages(stages)
                .build();
    }

    private static ProjectProductionStageRequest toStageRequest(ProjectProductionStageResponse stage) {
        List<ProjectProductionStageResourceUsageRequest> usages = stage.getProjectProductionStageResourceUsages() == null
                ? List.of()
                : stage.getProjectProductionStageResourceUsages().stream()
                .map(ProjectProductionDataFactory::toUsageRequest)
                .toList();

        return ProjectProductionStageRequest.builder()
                .name(stage.getName())
                .description(stage.getDescription())
                .comment(stage.getComment())
                .state(stage.getState())
                .stageOrder(stage.getStageOrder())
                .executionPercentage(stage.getExecutionPercentage())
                .projectProductionStageResourceUsages(usages)
                .build();
    }

    private static ProjectProductionStageResourceUsageRequest toUsageRequest(
            ProjectProductionStageResourceUsageResponse usage) {
        return ProjectProductionStageResourceUsageRequest.builder()
                .id(usage.getId())
                .resourceId(usage.getResource() != null ? usage.getResource().getId() : null)
                .amountNeeded(usage.getAmountNeeded())
                .amountUsed(usage.getAmountUsed())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    // TEMPLATE REQUEST BUILDERS
    // ═══════════════════════════════════════════════════════════════

    public static ProjectProductionTemplateRequest buildTemplateCreateRequest(Long storageId,
                                                                               Long equipmentCategoryId,
                                                                               Long equipmentModelId,
                                                                               List<ProjectProductionStageRequest> stages) {
        return buildTemplateCreateRequest(storageId, equipmentCategoryId, equipmentModelId,
                ProjectProductionType.CREATION, stages);
    }

    public static ProjectProductionTemplateRequest buildTemplateCreateRequest(Long storageId,
                                                                               Long equipmentCategoryId,
                                                                               Long equipmentModelId,
                                                                               ProjectProductionType type,
                                                                               List<ProjectProductionStageRequest> stages) {
        return ProjectProductionTemplateRequest.builder()
                .name(uniqueTemplateName())
                .state(ProjectProductionState.CREATED)
                .type(type != null ? type : ProjectProductionType.CREATION)
                .description("erp-auto-test project production template")
                .storageId(storageId)
                .equipmentCategoryId(equipmentCategoryId)
                .equipmentModelId(equipmentModelId)
                .projectProductionStages(stages != null ? stages : List.of())
                .build();
    }
}
