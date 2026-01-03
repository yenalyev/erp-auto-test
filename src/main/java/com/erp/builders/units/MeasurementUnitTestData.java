package com.erp.builders.units;



import com.erp.builders.common.FakerProvider;
import com.erp.builders.common.TestDataBuilder;
import com.erp.models.response.MeasurementUnitResponse;
import com.github.javafaker.Faker;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test data builder for MeasurementUnit entities
 */

public class MeasurementUnitTestData implements TestDataBuilder<MeasurementUnitResponse, MeasurementUnitResponse> {

    private static final Faker faker = FakerProvider.ukrainian();
    private static final AtomicInteger counter = new AtomicInteger(1);

    // PREDEFINED UNITS
    /**
     * Pieces (штуки)
     */
    public static MeasurementUnitResponse pieces() {
        return MeasurementUnitResponse.builder()
                .name("штуки")
                .shortName("шт")
                .build();
    }

    /**
     * kilograms (kg)
     */
    public static MeasurementUnitResponse kilograms() {
        return MeasurementUnitResponse.builder()
                .name("кілограми")
                .shortName("кг")
                .build();
    }

    // PREDEFINED UNITS WITH ID - для використання з існуючими БД записами

    /**
     * Pieces з конкретним ID (для reference на існуючий запис)
     */
    public static MeasurementUnitResponse piecesWithId(Long id) {
        return MeasurementUnitResponse.builder()
                .id(id)
                .name("штуки")
                .shortName("шт")
                .build();
    }


    // ═══════════════════════════════════════════════════════════════
    // RANDOM GENERATION - випадкові дані
    // ═══════════════════════════════════════════════════════════════

    /**
     * Випадкова одиниця виміру зі списку стандартних
     */
    public MeasurementUnitResponse random() {
        return faker.options().option(
                pieces(),
                kilograms()
        );
    }

    /**
     * Фіксована одиниця виміру для детермінованих тестів
     */
    public MeasurementUnitResponse fixed() {
        int id = counter.getAndIncrement();
        return MeasurementUnitResponse.builder()
                .name("Одиниця " + id)
                .shortName("од" + id)
                .build();
    }

    @Override
    public Class getResponseClass() {
        return null;
    }

    /**
     * Кастомна одиниця виміру
     */
    public static MeasurementUnitResponse custom(String name, String shortName) {
        return MeasurementUnitResponse.builder()
                .name(name)
                .shortName(shortName)
                .build();
    }


    // INVALID DATA - для негативних тестів

    /**
     * Порожнє ім'я
     */
    public static MeasurementUnitResponse emptyName() {
        return MeasurementUnitResponse.builder()
                .name("")
                .shortName("шт")
                .build();
    }

    /**
     * Null ім'я
     */
    public static MeasurementUnitResponse nullName() {
        return MeasurementUnitResponse.builder()
                .name(null)
                .shortName("шт")
                .build();
    }

    /**
     * Порожнє коротке ім'я
     */
    public static MeasurementUnitResponse emptyShortName() {
        return MeasurementUnitResponse.builder()
                .name("штуки")
                .shortName("")
                .build();
    }

    /**
     * Занадто довге ім'я
     */
    public static MeasurementUnitResponse tooLongName() {
        return MeasurementUnitResponse.builder()
                .name("A".repeat(256))
                .shortName("шт")
                .build();
    }


    // COUNTER MANAGEMENT

    public static void resetCounter() {
        counter.set(1);
    }

    public static int getCounterValue() {
        return counter.get();
    }
}