package com.erp.tests.functional.resource_viewer;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.RelocationState;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProductionFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.PagedResourceRelocationViewerResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceCategoryResponse;
import com.erp.models.response.ResourceRelocationSumViewerResponse;
import com.erp.models.response.ResourceRelocationViewerResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Resource Viewer (wolf) filter / guard / pagination contract for journal + sum.
 */
@Slf4j
@Epic("Resource Viewer")
@Feature("Filters and pagination")
public class ResourceViewerFilterApiTest extends BaseFunctionalTest {

    private static final double ALC_PER_UNIT = 2.0;
    private static final double PRODUCE_AMOUNT = 5.0;
    private static final double RELOCATE_AMOUNT = 5.0;
    private static final double STOCK_PAD = 100.0;
    private static final String SUPPLIER_MATCH = "RVW-Supplier-Match";
    private static final String SUPPLIER_OTHER = "RVW-Supplier-Other";

    /** Labels must match tk-ui / business_unit_filter.name (do not rename). */
    private static final String FILTER_PM_414 = "ПМ 414";
    private static final String FILTER_SBS_EXCEPT_PM = "СБС без ПМ 414";
    private static final String FILTER_OTHER = "Інші";

    /** UI default state filter on Resource Relocation Viewer. */
    private static final List<String> UI_ACTIVE_STATES = List.of(
            RelocationState.CREATED.name(),
            RelocationState.FINISHED.name(),
            RelocationState.AUTO_FINISHED.name());

    private TechnologicalMapFixture techMapFixture;
    private ProductionFixture productionFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;

    private Long productionStorageId;
    private Long receiverUnitId;
    private final List<TechnologicalMapResponse> createdMaps = new ArrayList<>();

    private Long categoryAId;
    private Long categoryBId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    @Step("Підготовка fixtures для Resource Viewer filter API")
    public void setupFilterSuite() {
        productionFixture = new ProductionFixture(testContext, apiExecutor);
        techMapFixture = productionFixture.getTechMapFixture();
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);

        techMapFixture.prepareContext();
        resourceFixture.prepareContext();
        relocationFixture.prepareContext();

        productionStorageId = ConfigProvider.getOwner1StorageId();
        // «Інші» = all except ПМ 414 / СБС trees — pick an existing UNIT in that set
        receiverUnitId = resolveUnitVisibleInOthersFilter();
        techMapFixture.setMode(productionStorageId, StorageTechnologicalMapMode.EDIT_ALLOWED);

        List<ResourceCategoryResponse> categories = apiExecutor
                .execute(ApiEndpointDefinition.RESOURCE_CATEGORY_GET_ALL, UserRole.ADMIN)
                .jsonPath()
                .getList("", ResourceCategoryResponse.class);
        if (categories == null || categories.size() < 2) {
            throw new SkipException("Потрібно ≥2 категорії ресурсів для filter API тестів");
        }
        categoryAId = categories.get(0).getId();
        categoryBId = categories.get(1).getId();
        SchemaRegistry.logSchemaCoverage();
    }

    @AfterClass(alwaysRun = true)
    public void teardown() {
        for (TechnologicalMapResponse map : createdMaps) {
            try {
                techMapFixture.deactivateTechMap(UserRole.OWNER_1, map.getId(), productionStorageId);
            } catch (RuntimeException e) {
                log.warn("Tech map deactivate failed id={}: {}", map.getId(), e.getMessage());
            }
        }
        if (techMapFixture != null && productionStorageId != null) {
            try {
                techMapFixture.setMode(productionStorageId, StorageTechnologicalMapMode.READ_ONLY);
            } catch (RuntimeException e) {
                log.warn("Restore READ_ONLY failed: {}", e.getMessage());
            }
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-RVW-API-010")
    @Story("categoryIds as tracking target")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Tracking лише через categoryIds (без resourceIds): інгредієнт у категорії A
            потрапляє в sum; ресурс іншої категорії — ні. Pre-seed нулів вимкнений
            (category filter active).
            """)
    public void testCategoryIdsAsTrackingTarget() {
        String suffix = uniqueSuffix();
        ResourceResponse alcoholA = resourceFixture.createUniqueResource("RVW-CAT-A-" + suffix, categoryAId);
        ResourceResponse alcoholB = resourceFixture.createUniqueResource("RVW-CAT-B-" + suffix, categoryBId);
        ResourceResponse product = resourceFixture.createUniqueResource("RVW-CAT-P-" + suffix);

        TechnologicalMapResponse map = createMap(
                "RVW-FIL-CAT",
                List.of(
                        new ResourceUsageRequest(alcoholA.getId(), ALC_PER_UNIT),
                        new ResourceUsageRequest(alcoholB.getId(), ALC_PER_UNIT)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)));

        ManufacturingItemResponse produced = produce(map, PRODUCE_AMOUNT);
        relocateProduced(product.getId(), RELOCATE_AMOUNT, produced.getBatchNumber());

        Map<String, Object> params = new HashMap<>();
        params.put("categoryIds", List.of(categoryAId));
        params.put("receiverIds", receiverUnitId);

        List<ResourceRelocationSumViewerResponse> sums = fetchSums(params);
        assertThat(amountOf(sums, alcoholA.getId()))
                .isCloseTo(RELOCATE_AMOUNT * ALC_PER_UNIT, within(0.001));
        assertThat(sums.stream().map(ResourceRelocationSumViewerResponse::getResourceId))
                .doesNotContain(alcoholB.getId());
        assertThat(sums.stream().anyMatch(s -> alcoholA.getId().equals(s.getResourceId())
                && s.getAmount() != null
                && s.getAmount().compareTo(BigDecimal.ZERO) == 0))
                .as("category filter: no zero pre-seed for unused tracked ids")
                .isFalse();
    }

    @Test(priority = 20)
    @TestCaseId("TC-RVW-API-011")
    @Story("supplier property AND filter")
    @Severity(SeverityLevel.CRITICAL)
    @Description("supplier=Постачальник AND з resourceIds: збіг → sum>0; інший постачальник → 0")
    public void testSupplierPropertyAndFilter() {
        String suffix = uniqueSuffix();
        ResourceResponse alcohol = resourceFixture.createUniqueResourceWithSupplier(
                "RVW-SUP-A-" + suffix, SUPPLIER_MATCH);
        ResourceResponse product = resourceFixture.createUniqueResource("RVW-SUP-P-" + suffix);

        TechnologicalMapResponse map = createMap(
                "RVW-FIL-SUP",
                List.of(new ResourceUsageRequest(alcohol.getId(), ALC_PER_UNIT)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)));
        ManufacturingItemResponse produced = produce(map, PRODUCE_AMOUNT);
        relocateProduced(product.getId(), RELOCATE_AMOUNT, produced.getBatchNumber());

        Map<String, Object> match = viewerParams(List.of(alcohol.getId()));
        match.put("supplier", SUPPLIER_MATCH);
        assertThat(amountOf(fetchSums(match), alcohol.getId()))
                .isCloseTo(RELOCATE_AMOUNT * ALC_PER_UNIT, within(0.001));

        Map<String, Object> other = viewerParams(List.of(alcohol.getId()));
        other.put("supplier", SUPPLIER_OTHER);
        assertThat(amountOf(fetchSums(other), alcohol.getId())).isEqualTo(0.0);
    }

    @Test(priority = 30)
    @TestCaseId("TC-RVW-API-012")
    @Story("Date range start/end")
    @Severity(SeverityLevel.CRITICAL)
    @Description("start/end відсікають переміщення поза періодом у journal і sum")
    public void testStartEndDateRangeFilter() {
        String suffix = uniqueSuffix();
        ResourceResponse alcohol = resourceFixture.createUniqueResource("RVW-DT-A-" + suffix);

        relocationFixture.ensureStock(productionStorageId, alcohol.getId(), STOCK_PAD);
        RelocationResponse sent = relocationFixture.createSend(
                UserRole.ADMIN, productionStorageId, receiverUnitId, alcohol.getId(), RELOCATE_AMOUNT);

        LocalDate today = LocalDate.now();
        Map<String, Object> inRange = viewerParams(List.of(alcohol.getId()));
        inRange.put("start", today.minusDays(1).toString());
        inRange.put("end", today.plusDays(1).toString());

        assertThat(amountOf(fetchSums(inRange), alcohol.getId()))
                .isCloseTo(RELOCATE_AMOUNT, within(0.001));
        assertThat(fetchJournal(inRange).stream().map(ResourceRelocationViewerResponse::getRelocationId))
                .contains(sent.getId());

        Map<String, Object> outOfRange = viewerParams(List.of(alcohol.getId()));
        outOfRange.put("start", today.minusDays(30).toString());
        outOfRange.put("end", today.minusDays(20).toString());

        assertThat(amountOf(fetchSums(outOfRange), alcohol.getId())).isEqualTo(0.0);
        assertThat(fetchJournal(outOfRange).stream().map(ResourceRelocationViewerResponse::getRelocationId))
                .doesNotContain(sent.getId());
    }

    @Test(priority = 40)
    @TestCaseId("TC-RVW-API-013")
    @Story("Empty response guards")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            hasNoTrackingTarget або hasNoReceiverFilter → 200 і порожній content
            (без resourceIds/categoryIds або без receiverIds/unit* прапорців).
            """)
    public void testEmptyGuardsReturnEmptyPage() {
        Map<String, Object> noTracking = new HashMap<>();
        noTracking.put("receiverIds", receiverUnitId);
        PagedResourceRelocationViewerResponse emptyTracking = fetchPage(noTracking);
        assertThat(emptyTracking.getContent()).isEmpty();

        Map<String, Object> noReceiver = new HashMap<>();
        noReceiver.put("resourceIds", List.of(1L));
        PagedResourceRelocationViewerResponse emptyReceiver = fetchPage(noReceiver);
        assertThat(emptyReceiver.getContent()).isEmpty();
    }

    @Test(priority = 50)
    @TestCaseId("TC-RVW-API-014")
    @Story("Journal pagination and date sort")
    @Severity(SeverityLevel.NORMAL)
    @Description("Пагінація journal: page metadata (totalElements/totalPages); рядки date DESC")
    public void testJournalPaginationAndDateDescSort() {
        String suffix = uniqueSuffix();
        ResourceResponse alcohol = resourceFixture.createUniqueResource("RVW-PG-A-" + suffix);
        relocationFixture.ensureStock(productionStorageId, alcohol.getId(), STOCK_PAD);

        for (int i = 0; i < 3; i++) {
            relocationFixture.createSend(
                    UserRole.ADMIN, productionStorageId, receiverUnitId, alcohol.getId(), 1.0);
        }

        Map<String, Object> page0 = viewerParams(List.of(alcohol.getId()));
        page0.put("page", 0);
        page0.put("size", 1);

        PagedResourceRelocationViewerResponse firstPage = fetchPage(page0);
        assertThat(firstPage.getPage()).isNotNull();
        assertThat(firstPage.getPage().getSize()).isEqualTo(1);
        assertThat(firstPage.getPage().getNumber()).isEqualTo(0);
        assertThat(firstPage.getPage().getTotalElements()).isGreaterThanOrEqualTo(3);
        assertThat(firstPage.getPage().getTotalPages()).isGreaterThanOrEqualTo(3);
        assertThat(firstPage.getContent()).hasSize(1);

        Map<String, Object> all = viewerParams(List.of(alcohol.getId()));
        all.put("page", 0);
        all.put("size", 100);
        List<ResourceRelocationViewerResponse> rows = fetchPage(all).getContent();
        List<LocalDate> dates = rows.stream()
                .map(ResourceRelocationViewerResponse::getDate)
                .toList();
        assertThat(dates)
                .as("journal rows sorted by date descending")
                .isSortedAccordingTo(Comparator.nullsLast(Comparator.reverseOrder()));
    }

    @Test(priority = 60)
    @TestCaseId("TC-RVW-API-015")
    @Story("UI states exclude CANCELLED / RETURNED")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            UI за замовчуванням фільтрує CREATED / FINISHED / AUTO_FINISHED.
            1) Активна видача видима з UI states і невидима з states=CANCELLED|RETURNED.
            2) Якщо вдається перевести в RETURNED (API CREATED→cancel або DB ordinal) —
               з UI states рядок зникає з journal/sums.
            """)
    public void testCancelledAndReturnedExcludedByUiStates() {
        String suffix = uniqueSuffix();
        ResourceResponse alcohol = resourceFixture.createUniqueResource("RVW-ST-" + suffix);
        relocationFixture.ensureStock(productionStorageId, alcohol.getId(), STOCK_PAD);

        RelocationResponse sent = relocationFixture.createSend(
                UserRole.ADMIN, productionStorageId, receiverUnitId, alcohol.getId(), RELOCATE_AMOUNT);

        Map<String, Object> uiParams = viewerParams(List.of(alcohol.getId()));
        uiParams.put("states", UI_ACTIVE_STATES);

        assertThat(fetchJournal(uiParams).stream().map(ResourceRelocationViewerResponse::getRelocationId).toList())
                .as("активна видача видима з UI states")
                .contains(sent.getId());
        assertThat(amountOf(fetchSums(uiParams), alcohol.getId()))
                .as("сума активної видачі")
                .isCloseTo(RELOCATE_AMOUNT, within(0.001));

        Map<String, Object> terminalOnly = viewerParams(List.of(alcohol.getId()));
        terminalOnly.put("states", List.of(
                RelocationState.CANCELLED.name(),
                RelocationState.RETURNED.name()));
        assertThat(fetchJournal(terminalOnly).stream().map(ResourceRelocationViewerResponse::getRelocationId).toList())
                .as("активна видача не потрапляє у фільтр CANCELLED|RETURNED")
                .doesNotContain(sent.getId());

        if (!tryForceRelocationReturned(sent)) {
            Allure.step("Lifecycle RETURNED недоступний без БД (state=" + sent.getState() + ") — "
                    + "перевірено лише фільтр states");
            return;
        }

        // BomKey кешує за фільтром — змінюємо end після DB/API mutate.
        Map<String, Object> afterParams = new HashMap<>(uiParams);
        afterParams.put("end", LocalDate.now().plusDays(1).toString());

        assertThat(fetchJournal(afterParams).stream().map(ResourceRelocationViewerResponse::getRelocationId).toList())
                .as("після RETURNED немає в journal з UI states")
                .doesNotContain(sent.getId());
        assertThat(amountOf(fetchSums(afterParams), alcohol.getId()))
                .as("після RETURNED сума 0 з UI states")
                .isCloseTo(0.0, within(0.001));
    }

    @Test(priority = 70)
    @TestCaseId("TC-RVW-API-016")
    @Story("Receiver groups ПМ 414 / СБС / Інші")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            UI чекбокси шлють unit414Pm / unitSbsExcept414Pm / unitsOther (назви з business_unit_filter).
            Видача на типовий UNIT (не ПМ 414 / не СБС): видно з unitsOther=Інші;
            не видно лише з unit414Pm або unitSbsExcept414Pm.
            """)
    public void testReceiverUnitGroupFilters() {
        String suffix = uniqueSuffix();
        ResourceResponse alcohol = resourceFixture.createUniqueResource("RVW-UG-" + suffix);
        relocationFixture.ensureStock(productionStorageId, alcohol.getId(), STOCK_PAD);
        RelocationResponse sent = relocationFixture.createSend(
                UserRole.ADMIN, productionStorageId, receiverUnitId, alcohol.getId(), RELOCATE_AMOUNT);

        Map<String, Object> otherOnly = new HashMap<>();
        otherOnly.put("resourceIds", List.of(alcohol.getId()));
        otherOnly.put("unitsOther", FILTER_OTHER);

        List<Long> otherIds = fetchJournal(otherOnly).stream()
                .map(ResourceRelocationViewerResponse::getRelocationId)
                .toList();
        if (!otherIds.contains(sent.getId())) {
            throw new AssertionError(
                    "receiverUnitId=" + receiverUnitId + " не входить у фільтр «Інші» "
                            + "(перевірте business_unit_filter / дерево ПМ 414·СБС)");
        }
        assertThat(amountOf(fetchSums(otherOnly), alcohol.getId()))
                .as("unitsOther=Інші включає видачу на типовий UNIT")
                .isCloseTo(RELOCATE_AMOUNT, within(0.001));

        Map<String, Object> pmOnly = new HashMap<>();
        pmOnly.put("resourceIds", List.of(alcohol.getId()));
        pmOnly.put("unit414Pm", FILTER_PM_414);
        assertThat(fetchJournal(pmOnly).stream().map(ResourceRelocationViewerResponse::getRelocationId).toList())
                .as("unit414Pm=ПМ 414 не включає типовий UNIT поза ПМ 414")
                .doesNotContain(sent.getId());

        Map<String, Object> sbsOnly = new HashMap<>();
        sbsOnly.put("resourceIds", List.of(alcohol.getId()));
        sbsOnly.put("unitSbsExcept414Pm", FILTER_SBS_EXCEPT_PM);
        assertThat(fetchJournal(sbsOnly).stream().map(ResourceRelocationViewerResponse::getRelocationId).toList())
                .as("unitSbsExcept414Pm не включає типовий UNIT поза СБС")
                .doesNotContain(sent.getId());
    }

    @Test(priority = 71)
    @TestCaseId("TC-RVW-API-017")
    @Story("receiverIds AND unit414Pm intersection")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Явний receiverIds + unit414Pm → AND (перетин). Якщо UNIT не в ПМ 414 —
            порожній journal (guard NON_MATCHING_RECEIVER), навіть коли unitsOther сам показує рух.
            """)
    public void testReceiverIdsAndUnit414PmIntersection() {
        String suffix = uniqueSuffix();
        ResourceResponse alcohol = resourceFixture.createUniqueResource("RVW-AND-" + suffix);
        relocationFixture.ensureStock(productionStorageId, alcohol.getId(), STOCK_PAD);
        RelocationResponse sent = relocationFixture.createSend(
                UserRole.ADMIN, productionStorageId, receiverUnitId, alcohol.getId(), RELOCATE_AMOUNT);

        Map<String, Object> withOther = new HashMap<>();
        withOther.put("resourceIds", List.of(alcohol.getId()));
        withOther.put("receiverIds", receiverUnitId);
        withOther.put("unitsOther", FILTER_OTHER);
        List<Long> visible = fetchJournal(withOther).stream()
                .map(ResourceRelocationViewerResponse::getRelocationId)
                .toList();
        if (!visible.contains(sent.getId())) {
            throw new AssertionError(
                    "receiverUnitId=" + receiverUnitId + " не в «Інші» — AND-кейс з ПМ 414 нерелевантний");
        }

        Map<String, Object> andPm = new HashMap<>();
        andPm.put("resourceIds", List.of(alcohol.getId()));
        andPm.put("receiverIds", receiverUnitId);
        andPm.put("unit414Pm", FILTER_PM_414);
        assertThat(fetchJournal(andPm))
                .as("receiverIds∩ПМ 414 порожній для UNIT поза ПМ 414")
                .isEmpty();
        assertThat(amountOf(fetchSums(andPm), alcohol.getId())).isCloseTo(0.0, within(0.001));
    }

    /**
     * @return {@code true} if relocation was moved to RETURNED (API or DB); {@code false} if skipped.
     */
    private boolean tryForceRelocationReturned(RelocationResponse sent) {
        if (sent.getState() == RelocationState.CREATED || sent.getState() == RelocationState.CANCELLED) {
            Allure.step("API: CANCELLED → RETURNED для relocation " + sent.getId(), () -> {
                if (sent.getState() == RelocationState.CREATED) {
                    relocationFixture.resolve(
                            UserRole.ADMIN, sent.getId(), receiverUnitId, RelocationState.CANCELLED);
                }
                relocationFixture.resolve(
                        UserRole.ADMIN, sent.getId(), productionStorageId, RelocationState.RETURNED);
            });
            return true;
        }
        if (getDbHelper() == null) {
            return false;
        }
        // Hibernate maps Relocation.state without @Enumerated → ORDINAL (smallint).
        Allure.step("DB: UPDATE relocation.state=RETURNED(ordinal) id=" + sent.getId(), () -> {
            String sql = "UPDATE relocation SET state = ? WHERE id = ?";
            try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(sql)) {
                ps.setInt(1, RelocationState.RETURNED.ordinal());
                ps.setLong(2, sent.getId());
                assertThat(ps.executeUpdate())
                        .as("оновлено relocation id=%s", sent.getId())
                        .isEqualTo(1);
            } catch (SQLException e) {
                throw new IllegalStateException(
                        "Не вдалося оновити state relocation id=" + sent.getId() + ": " + e.getMessage(), e);
            }
        });
        return true;
    }

    private TechnologicalMapResponse createMap(
            String namePrefix,
            List<ResourceUsageRequest> inputs,
            List<ResourceUsageRequest> outputs) {
        TechnologicalMapRequest request = TechnologicalMapDataFactory
                .createProductionMapWithStorages(namePrefix, inputs, outputs, Set.of(productionStorageId))
                .build();
        TechnologicalMapResponse map = Allure.step(
                "Створити техкарту " + namePrefix,
                () -> techMapFixture.createTechMapWithRequest(UserRole.ADMIN, request));
        createdMaps.add(map);
        return map;
    }

    private ManufacturingItemResponse produce(TechnologicalMapResponse map, double amount) {
        productionFixture.ensureStockForTechMapInputs(productionStorageId, map, STOCK_PAD);
        return productionFixture.createWithUniqueBatch(
                UserRole.ADMIN, productionStorageId, map, amount);
    }

    private RelocationResponse relocateProduced(Long productId, double amount, String batchNumber) {
        return relocationFixture.createSendWithBatch(
                UserRole.ADMIN,
                productionStorageId,
                receiverUnitId,
                productId,
                amount,
                batchNumber,
                true);
    }

    /**
     * «Інші» filter = include all minus descendants of exclude roots (ПМ 414 / СБС).
     * Prefer DB resolution; fall back to API probe via a short relocation sample.
     */
    private Long resolveUnitVisibleInOthersFilter() {
        if (getDbHelper() != null) {
            try {
                Long fromDb = resolveUnitInOthersViaDb();
                if (fromDb != null) {
                    log.info("RVW filter: receiverUnitId={} from business_unit_filter «Інші» (DB)", fromDb);
                    return fromDb;
                }
            } catch (SQLException e) {
                log.warn("RVW filter: DB resolve for «Інші» failed: {}", e.getMessage());
            }
        }
        return resolveUnitInOthersViaApiProbe();
    }

    private Long resolveUnitInOthersViaDb() throws SQLException {
        String includeIds;
        String excludeIds;
        String usageType;
        String sql = "SELECT include_ids, exclude_ids, usage_type FROM business_unit_filter WHERE lower(name) = lower(?)";
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(sql)) {
            ps.setString(1, FILTER_OTHER);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                includeIds = rs.getString("include_ids");
                excludeIds = rs.getString("exclude_ids");
                usageType = rs.getString("usage_type");
            }
        }

        Set<Long> excluded = new HashSet<>();
        if (excludeIds != null && !excludeIds.isBlank()) {
            List<Long> roots = Arrays.stream(excludeIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::valueOf)
                    .toList();
            excluded.addAll(expandStorageDescendants(roots));
        }

        Set<Long> included = null;
        if (includeIds != null && !includeIds.isBlank() && !"all".equalsIgnoreCase(includeIds.trim())) {
            List<Long> roots = Arrays.stream(includeIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::valueOf)
                    .toList();
            included = "DIRECT".equalsIgnoreCase(usageType)
                    ? new HashSet<>(roots)
                    : expandStorageDescendants(roots);
        }

        final Set<Long> includeSet = included;
        Response response = apiExecutor.execute(ApiEndpointDefinition.STORAGE_GET_ALL, UserRole.ADMIN);
        List<StorageResponse> storages = DatabaseIntegrityValidator.extractList(response, StorageResponse.class);
        return storages.stream()
                .filter(s -> "UNIT".equalsIgnoreCase(s.getType()))
                .filter(s -> Boolean.TRUE.equals(s.getActive()) || s.getActive() == null)
                .map(StorageResponse::getId)
                .filter(id -> !excluded.contains(id))
                .filter(id -> includeSet == null || includeSet.contains(id))
                .findFirst()
                .orElse(null);
    }

    private Set<Long> expandStorageDescendants(List<Long> roots) throws SQLException {
        if (roots.isEmpty()) {
            return Set.of();
        }
        String placeholders = roots.stream().map(r -> "?").collect(Collectors.joining(","));
        String sql = """
                WITH RECURSIVE tree AS (
                    SELECT id FROM storage WHERE id IN (%s)
                    UNION ALL
                    SELECT s.id FROM storage s JOIN tree t ON s.parent_id = t.id
                )
                SELECT id FROM tree
                """.formatted(placeholders);
        Set<Long> ids = new HashSet<>();
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(sql)) {
            for (int i = 0; i < roots.size(); i++) {
                ps.setLong(i + 1, roots.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
        }
        return ids;
    }

    private Long resolveUnitInOthersViaApiProbe() {
        Response response = apiExecutor.execute(ApiEndpointDefinition.STORAGE_GET_ALL, UserRole.ADMIN);
        List<StorageResponse> units = DatabaseIntegrityValidator.extractList(response, StorageResponse.class)
                .stream()
                .filter(s -> "UNIT".equalsIgnoreCase(s.getType()))
                .filter(s -> Boolean.TRUE.equals(s.getActive()) || s.getActive() == null)
                .limit(12)
                .toList();
        if (units.isEmpty()) {
            throw new AssertionError("No UNIT storages available to probe «Інші» filter");
        }

        ResourceResponse probe = resourceFixture.createUniqueResource("RVW-OTH-" + uniqueSuffix());
        relocationFixture.ensureStock(productionStorageId, probe.getId(), 50.0);
        Map<Long, Long> relocToUnit = new HashMap<>();
        for (StorageResponse unit : units) {
            try {
                RelocationResponse sent = relocationFixture.createSend(
                        UserRole.ADMIN, productionStorageId, unit.getId(), probe.getId(), 1.0);
                relocToUnit.put(sent.getId(), unit.getId());
            } catch (RuntimeException e) {
                log.warn("RVW probe send to unit {} failed: {}", unit.getId(), e.getMessage());
            }
        }

        Map<String, Object> otherOnly = new HashMap<>();
        otherOnly.put("resourceIds", List.of(probe.getId()));
        otherOnly.put("unitsOther", FILTER_OTHER);
        Set<Long> visible = fetchJournal(otherOnly).stream()
                .map(ResourceRelocationViewerResponse::getRelocationId)
                .collect(Collectors.toSet());

        return relocToUnit.entrySet().stream()
                .filter(e -> visible.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No UNIT from probe sample appears in unitsOther=«Інші» on this env"));
    }

    private Map<String, Object> viewerParams(List<Long> resourceIds) {
        Map<String, Object> params = new HashMap<>();
        params.put("resourceIds", resourceIds);
        params.put("receiverIds", receiverUnitId);
        return params;
    }

    private List<ResourceRelocationSumViewerResponse> fetchSums(Map<String, Object> params) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_GET,
                UserRole.RESOURCE_VIEWER,
                params);
        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_GET);
        PagedResourceRelocationViewerResponse page = response.as(PagedResourceRelocationViewerResponse.class);
        return page.getSums() != null ? page.getSums() : List.of();
    }

    private List<ResourceRelocationViewerResponse> fetchJournal(Map<String, Object> params) {
        return fetchPage(params).getContent();
    }

    private PagedResourceRelocationViewerResponse fetchPage(Map<String, Object> params) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_GET,
                UserRole.RESOURCE_VIEWER,
                params);
        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_GET);
        PagedResourceRelocationViewerResponse page = response.as(PagedResourceRelocationViewerResponse.class);
        if (page.getContent() == null) {
            page.setContent(List.of());
        }
        return page;
    }

    private static double amountOf(List<ResourceRelocationSumViewerResponse> sums, Long resourceId) {
        return sums.stream()
                .filter(s -> resourceId.equals(s.getResourceId()))
                .map(ResourceRelocationSumViewerResponse::getAmount)
                .filter(a -> a != null)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();
    }

    private static String uniqueSuffix() {
        return String.valueOf(System.currentTimeMillis());
    }
}
