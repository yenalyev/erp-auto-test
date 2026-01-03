package com.erp.builders.techmap;

import com.erp.builders.common.FakerProvider;
import com.erp.builders.common.TestDataBuilder;
import com.erp.models.response.*;
import com.erp.utils.CollectionUtils;
import com.github.javafaker.Faker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 🏭 Test Data Builder for Technological Map entities
 * <p>
 * Supports two modes:
 * 1. **Mock mode** (default): Generates fake resources for isolated testing
 * 2. **Real data mode**: Uses actual resources from database for integration testing
 * <p>
 * Usage examples:
 * <pre>
 * // Mock mode - isolated testing
 * TechnologicalMapResponse mockMap = new TechnologicalMapTestData().random();
 *
 * // Real data mode - integration testing
 * List&lt;ResourceResponse&gt; realResources = resourceService.getAllResources();
 * List&lt;MeasurementUnitResponse&gt; realUnits = measurementService.getAllUnits();
 *
 * TechnologicalMapResponse realMap = new TechnologicalMapTestData()
 *     .withRealResources(realResources)
 *     .withRealMeasurementUnits(realUnits)
 *     .withInputResources(3)
 *     .withOutputResources(1)
 *     .random();
 *
 * // Using specific resources
 * TechnologicalMapResponse customMap = new TechnologicalMapTestData()
 *     .withInputResourcesList(specificInputs)
 *     .withOutputResourcesList(specificOutputs)
 *     .random();
 * </pre>
 */
public class TechnologicalMapTestData implements TestDataBuilder<TechnologicalMapResponse, TechnologicalMapResponse> {

    private final Faker faker = FakerProvider.ukrainian();
    private static final AtomicInteger counter = new AtomicInteger(1);

    // Configuration fields
    private String name;
    private Integer inputResourcesCount = 2;
    private Integer outputResourcesCount = 1;
    private Integer alternativeGroupsCount = 1;
    private boolean includeAlternatives = true;

    // 🔑 Real data from database
    private List<ResourceResponse> availableResources;
    private List<MeasurementUnitResponse> availableMeasurementUnits;

    // 🎯 Specific resources to use
    private List<ResourceUsageResponse> customInputResources;
    private List<ResourceUsageResponse> customOutputResources;
    private List<AlternativeGroupResponse> customAlternatives;

    // Fallback mock data
    private static final List<String> MATERIALS = Arrays.asList(
            "Деревина дубова", "Деревина соснова", "Фанера", "МДФ плита",
            "Метал листовий", "Алюміній", "Сталь", "Нержавіюча сталь",
            "Пластик ABS", "Поліпропілен", "Скло", "Тканина",
            "Фарба", "Лак", "Клей", "Саморізи", "Болти"
    );

    private static final List<String> PRODUCTS = Arrays.asList(
            "Стіл обідній", "Стілець", "Шафа", "Полиця",
            "Двері", "Вікно", "Рама", "Панель",
            "Деталь А", "Деталь Б", "Вузол С", "Блок Д"
    );

    private static final List<MeasurementUnit> MOCK_UNITS = Arrays.asList(
            new MeasurementUnit(1L, "Кілограм", "кг"),
            new MeasurementUnit(2L, "Метр", "м"),
            new MeasurementUnit(3L, "Квадратний метр", "м²"),
            new MeasurementUnit(4L, "Кубічний метр", "м³"),
            new MeasurementUnit(5L, "Літр", "л"),
            new MeasurementUnit(6L, "Штука", "шт"),
            new MeasurementUnit(7L, "Упаковка", "упак"),
            new MeasurementUnit(8L, "Грам", "г")
    );

    // Inner class for mock measurement units
    private static class MeasurementUnit {
        Long id;
        String name;
        String shortName;

        MeasurementUnit(Long id, String name, String shortName) {
            this.id = id;
            this.name = name;
            this.shortName = shortName;
        }
    }

    @Override
    public TechnologicalMapResponse random() {
        String mapName = (name != null) ? name : generateRandomMapName();

        List<ResourceUsageResponse> inputs = (customInputResources != null)
                ? customInputResources
                : generateInputResources(inputResourcesCount);

        List<ResourceUsageResponse> outputs = (customOutputResources != null)
                ? customOutputResources
                : generateOutputResources(outputResourcesCount);

        List<AlternativeGroupResponse> alternatives = (customAlternatives != null)
                ? customAlternatives
                : (includeAlternatives ? generateAlternativeGroups(alternativeGroupsCount) : new ArrayList<>());

        return TechnologicalMapResponse.builder()
                .name(mapName)
                .input(inputs)
                .output(outputs)
                .alternatives(alternatives)
                .build();
    }

    @Override
    public TechnologicalMapResponse fixed() {
        return TechnologicalMapResponse.builder()
                .name("Технологічна карта - Виробництво стола дерев'яного (тестова)")
                .input(getFixedInputResources())
                .output(getFixedOutputResources())
                .alternatives(getFixedAlternativeGroups())
                .build();
    }

    @Override
    public Class<TechnologicalMapResponse> getResponseClass() {
        return TechnologicalMapResponse.class;
    }

    // ============================================
    // 🔧 Configuration Methods - Basic
    // ============================================

    public TechnologicalMapTestData withName(String name) {
        this.name = name;
        return this;
    }

    public TechnologicalMapTestData withInputResources(int count) {
        this.inputResourcesCount = count;
        return this;
    }

    public TechnologicalMapTestData withOutputResources(int count) {
        this.outputResourcesCount = count;
        return this;
    }

    public TechnologicalMapTestData withAlternativeGroups(int count) {
        this.alternativeGroupsCount = count;
        return this;
    }

    public TechnologicalMapTestData withoutAlternatives() {
        this.includeAlternatives = false;
        return this;
    }

    // ============================================
    // 🔑 Configuration Methods - Real Data Injection
    // ============================================

    /**
     * 🎯 Provide real resources from database
     * <p>
     * These resources will be randomly selected when generating input/output
     *
     * @param resources List of actual resources from database
     * @return builder instance
     */
    public TechnologicalMapTestData withRealResources(List<ResourceResponse> resources) {
        if (resources == null || resources.isEmpty()) {
            throw new IllegalArgumentException("Resources list cannot be null or empty");
        }
        this.availableResources = new ArrayList<>(resources);
        return this;
    }

    /**
     * 🎯 Provide real measurement units from database
     *
     * @param units List of actual measurement units from database
     * @return builder instance
     */
    public TechnologicalMapTestData withRealMeasurementUnits(List<MeasurementUnitResponse> units) {
        if (units == null || units.isEmpty()) {
            throw new IllegalArgumentException("Measurement units list cannot be null or empty");
        }
        this.availableMeasurementUnits = new ArrayList<>(units);
        return this;
    }

    /**
     * 🎯 Set specific input resources to use
     * <p>
     * Use this when you need exact control over input resources
     *
     * @param inputResources Exact list of input resources with amounts
     * @return builder instance
     */
    public TechnologicalMapTestData withInputResourcesList(List<ResourceUsageResponse> inputResources) {
        this.customInputResources = inputResources;
        return this;
    }

    /**
     * 🎯 Set specific output resources to use
     *
     * @param outputResources Exact list of output resources with amounts
     * @return builder instance
     */
    public TechnologicalMapTestData withOutputResourcesList(List<ResourceUsageResponse> outputResources) {
        this.customOutputResources = outputResources;
        return this;
    }

    /**
     * 🎯 Set specific alternative groups to use
     *
     * @param alternatives Exact list of alternative groups
     * @return builder instance
     */
    public TechnologicalMapTestData withAlternativesList(List<AlternativeGroupResponse> alternatives) {
        this.customAlternatives = alternatives;
        return this;
    }

    /**
     * 🎯 Create input resource usage from existing resource
     *
     * @param resource Existing resource from database
     * @param amount Amount to use
     * @return ResourceUsageResponse ready to use
     */
    public ResourceUsageResponse createInputUsage(ResourceResponse resource, Double amount) {
        return ResourceUsageResponse.builder()
                .resource(resource)
                .amount(amount)
                .build();
    }

    /**
     * 🎯 Create output resource usage from existing resource
     *
     * @param resource Existing resource from database
     * @param amount Amount to produce
     * @return ResourceUsageResponse ready to use
     */
    public ResourceUsageResponse createOutputUsage(ResourceResponse resource, Double amount) {
        return ResourceUsageResponse.builder()
                .resource(resource)
                .amount(amount)
                .build();
    }

    // ============================================
    // 🏗️ Preset Configurations
    // ============================================

    public TechnologicalMapTestData minimal() {
        this.inputResourcesCount = 1;
        this.outputResourcesCount = 1;
        this.includeAlternatives = false;
        return this;
    }

    public TechnologicalMapTestData complex() {
        this.inputResourcesCount = 5;
        this.outputResourcesCount = 2;
        this.alternativeGroupsCount = 3;
        this.includeAlternatives = true;
        return this;
    }

    // ============================================
    // 🏗️ Resource Generation Methods
    // ============================================

    private String generateRandomMapName() {
        String product = PRODUCTS.get(faker.random().nextInt(PRODUCTS.size()));
        return String.format("Технологічна карта - Виробництво: %s #%d",
                product, counter.getAndIncrement());
    }

    private List<ResourceUsageResponse> generateInputResources(int count) {
        if (availableResources != null && !availableResources.isEmpty()) {
            // 🎯 Use real resources from database
            return generateRealResourceUsages(count, availableResources);
        } else {
            // 🎲 Use mock data
            return IntStream.range(0, count)
                    .mapToObj(i -> createMockResourceUsage(
                            MATERIALS.get(faker.random().nextInt(MATERIALS.size())),
                            faker.number().randomDouble(2, 1, 100)
                    ))
                    .collect(Collectors.toList());
        }
    }

    private List<ResourceUsageResponse> generateOutputResources(int count) {
        if (availableResources != null && !availableResources.isEmpty()) {
            // 🎯 Use real resources from database
            return generateRealResourceUsages(count, availableResources);
        } else {
            // 🎲 Use mock data
            return IntStream.range(0, count)
                    .mapToObj(i -> createMockResourceUsage(
                            PRODUCTS.get(faker.random().nextInt(PRODUCTS.size())),
                            faker.number().randomDouble(2, 1, 10)
                    ))
                    .collect(Collectors.toList());
        }
    }

    /**
     * 🎯 Generate resource usages from real database resources
     */
    private List<ResourceUsageResponse> generateRealResourceUsages(int count, List<ResourceResponse> resources) {
        if (count > resources.size()) {
            throw new IllegalStateException(
                    String.format("Requested %d resources but only %d available in database",
                            count, resources.size())
            );
        }

        // Randomly select resources without duplicates
        List<ResourceResponse> selectedResources = CollectionUtils.getRandomSubList(resources, count);

        return selectedResources.stream()
                .map(resource -> ResourceUsageResponse.builder()
                        .resource(resource)
                        .amount(faker.number().randomDouble(2, 1, 100))
                        .build())
                .collect(Collectors.toList());
    }

    private List<AlternativeGroupResponse> generateAlternativeGroups(int count) {
        if (availableResources != null && !availableResources.isEmpty()) {
            // 🎯 Use real resources for alternatives
            return IntStream.range(0, count)
                    .mapToObj(i -> createRealAlternativeGroup(
                            "Альтернативна група #" + (i + 1),
                            faker.bool().bool(),
                            faker.random().nextInt(2, Math.min(4, availableResources.size()))
                    ))
                    .collect(Collectors.toList());
        } else {
            // 🎲 Use mock data
            return IntStream.range(0, count)
                    .mapToObj(i -> createMockAlternativeGroup(
                            "Альтернативна група #" + (i + 1),
                            faker.bool().bool(),
                            faker.random().nextInt(2, 4)
                    ))
                    .collect(Collectors.toList());
        }
    }

    /**
     * 🎯 Create alternative group from real database resources
     */
    private AlternativeGroupResponse createRealAlternativeGroup(String groupName, Boolean required, int optionsCount) {
        List<ResourceResponse> selectedResources = CollectionUtils.getRandomSubList(availableResources, optionsCount);

        List<AlternativeOptionResponse> options = IntStream.range(0, selectedResources.size())
                .mapToObj(i -> AlternativeOptionResponse.builder()
                        .id(faker.number().randomNumber(4, true))
                        .name("Варіант: " + selectedResources.get(i).getName())
                        .resource(selectedResources.get(i))
                        .mainOption(i == 0) // First option is main
                        .amount(faker.number().randomDouble(2, 1, 50))
                        .build())
                .collect(Collectors.toList());

        return AlternativeGroupResponse.builder()
                .id(faker.number().randomNumber(4, true))
                .name(groupName)
                .required(required)
                .options(options)
                .build();
    }

    // ============================================
    // 🎲 Mock Data Generation Methods
    // ============================================

    private ResourceUsageResponse createMockResourceUsage(String resourceName, Double amount) {
        MeasurementUnitResponse unit;

        if (availableMeasurementUnits != null && !availableMeasurementUnits.isEmpty()) {
            // 🎯 Use real measurement unit
            unit = availableMeasurementUnits.get(
                    faker.random().nextInt(availableMeasurementUnits.size())
            );
        } else {
            // 🎲 Use mock measurement unit
            MeasurementUnit mockUnit = MOCK_UNITS.get(faker.random().nextInt(MOCK_UNITS.size()));
            unit = MeasurementUnitResponse.builder()
                    .id(mockUnit.id)
                    .name(mockUnit.name)
                    .shortName(mockUnit.shortName)
                    .build();
        }

        return ResourceUsageResponse.builder()
                .resource(ResourceResponse.builder()
                        .id(faker.number().randomNumber(4, true))
                        .name(resourceName + " (тест)")
                        .unit(unit)
                        .build())
                .amount(amount)
                .build();
    }

    private AlternativeGroupResponse createMockAlternativeGroup(String groupName, Boolean required, int optionsCount) {
        List<AlternativeOptionResponse> options = IntStream.range(0, optionsCount)
                .mapToObj(i -> createMockAlternativeOption(
                        MATERIALS.get(faker.random().nextInt(MATERIALS.size())),
                        i == 0
                ))
                .collect(Collectors.toList());

        return AlternativeGroupResponse.builder()
                .id(faker.number().randomNumber(4, true))
                .name(groupName)
                .required(required)
                .options(options)
                .build();
    }

    private AlternativeOptionResponse createMockAlternativeOption(String materialName, boolean isMain) {
        MeasurementUnitResponse unit;

        if (availableMeasurementUnits != null && !availableMeasurementUnits.isEmpty()) {
            unit = availableMeasurementUnits.get(
                    faker.random().nextInt(availableMeasurementUnits.size())
            );
        } else {
            MeasurementUnit mockUnit = MOCK_UNITS.get(faker.random().nextInt(MOCK_UNITS.size()));
            unit = MeasurementUnitResponse.builder()
                    .id(mockUnit.id)
                    .name(mockUnit.name)
                    .shortName(mockUnit.shortName)
                    .build();
        }

        return AlternativeOptionResponse.builder()
                .id(faker.number().randomNumber(4, true))
                .name("Варіант: " + materialName)
                .resource(ResourceResponse.builder()
                        .id(faker.number().randomNumber(4, true))
                        .name(materialName + " (тест)")
                        .unit(unit)
                        .build())
                .mainOption(isMain)
                .amount(faker.number().randomDouble(2, 1, 50))
                .build();
    }

    // ============================================
    // 📋 Fixed Data Methods
    // ============================================

    private List<ResourceUsageResponse> getFixedInputResources() {
        // ... (залишається без змін)
        List<ResourceUsageResponse> inputs = new ArrayList<>();

        inputs.add(ResourceUsageResponse.builder()
                .resource(ResourceResponse.builder()
                        .id(1001L)
                        .name("Деревина дубова (тест)")
                        .unit(MeasurementUnitResponse.builder()
                                .id(3L)
                                .name("Квадратний метр")
                                .shortName("м²")
                                .build())
                        .build())
                .amount(2.5)
                .build());

        inputs.add(ResourceUsageResponse.builder()
                .resource(ResourceResponse.builder()
                        .id(1002L)
                        .name("Лак меблевий (тест)")
                        .unit(MeasurementUnitResponse.builder()
                                .id(5L)
                                .name("Літр")
                                .shortName("л")
                                .build())
                        .build())
                .amount(0.5)
                .build());

        inputs.add(ResourceUsageResponse.builder()
                .resource(ResourceResponse.builder()
                        .id(1003L)
                        .name("Саморізи 4x50 (тест)")
                        .unit(MeasurementUnitResponse.builder()
                                .id(6L)
                                .name("Штука")
                                .shortName("шт")
                                .build())
                        .build())
                .amount(20.0)
                .build());

        return inputs;
    }

    private List<ResourceUsageResponse> getFixedOutputResources() {
        // ... (залишається без змін)
        List<ResourceUsageResponse> outputs = new ArrayList<>();

        outputs.add(ResourceUsageResponse.builder()
                .resource(ResourceResponse.builder()
                        .id(2001L)
                        .name("Стіл дерев'яний обідній (тест)")
                        .unit(MeasurementUnitResponse.builder()
                                .id(6L)
                                .name("Штука")
                                .shortName("шт")
                                .build())
                        .build())
                .amount(1.0)
                .build());

        return outputs;
    }

    private List<AlternativeGroupResponse> getFixedAlternativeGroups() {
        // ... (залишається без змін)
        List<AlternativeGroupResponse> groups = new ArrayList<>();

        List<AlternativeOptionResponse> woodOptions = new ArrayList<>();

        woodOptions.add(AlternativeOptionResponse.builder()
                .id(3001L)
                .name("Варіант: Деревина дубова")
                .resource(ResourceResponse.builder()
                        .id(1001L)
                        .name("Деревина дубова (тест)")
                        .unit(MeasurementUnitResponse.builder()
                                .id(3L)
                                .name("Квадратний метр")
                                .shortName("м²")
                                .build())
                        .build())
                .mainOption(true)
                .amount(2.5)
                .build());

        woodOptions.add(AlternativeOptionResponse.builder()
                .id(3002L)
                .name("Варіант: Деревина соснова")
                .resource(ResourceResponse.builder()
                        .id(1004L)
                        .name("Деревина соснова (тест)")
                        .unit(MeasurementUnitResponse.builder()
                                .id(3L)
                                .name("Квадратний метр")
                                .shortName("м²")
                                .build())
                        .build())
                .mainOption(false)
                .amount(3.0)
                .build());

        groups.add(AlternativeGroupResponse.builder()
                .id(4001L)
                .name("Вибір деревини")
                .required(true)
                .options(woodOptions)
                .build());

        return groups;
    }

    // ============================================
    // 🎯 Utility Methods
    // ============================================

    public static void resetCounter() {
        counter.set(1);
    }

    public static int getCurrentCounter() {
        return counter.get();
    }
}