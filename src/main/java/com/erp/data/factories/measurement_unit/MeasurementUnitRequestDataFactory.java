package com.erp.data.factories.measurement_unit;

import com.erp.data.FakerProvider;
import com.erp.models.request.MeasurementUnitRequest;
import com.erp.models.response.MeasurementUnitResponse;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MeasurementUnitRequestDataFactory {

    // Список реальних одиниць для стабільності тестів
    private static final List<String[]> REAL_UNITS = Arrays.asList(
            new String[]{"kilograms", "kg"},
            new String[]{"grams", "g"},
            new String[]{"liters", "l"},
            new String[]{"milliliters", "ml"},
            new String[]{"pieces", "pcs"},
            new String[]{"meters", "m"},
            new String[]{"centimeters", "cm"},
            new String[]{"boxes", "box"},
            new String[]{"packs", "pack"},
            new String[]{"tons", "t"}
    );

    /**
     * 🔥 Повертає список реквестів для створення одиниць, яких ще немає в базі.
     * @param existingUnits список одиниць, які вже завантажені з API
     * @param targetTotal скільки всього одиниць нам потрібно мати для тестів
     */
    public static List<MeasurementUnitRequest> getMissingUnits(List<MeasurementUnitResponse> existingUnits,
                                                               int targetTotal) {
        List<MeasurementUnitRequest> missingRequests = new ArrayList<>();
        int currentCount = (existingUnits == null) ? 0 : (int) existingUnits.stream()
                .filter(u -> u != null && u.getId() != null)
                .count();

        for (String[] candidate : REAL_UNITS) {
            // Якщо ми вже досягли цілі (база + те що плануємо створити), зупиняємось
            if (currentCount + missingRequests.size() >= targetTotal) break;

            String name = candidate[0];
            String shortName = candidate[1];

            // Перевірка на дублікат за ім'ям або скороченням
            boolean alreadyExists = existingUnits != null && existingUnits.stream()
                    .filter(u -> u != null && u.getId() != null)
                    .anyMatch(u -> name.equalsIgnoreCase(u.getName())
                            || shortName.equalsIgnoreCase(u.getShortName()));

            if (!alreadyExists) {
                missingRequests.add(create(name, shortName).build());
            }
        }
        return missingRequests;
    }

    public static MeasurementUnitRequest.MeasurementUnitRequestBuilder createRandom() {
        return MeasurementUnitRequest.builder()
                .name(FakerProvider.ukrainian().commerce().material() + " : " + System.currentTimeMillis())
                .shortName(FakerProvider.ukrainian().commerce().color() + " : " + System.currentTimeMillis());
    }

    public static MeasurementUnitRequest.MeasurementUnitRequestBuilder create(String name,
                                                                                String shortName) {
        return MeasurementUnitRequest.builder()
                .name(name)
                .shortName(shortName);
    }

    /**
     * 🔥 Створює реквест на основі існуючої відповіді.
     * Це дозволяє взяти існуючий об'єкт і змінити в ньому лише одне поле.
     */
    public static MeasurementUnitRequest.MeasurementUnitRequestBuilder fromExisting(@NonNull MeasurementUnitResponse unitResponse) {
        return MeasurementUnitRequest.builder()
                .name(unitResponse.getName())
                .shortName(unitResponse.getShortName());
    }
}
