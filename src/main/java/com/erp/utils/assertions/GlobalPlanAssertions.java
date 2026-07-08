package com.erp.utils.assertions;

import com.erp.models.response.DecompositionBlockItemResponse;
import com.erp.models.response.DecompositionBlockResponse;
import com.erp.models.response.DecompositionRequirementsResponse;
import com.erp.models.response.DecompositionResponse;
import com.erp.models.response.LocationPlanResponse;
import com.erp.models.response.PlanResponse;
import com.erp.models.response.RequirementItemResponse;
import com.erp.models.response.ResourceUsageResponse;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

public final class GlobalPlanAssertions {

  public enum RequirementSection {
    SEMI_FINISHED,
    RAW_MATERIALS
  }

  private static final double TOLERANCE = 0.001;

  private GlobalPlanAssertions() {
  }

  public static void assertRequirementAmount(
      DecompositionRequirementsResponse requirements,
      Long resourceId,
      double expectedRequired,
      RequirementSection section) {
    assertThat(requirements).isNotNull();
    List<RequirementItemResponse> items = section == RequirementSection.SEMI_FINISHED
        ? requirements.getSemiFinished()
        : requirements.getRawMaterials();
    Optional<RequirementItemResponse> item = items.stream()
        .filter(i -> resourceId.equals(i.getResource().getId()))
        .findFirst();
    assertThat(item)
        .as("Requirement for resource id=%s in %s", resourceId, section)
        .isPresent();
    assertThat(item.get().getRequiredAmount())
        .as("requiredAmount for resource id=%s", resourceId)
        .isCloseTo(expectedRequired, within(TOLERANCE));
  }

  public static void assertRequirementProducedEqualsRequired(
      DecompositionRequirementsResponse requirements,
      Long resourceId) {
    assertThat(requirements).isNotNull();
    RequirementItemResponse item = requirements.getSemiFinished().stream()
        .filter(i -> resourceId.equals(i.getResource().getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Semi-finished requirement not found: " + resourceId));
    assertThat(item.getProducedAmount())
        .as("producedAmount for resource id=%s", resourceId)
        .isCloseTo(item.getRequiredAmount(), within(TOLERANCE));
  }

    public static void assertLocationOutputCount(
      List<LocationPlanResponse> locationPlans,
      Long storageId,
      int expectedCount) {
    LocationPlanResponse locationPlan = locationPlans.stream()
        .filter(lp -> storageId.equals(lp.getStorage().getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Location plan not found for storage " + storageId));
    assertThat(locationPlan.getOutput())
        .as("location output count at storage %s", storageId)
        .hasSize(expectedCount);
  }

  public static void assertLocationOutput(
      List<LocationPlanResponse> locationPlans,
      Long storageId,
      Long resourceId,
      double expectedAmount) {
    LocationPlanResponse locationPlan = locationPlans.stream()
        .filter(lp -> storageId.equals(lp.getStorage().getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Location plan not found for storage " + storageId));
    ResourceUsageResponse usage = locationPlan.getOutput().stream()
        .filter(o -> resourceId.equals(o.getResource().getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError(
            "Output resource id=%s not found for storage %s".formatted(resourceId, storageId)));
    assertThat(usage.getAmount())
        .as("location output amount for resource id=%s at storage %s", resourceId, storageId)
        .isCloseTo(expectedAmount, within(TOLERANCE));
  }

  public static void assertPlanOutputAmount(
      PlanResponse plan,
      Long resourceId,
      double expectedAmount) {
    ResourceUsageResponse usage = plan.getOutput().stream()
        .filter(o -> resourceId.equals(o.getResource().getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError(
            "Output resource id=%s not found in plan %s".formatted(resourceId, plan.getId())));
    assertThat(usage.getAmount())
        .as("plan output amount for resource id=%s", resourceId)
        .isCloseTo(expectedAmount, within(TOLERANCE));
  }

  public static void assertNextBlockRequired(
      DecompositionResponse decomposition,
      Long resourceId,
      double expectedAmount) {
    assertThat(decomposition.getNextBlock()).isNotNull();
    DecompositionBlockResponse nextBlock = decomposition.getNextBlock();
    DecompositionBlockItemResponse item = nextBlock.getItems().stream()
        .filter(i -> resourceId.equals(i.getResource().getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Resource id=%s not in nextBlock".formatted(resourceId)));
    assertThat(item.getRequiredAmount())
        .as("nextBlock requiredAmount for resource id=%s", resourceId)
        .isCloseTo(expectedAmount, within(TOLERANCE));
  }
}
