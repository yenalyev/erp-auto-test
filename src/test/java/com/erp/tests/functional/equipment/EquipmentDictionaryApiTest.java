package com.erp.tests.functional.equipment;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.EquipmentDictionaryFixture;
import com.erp.models.response.EquipmentModelResponse;
import com.erp.models.response.SimpleEntityResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.*;
import org.testng.annotations.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Master Data")
@Feature("Довідники — Обладнання")
public class EquipmentDictionaryApiTest extends BaseFunctionalTest {
    private EquipmentDictionaryFixture fixture;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareDictionary() {
        fixture = new EquipmentDictionaryFixture(testContext, apiExecutor);
        fixture.prepare();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupDictionary() { if (fixture != null) fixture.cleanup(); }

    @Test
    @TestCaseId("TC-EQD-API-001")
    @Description("Модель із трьома одиницями у двох категоріях має один рядок і дві унікальні категорії. "
            + "Категорії сусідньої моделі не домішуються. createdAt — дата моделі; createdBy заповнений для нових моделей.")
    public void modelAggregatesAllDistinctCategoriesAndMetadata() {
        var result = fixture.list(Map.of());
        assertThat(result.getPage().getTotalElements()).isEqualTo(4);
        assertThat(result.getContent()).extracting(EquipmentModelResponse::getId).doesNotHaveDuplicates();
        for (var model : result.getContent()) {
            assertThat(model.getCreatedAt()).isNotNull();
            assertThat(model.getCreatedBy()).isNotBlank();
            List<Long> expected = model.getName().equals(fixture.alpha)
                    ? List.of(fixture.categoryA(), fixture.categoryB())
                    : List.of(model.getName().equals(fixture.ghe) ? fixture.categoryA() : fixture.categoryB());
            assertThat(model.getCategories()).extracting(SimpleEntityResponse::getId)
                    .containsExactlyInAnyOrderElementsOf(expected);
            for (var category : model.getCategories()) {
                assertThat(category.getName()).isEqualTo(fixture.categoryName(category.getId()));
            }
        }
    }

    @Test
    @TestCaseId("TC-EQD-API-002")
    @Description("Без sort API застосовує український алфавітний порядок А, Ґ, Є, Я; порядок створення протилежний.")
    public void defaultsToUkrainianNameAscending() {
        assertThat(names(Map.of())).containsExactlyElementsOf(fixture.ascendingNames());
    }

    @Test
    @TestCaseId("TC-EQD-API-003")
    @Description("Обидва напрями modelName та createdAt перевіряються незалежно від порядку створення. "
            + "У всіх результатах зберігаються ті самі чотири моделі.")
    public void supportsBothDirectionsForNameAndCreationDate() {
        for (String direction : List.of("ASC", "DESC")) {
            List<String> expected = new ArrayList<>(fixture.ascendingNames());
            if (direction.equals("DESC")) Collections.reverse(expected);
            assertThat(names(Map.of("sort", "modelName," + direction))).containsExactlyElementsOf(expected);
            var models = fixture.list(Map.of("sort", "createdAt," + direction)).getContent();
            assertThat(models).extracting(EquipmentModelResponse::getName)
                    .containsExactlyInAnyOrderElementsOf(fixture.ascendingNames());
            assertThat(models).extracting(EquipmentModelResponse::getCreatedAt)
                    .doesNotContainNull().isSortedAccordingTo(direction.equals("ASC")
                            ? Comparator.naturalOrder() : Comparator.reverseOrder());
            assertThat(models.stream().map(EquipmentModelResponse::getCreatedAt).distinct().count())
                    .as("Дані повинні розрізняти сортування за датою").isGreaterThan(1);
        }
    }

    @Test
    @TestCaseId("TC-EQD-API-004")
    @Description("Повна назва, підрядок, інший регістр і відсутній збіг; порожній рядок еквівалентний відсутньому фільтру.")
    public void searchesNameAndHandlesNoMatches() {
        assertThat(names(Map.of("modelName", fixture.alpha))).containsExactly(fixture.alpha);
        assertThat(names(Map.of("modelName", fixture.marker.toUpperCase(Locale.ROOT))))
                .containsExactlyElementsOf(fixture.ascendingNames());
        var empty = fixture.list(Map.of("modelName", fixture.marker + "-missing"));
        assertThat(empty.getContent()).isEmpty();
        assertThat(empty.getPage().getTotalElements()).isZero();
        var unfiltered = apiExecutor.executeWithQueryParams(ApiEndpointDefinition.EQUIPMENT_MODELS_GET_PAGE,
                UserRole.ADMIN, Map.of("size", 20));
        assertThat(unfiltered.statusCode()).isEqualTo(200);
        var blank = fixture.list(Map.of("modelName", ""));
        assertThat(blank.getPage().getTotalElements()).isEqualTo(unfiltered.jsonPath().getLong("page.totalElements"));
        assertThat(blank.getContent()).extracting(EquipmentModelResponse::getId)
                .containsExactlyElementsOf(unfiltered.jsonPath().getList("content.id", Long.class));
    }

    @Test
    @TestCaseId("TC-EQD-API-005")
    @Description("Одна категорія; кілька категорій як OR; категорія без обладнання. "
            + "Фільтр B не обрізає категорію A в рядку моделі, що належить обом категоріям.")
    public void filtersCategoriesWithoutTruncatingAggregates() {
        assertThat(names(Map.of("categoryIds", List.of(fixture.categoryA()))))
                .containsExactly(fixture.alpha, fixture.ghe);
        var categoryB = fixture.list(Map.of("categoryIds", List.of(fixture.categoryB())));
        assertThat(categoryB.getContent()).extracting(EquipmentModelResponse::getName)
                .containsExactly(fixture.alpha, fixture.ye, fixture.omega);
        assertThat(categoryB.getContent().stream().filter(m -> m.getName().equals(fixture.alpha))
                .findFirst().orElseThrow().getCategories()).extracting(SimpleEntityResponse::getId)
                .containsExactlyInAnyOrder(fixture.categoryA(), fixture.categoryB());
        assertThat(names(Map.of("categoryIds", List.of(fixture.categoryA(), fixture.categoryB()))))
                .containsExactlyElementsOf(fixture.ascendingNames());
        var unused = fixture.list(Map.of("categoryIds", List.of(fixture.unusedCategory())));
        assertThat(unused.getContent()).isEmpty();
        assertThat(unused.getPage().getTotalElements()).isZero();
    }

    @Test
    @TestCaseId("TC-EQD-API-006")
    @Description("Назва AND категорія: збіг, суперечливі фільтри та скидання категорії повертає вибрану за назвою модель.")
    public void combinesAndClearsFilters() {
        assertThat(names(Map.of("modelName", fixture.ghe, "categoryIds", List.of(fixture.categoryA()))))
                .containsExactly(fixture.ghe);
        assertThat(names(Map.of("modelName", fixture.ghe, "categoryIds", List.of(fixture.categoryB())))).isEmpty();
        assertThat(names(Map.of("modelName", fixture.ghe))).containsExactly(fixture.ghe);
        assertThat(names(Map.of())).containsExactlyElementsOf(fixture.ascendingNames());
    }

    @Test
    @TestCaseId("TC-EQD-API-007")
    @Description("Дві сторінки по дві моделі: глобальний порядок, сталі totals, відсутність повторів; фільтр діє до пагінації.")
    public void paginatesSortedFilteredModelsWithoutDuplicates() {
        List<String> all = new ArrayList<>();
        for (int number = 0; number < 2; number++) {
            var result = fixture.list(Map.of("page", number, "size", 2));
            assertThat(result.getPage().getNumber()).isEqualTo(number);
            assertThat(result.getPage().getSize()).isEqualTo(2);
            assertThat(result.getPage().getTotalElements()).isEqualTo(4);
            assertThat(result.getPage().getTotalPages()).isEqualTo(2);
            all.addAll(result.getContent().stream().map(EquipmentModelResponse::getName).toList());
        }
        assertThat(all).containsExactlyElementsOf(fixture.ascendingNames());
        var filtered = fixture.list(Map.of("size", 1, "page", 1, "categoryIds", List.of(fixture.categoryA())));
        assertThat(filtered.getPage().getTotalElements()).isEqualTo(2);
        assertThat(filtered.getContent()).extracting(EquipmentModelResponse::getName).containsExactly(fixture.ghe);
    }

    private List<String> names(Map<String, ?> filters) {
        return fixture.list(filters).getContent().stream().map(EquipmentModelResponse::getName).toList();
    }
}
