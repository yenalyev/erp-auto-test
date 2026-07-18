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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds production journal filter scenarios from live API data (no hardcoded filter values).
 */
@Slf4j
public class ProductionJournalFilterCatalog {

    private static final int BASELINE_PAGE_SIZE = 500;

    private final long storageId;
    private final List<ManufacturingItemResponse> baseline;
    private final Map<Long, Long> productCategoryMap;
    private final Map<Long, ResourceCategoryResponse> categoriesById;

    private ProductionJournalFilterCatalog(long storageId,
                                           List<ManufacturingItemResponse> baseline,
                                           Map<Long, Long> productCategoryMap,
                                           Map<Long, ResourceCategoryResponse> categoriesById) {
        this.storageId = storageId;
        this.baseline = baseline;
        this.productCategoryMap = productCategoryMap;
        this.categoriesById = categoriesById;
    }

    public static ProductionJournalFilterCatalog load(ProductionFixture fixture, long storageId) {
        List<ManufacturingItemResponse> baseline = fixture.getJournalPage(
                ProductionJournalQuery.uiDefaults(storageId).toBuilder().pageSize(BASELINE_PAGE_SIZE).build());
        if (baseline.isEmpty()) {
            throw new SkipException("Журнал виробництва порожній — неможливо побудувати сценарії фільтрів");
        }
        Map<Long, Long> productCategoryMap = fixture.getProductCategoryMap();
        Map<Long, ResourceCategoryResponse> categoriesById = new HashMap<>();
        for (ResourceCategoryResponse category : fixture.getResourceCategories()) {
            categoriesById.put(category.getId(), category);
        }
        log.info("Production journal filter catalog: {} baseline records, {} product categories mapped",
                baseline.size(), productCategoryMap.size());
        return new ProductionJournalFilterCatalog(storageId, baseline, productCategoryMap, categoriesById);
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
            ProductionJournalQuery query = ProductionJournalQuery.uiDefaults(storageId)
                    .toBuilder()
                    .categoryId(categoryId)
                    .build();
            if (fixture.getJournalTotalElements(query) == 0) {
                continue;
            }
            return Optional.of(ProductionJournalFilterScenario.builder()
                    .name("category")
                    .anchor(anchor)
                    .query(query)
                    .categoryId(categoryId)
                    .categoryName(category.getName())
                    .build());
        }
        return Optional.empty();
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
