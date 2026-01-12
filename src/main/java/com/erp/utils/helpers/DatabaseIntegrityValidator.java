package com.erp.utils.helpers;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import io.qameta.allure.Allure;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class DatabaseIntegrityValidator {

    /**
     * Отримує кількість записів у базі даних, що відповідають певному фільтру.
     * Використовується у фазі Arrange перед виконанням дії.
     */
    public static <RES> long getRecordCount(ApiExecutor apiExecutor,
                                            ApiEndpointDefinition endpoint,
                                            UserRole userRole,
                                            Class<RES> responseClass,
                                            Predicate<RES> filter) {
        return Allure.step("Отримання поточної кількості записів для перевірки цілісності", () -> {
            Response response = apiExecutor.execute(endpoint, userRole);
            List<RES> list = response.jsonPath().getList("", responseClass);

            if (list == null) return 0L;

            return list.stream()
                    .filter(filter)
                    .count();
        });
    }

    /**
     * Верифікує, що кількість записів у базі не змінилася.
     * Використовується у фазі Assert для перевірки відсутності "фантомних" записів.
     */
    public static <RES> void assertDatabaseCountUnchanged(ApiExecutor apiExecutor,
                                                          ApiEndpointDefinition endpoint,
                                                          UserRole userRole,
                                                          long expectedCount,
                                                          Class<RES> responseClass,
                                                          Predicate<RES> filter) {
        Allure.step("INTEGRITY CHECK: Верифікація незмінності кількості записів у БД", () -> {
            long actualCount = getRecordCount(apiExecutor, endpoint, userRole, responseClass, filter);

            if (actualCount != expectedCount) {
                // Якщо цілісність порушена, додаємо деталі у звіт Allure
                Allure.addAttachment("Integrity Failure Detail",
                        String.format("Expected count: %d, Actual count: %d. Possible 'phantom' record created!",
                                expectedCount, actualCount));
            }

            assertThat(actualCount)
                    .as("Цілісність бази даних порушена! Кількість записів змінилася після помилкового запиту.")
                    .isEqualTo(expectedCount);
        });
    }
}