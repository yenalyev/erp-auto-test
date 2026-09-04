package com.erp.utils.helpers;

import com.erp.fixtures.ProductionFixture;
import com.erp.models.common.ProductionJournalFilterScenario;
import com.erp.models.query.ProductionJournalQuery;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.ResourceCategoryResponse;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Builds production journal filter scenarios from live API data (no hardcoded filter values).
 */
@Slf4j
public class ProductionJournalFilterCatalog {

    private static final int BASELINE_PAGE_SIZE = 500;

    private final long storageId;
    private final List<ManufacturingItemResponse> baseline;
    private final long unfilteredTotal;
    private final Map<Long, Long> productCategoryMap;
    private final Map<Long, ResourceCategoryResponse> categoriesById;

    private ProductionJournalFilterCatalog(long storageId,
                                           List<ManufacturingItemResponse> baseline,
                                           long unfilteredTotal,
                                           Map<Long, Long> productCategoryMap,
                                           Map<Long, ResourceCategoryResponse> categoriesById) {
        this.storageId = storageId;
        this.baseline = baseline;
        this.unfilteredTotal = unfilteredTotal;
        this.productCategoryMap = productCategoryMap;
        this.categoriesById = categoriesById;
    }

    public static ProductionJournalFilterCatalog load(ProductionFixture fixture, long storageId) {
        ProductionJournalQuery baselineQuery = ProductionJournalQuery.uiDefaults(storageId)
                .toBuilder()
                .pageSize(BASELINE_PAGE_SIZE)
                .build();
        List<ManufacturingItemResponse> baseline = fixture.getJournalPage(baselineQuery);
        if (baseline.isEmpty()) {
            throw new SkipException("Журнал виробництва порожній — неможливо побудувати сценарії фільтрів");
        }
        long unfilteredTotal = fixture.getJournalTotalElements(ProductionJournalQuery.uiDefaults(storageId));
        Set<Long> productIds = new LinkedHashSet<>();
        for (ManufacturingItemResponse item : baseline) {
            if (item.getProduct() != null && item.getProduct().getId() != null) {
                productIds.add(item.getProduct().getId());
            }
        }
        Map<Long, Long> productCategoryMap = fixture.getProductCategoryMapForIds(productIds);
        Map<Long, ResourceCategoryResponse> categoriesById = new HashMap<>();
        for (ResourceCategoryResponse category : fixture.getResourceCategories()) {
            categoriesById.put(category.getId(), category);
        }
        log.info("Production journal filter catalog: {} baseline records, unfilteredTotal={}, {} product categories mapped",
                baseline.size(), unfilteredTotal, productCategoryMap.size());
        return new ProductionJournalFilterCatalog(
                storageId, baseline, unfilteredTotal, productCategoryMap, categoriesById);
    }

    public long storageId() {
        return storageId;
    }

    public List<ManufacturingItemResponse> baseline() {
        return baseline;
    }

    public Map<Long, Long> productCategoryMap() {
        return productCategoryMap;
    }

    /** True when the first unfiltered page contains the whole journal (subset checks are valid). */
    public boolean baselineCoversJournal() {
        return baseline.size() >= unfilteredTotal;
    }

    public long unfilteredTotal() {
        return unfilteredTotal;
    }

    public void ensureProductCategories(ProductionFixture fixture, List<ManufacturingItemResponse> items) {
        Set<Long> missing = new LinkedHashSet<>();
        for (ManufacturingItemResponse item : items) {
            Long productId = item.getProduct() != null ? item.getProduct().getId() : null;
            if (productId != null && !productCategoryMap.containsKey(productId)) {
                missing.add(productId);
            }
        }
        if (!missing.isEmpty()) {
            productCategoryMap.putAll(fixture.getProductCategoryMapForIds(missing));
        }
    }

    public ProductionJournalFilterScenario productFilter(ProductionFixture fixture) {
        ManufacturingItemResponse anchor = baseline.getFirst();
        String productTerm = deriveProductSearchTerm(fixture, anchor);
        ProductionJournalQuery query = ProductionJournalQuery.uiDefaults(storageId)
                .toBuilder()
                .product(productTerm)
                .build();
        return ProductionJournalFilterScenario.builder()
                .name("product")
                .anchor(anchor)
                .query(query)
                .productTerm(productTerm)
                .build();
    }

    public ProductionJournalFilterScenario startDateFilter(ProductionFixture fixture) {
        ManufacturingItemResponse anchor = baseline.getFirst();
        LocalDate startDate = anchor.getDate();
        ProductionJournalQuery query = ProductionJournalQuery.uiDefaults(storageId)
                .toBuilder()
                .startDate(startDate)
                .build();
        return ProductionJournalFilterScenario.builder()
                .name("startDate")
                .anchor(anchor)
                .query(query)
                .startDate(startDate)
                .build();
    }

    public ProductionJournalFilterScenario endDateFilter(ProductionFixture fixture) {
        ManufacturingItemResponse anchor = baseline.getFirst();
        LocalDate endDate = anchor.getDate();
        // DateRangePicker cannot set end without start — UI applies same-day range.
        ProductionJournalQuery query = ProductionJournalQuery.uiDefaults(storageId)
                .toBuilder()
                .startDate(endDate)
                .endDate(endDate)
                .build();
        return ProductionJournalFilterScenario.builder()
                .name("endDate")
                .anchor(anchor)
                .query(query)
                .startDate(endDate)
                .endDate(endDate)
                .build();
    }

    public ProductionJournalFilterScenario dateRangeFilter(ProductionFixture fixture) {
        ManufacturingItemResponse anchor = baseline.getFirst();
        LocalDate date = anchor.getDate();
        ProductionJournalQuery query = ProductionJournalQuery.uiDefaults(storageId)
                .toBuilder()
                .startDate(date)
                .endDate(date)
                .build();
        return ProductionJournalFilterScenario.builder()
                .name("dateRange")
                .anchor(anchor)
                .query(query)
                .startDate(date)
                .endDate(date)
                .build();
    }

    public Optional<ProductionJournalFilterScenario> categoryFilter(ProductionFixture fixture) {
        Map<Long, Long> hitsByCategory = new HashMap<>();
        ProductionJournalFilterScenario rarest = null;
        long rarestHits = Long.MAX_VALUE;
        for (ManufacturingItemResponse anchor : baseline) {
            Long productId = anchor.getProduct() != null ? anchor.getProduct().getId() : null;
            if (productId == null) {
                continue;
            }
            Long categoryId = productCategoryMap.get(productId);
            if (categoryId == null) {
                continue;
            }
            ResourceCategoryResponse category = categoriesById.get(categoryId);
            if (category == null) {
                continue;
            }
            long hits = hitsByCategory.computeIfAbsent(categoryId, id -> {
                ProductionJournalQuery probe = ProductionJournalQuery.uiDefaults(storageId)
                        .toBuilder()
                        .categoryId(id)
                        .build();
                return fixture.getJournalTotalElements(probe);
            });
            if (hits == 0 || hits >= rarestHits) {
                continue;
            }
            rarestHits = hits;
            ProductionJournalQuery query = ProductionJournalQuery.uiDefaults(storageId)
                    .toBuilder()
                    .categoryId(categoryId)
                    .build();
            rarest = ProductionJournalFilterScenario.builder()
                    .name("category")
                    .anchor(anchor)
                    .query(query)
                    .categoryId(categoryId)
                    .categoryName(category.getName())
                    .build();
            if (hits == 1) {
                break;
            }
        }
        return Optional.ofNullable(rarest);
    }

    public ProductionJournalFilterScenario productAndDateRangeFilter(ProductionFixture fixture) {
        ManufacturingItemResponse anchor = baseline.getFirst();
        String productTerm = deriveProductSearchTerm(fixture, anchor);
        LocalDate date = anchor.getDate();
        ProductionJournalQuery query = ProductionJournalQuery.uiDefaults(storageId)
                .toBuilder()
                .product(productTerm)
                .startDate(date)
                .endDate(date)
                .build();
        return ProductionJournalFilterScenario.builder()
                .name("product+dateRange")
                .anchor(anchor)
                .query(query)
                .productTerm(productTerm)
                .startDate(date)
                .endDate(date)
                .build();
    }

    public Optional<ProductionJournalFilterScenario> productAndCategoryFilter(ProductionFixture fixture) {
        return categoryFilter(fixture).map(categoryScenario -> {
            ManufacturingItemResponse anchor = categoryScenario.anchor();
            String productTerm = deriveProductSearchTerm(fixture, anchor);
            ProductionJournalQuery query = ProductionJournalQuery.uiDefaults(storageId)
                    .toBuilder()
                    .product(productTerm)
                    .categoryId(categoryScenario.categoryId())
                    .build();
            return ProductionJournalFilterScenario.builder()
                    .name("product+category")
                    .anchor(anchor)
                    .query(query)
                    .productTerm(productTerm)
                    .categoryId(categoryScenario.categoryId())
                    .categoryName(categoryScenario.categoryName())
                    .build();
        });
    }

    public Optional<ProductionJournalFilterScenario> productDateAndCategoryFilter(ProductionFixture fixture) {
        return categoryFilter(fixture).map(categoryScenario -> {
            ManufacturingItemResponse anchor = categoryScenario.anchor();
            String productTerm = deriveProductSearchTerm(fixture, anchor);
            LocalDate date = anchor.getDate();
            ProductionJournalQuery query = ProductionJournalQuery.uiDefaults(storageId)
                    .toBuilder()
                    .product(productTerm)
                    .startDate(date)
                    .endDate(date)
                    .categoryId(categoryScenario.categoryId())
                    .build();
            return ProductionJournalFilterScenario.builder()
                    .name("product+dateRange+category")
                    .anchor(anchor)
                    .query(query)
                    .productTerm(productTerm)
                    .startDate(date)
                    .endDate(date)
                    .categoryId(categoryScenario.categoryId())
                    .categoryName(categoryScenario.categoryName())
                    .build();
        });
    }

    private String deriveProductSearchTerm(ProductionFixture fixture, ManufacturingItemResponse anchor) {
        String name = anchor.getProduct().getName();
        Objects.requireNonNull(name, "anchor product name");
        int minLength = Math.min(3, name.length());
        for (int len = name.length(); len >= minLength; len--) {
            String term = name.substring(0, len).trim();
            ProductionJournalQuery probe = ProductionJournalQuery.uiDefaults(storageId)
                    .toBuilder()
                    .product(term)
                    .pageSize(BASELINE_PAGE_SIZE)
                    .build();
            boolean matchesAnchor = fixture.getJournalPage(probe).stream()
                    .anyMatch(item -> Objects.equals(item.getId(), anchor.getId()));
            if (matchesAnchor) {
                return term;
            }
        }
        throw new IllegalStateException("Cannot derive product search term for anchor id=" + anchor.getId());
    }
}
