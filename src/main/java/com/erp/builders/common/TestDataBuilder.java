package com.erp.builders.common;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Базовий контракт для всіх генераторів тестових даних
 * @param <T> Тип об'єкта (зазвичай DTO), який ми створюємо
 */
public interface TestDataBuilder<T,R> {

    // Створити об'єкт з повністю випадковими даними
    T random();

    // Створити об'єкт з валідними, але передбачуваними даними
    T fixed();

    // Створити список випадкових об'єктів (вже реалізовано для всіх)
    default List<T> randomList(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> random())
                .collect(Collectors.toList());
    }

    // 3. Повертає клас відповіді для автоматичної десеріалізації в BaseDataService
    Class<R> getResponseClass();

    /**
     * Універсальний метод для модифікації об'єкта (Update logic).
     * Дозволяє взяти базовий об'єкт і змінити в ньому будь-які поля через Lambda.
     */
    default T modify(T base, Consumer<T> modifier) {
        modifier.accept(base);
        return base;
    }

}