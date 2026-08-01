package com.erp.data.factories.project_production;

import com.erp.data.FakerProvider;
import com.erp.enums.ProjectProductionState;
import com.erp.enums.ProjectProductionType;
import com.erp.models.request.ProjectCategoryRequest;
import com.erp.models.request.ProjectProductPropertyRequest;
import com.erp.models.request.ProjectProductRequest;
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
 * ({@code /api/v1/project-production}, {@code /api/v1/project-production-template},
 * {@code /api/v1/project-category}, {@code /api/v1/project-product}).
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

    public static String uniqueCategoryName() {
        return "PP-CAT-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static String uniqueProductName() {
        return "PP-PROD-" + UUID.randomUUID().toString().substring(0, 8);
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
                                                               Long categoryId,
                                                               Long productId,
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
                .projectCategoryId(categoryId)
                .projectProductId(productId)
                .projectProductionStages(stages != null ? stages : List.of())
                .build();
    }

    /** Convenience: single stage with a single resource usage (needed == used, i.e. fully consumed). */
    public static ProjectProductionRequest buildCreateRequestWithStage(Long storageId,
                                                                       Long categoryId,
                                                                       Long productId,
                                                                       Long resourceId,
                                                                       double resourceAmountNeeded,
                                                                       double resourceAmountUsed) {
        return buildCreateRequest(
                storageId,
                categoryId,
                productId,
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
                .projectCategoryId(source.getProjectCategory() != null ? source.getProjectCategory().getId() : null)
                .projectProductId(source.getProjectProduct() != null ? source.getProjectProduct().getId() : null)
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
    // TEMPLATE / CATEGORY / PRODUCT REQUEST BUILDERS
    // ═══════════════════════════════════════════════════════════════

    public static ProjectProductionTemplateRequest buildTemplateCreateRequest(Long storageId,
                                                                               Long categoryId,
                                                                               Long productId,
                                                                               List<ProjectProductionStageRequest> stages) {
        return ProjectProductionTemplateRequest.builder()
                .name(uniqueTemplateName())
                .state(ProjectProductionState.CREATED)
                .type(ProjectProductionType.CREATION)
                .description("erp-auto-test project production template")
                .storageId(storageId)
                .projectCategoryId(categoryId)
                .projectProductId(productId)
                .projectProductionStages(stages != null ? stages : List.of())
                .build();
    }

    public static ProjectCategoryRequest buildCategoryCreateRequest() {
        return buildCategoryCreateRequest(uniqueCategoryName());
    }

    public static ProjectCategoryRequest buildCategoryCreateRequest(String name) {
        return ProjectCategoryRequest.builder()
                .name(name)
                .description("erp-auto-test project category")
                .build();
    }

    public static ProjectProductRequest buildProductCreateRequest(Long categoryId) {
        return buildProductCreateRequest(categoryId, uniqueProductName());
    }

    public static ProjectProductRequest buildProductCreateRequest(Long categoryId, String name) {
        return ProjectProductRequest.builder()
                .projectCategoryId(categoryId)
                .name(name)
                .description("erp-auto-test project product")
                .properties(List.of(
                        ProjectProductPropertyRequest.builder()
                                .name("erp-auto-test-property")
                                .value("erp-auto-test-value")
                                .build()))
                .build();
    }
}
