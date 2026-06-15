package com.erp.data.factories.non_series_production;

import com.erp.data.FakerProvider;
import com.erp.enums.NonSeriesProductionStatus;
import com.erp.models.request.NonSeriesProductionRequest;
import com.erp.models.request.NonSeriesProductionResourceUsageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class NonSeriesProductionDataFactory {

  private NonSeriesProductionDataFactory() {
  }

  public static NonSeriesProductionRequest buildCreateRequest(Long storageId,
                                                              NonSeriesProductionStatus status,
                                                              String product,
                                                              double productAmount,
                                                              List<NonSeriesProductionResourceUsageRequest> resourceUsage) {
    LocalDate today = LocalDate.now();
    return NonSeriesProductionRequest.builder()
        .storageId(storageId)
        .start(today)
        .end(today.plusDays(1))
        .workerQty(2)
        .product(product)
        .amount(BigDecimal.valueOf(productAmount))
        .description("erp-auto-test non-series production")
        .status(status)
        .resourceUsageList(resourceUsage != null ? resourceUsage : List.of())
        .build();
  }

  public static NonSeriesProductionRequest buildInProgressRequest(Long storageId,
                                                                  Long resourceId,
                                                                  double resourceAmountPerUnit,
                                                                  double productAmount) {
    return buildCreateRequest(
        storageId,
        NonSeriesProductionStatus.IN_PROGRESS,
        uniqueProductName(),
        productAmount,
        List.of(usage(resourceId, resourceAmountPerUnit)));
  }

  public static NonSeriesProductionRequest buildDoneRequest(Long storageId,
                                                            Long resourceId,
                                                            double resourceAmountPerUnit,
                                                            double productAmount) {
    return buildCreateRequest(
        storageId,
        NonSeriesProductionStatus.DONE,
        uniqueProductName(),
        productAmount,
        List.of(usage(resourceId, resourceAmountPerUnit)));
  }

  public static NonSeriesProductionRequest withStatus(NonSeriesProductionRequest base,
                                                      NonSeriesProductionStatus status) {
    return base.toBuilder().status(status).build();
  }

  public static NonSeriesProductionResourceUsageRequest usage(Long resourceId, double amountPerUnit) {
    return NonSeriesProductionResourceUsageRequest.builder()
        .resourceId(resourceId)
        .amount(BigDecimal.valueOf(amountPerUnit))
        .build();
  }

  public static String uniqueProductName() {
    return "NSP-" + FakerProvider.english().commerce().productName()
        + "-" + UUID.randomUUID().toString().substring(0, 6);
  }

  /**
   * Integer per-unit usage so {@code productAmount × usage} is strictly greater than
   * {@code minUsageFraction × stock} and still fits within {@code availableStock}.
   */
  public static long usagePerUnitForStockUsageAbove(double availableStock,
                                                    int productAmount,
                                                    double minUsageFraction) {
    if (productAmount <= 0 || availableStock <= 0) {
      throw new IllegalArgumentException("availableStock and productAmount must be positive");
    }
    if (minUsageFraction <= 0 || minUsageFraction >= 1) {
      throw new IllegalArgumentException("minUsageFraction must be between 0 and 1 (exclusive)");
    }
    long stock = (long) Math.floor(availableStock);
    long minTotal = (long) Math.floor(stock * minUsageFraction) + 1;
    long total = minTotal;
    if (total % productAmount != 0) {
      total += productAmount - (total % productAmount);
    }
    if (total > stock) {
      total = (stock / productAmount) * productAmount;
    }
    if (total <= stock * minUsageFraction || total <= 0) {
      throw new IllegalStateException(String.format(
          "Cannot derive integer usage: stock=%d, units=%d, need total > %.2f",
          stock, productAmount, stock * minUsageFraction));
    }
    return total / productAmount;
  }
}
