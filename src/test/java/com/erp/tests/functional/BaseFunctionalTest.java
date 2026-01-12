package com.erp.tests.functional;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.tests.BaseTest;
import com.erp.utils.helpers.AllureHelper;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import io.qameta.allure.Allure;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Slf4j
public abstract class BaseFunctionalTest extends BaseTest {

    @BeforeClass(alwaysRun = true)
    public void setupFunctionalContext() {
        // Ініціалізуємо базову фікстуру
        //baseFixture = new BaseFixture(testContext, apiExecutor);

        // Гарантуємо наявність базових довідників (Units),
        // які потрібні майже для всіх функціональних модулів
        //baseFixture.fetchSharedUnit(5);
    }

    // Тут можна додати метод для очищення створених даних
    // @AfterMethod(alwaysRun = true)
    // public void cleanup() { ... }

    private void assertFieldsMatch(Object request, Object response) {
        // Отримуємо всі поля з класу Request (наприклад, name, shortName)
        Field[] fields = request.getClass().getDeclaredFields();

        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object expectedValue = field.get(request);

                // Шукаємо поле з такою ж назвою у Response
                Field responseField = response.getClass().getDeclaredField(field.getName());
                responseField.setAccessible(true);
                Object actualValue = responseField.get(response);

                assertThat(actualValue)
                        .as("Поле '" + field.getName() + "' не збігається")
                        .isEqualTo(expectedValue);

            } catch (NoSuchFieldException e) {
                // Якщо в Response немає поля з такою назвою — ігноруємо або логуємо
                log.debug("Поле {} відсутнє в Response класі", field.getName());
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Помилка рефлексії при порівнянні полів", e);
            }
        }
    }

    protected <REQ, RES> void verifyCreatedEntity(Response response, REQ request,
                                                  ApiEndpointDefinition getListEndpoint,
                                                  Class<RES> responseClass) {

        // 1. Десеріалізуємо відповідь
        RES createdEntity = response.as(responseClass);

        Allure.step("STEP 3: Комплексна верифікація створеної сутності", () -> {

            Allure.step("Перевірка полів Response Body (через Reflection)", () -> {
                // Перевіряємо ID
                try {
                    Object id = responseClass.getMethod("getId").invoke(createdEntity);
                    assertThat(id).as("ID сутності не повинен бути порожнім").isNotNull();
                } catch (Exception e) {
                    log.warn("Не вдалося знайти метод getId у класі {}", responseClass.getSimpleName());
                }

                // Порівнюємо всі поля, що прийшли з Request
                assertFieldsMatch(request, createdEntity);
            });

            Allure.step("Перевірка фізичного збереження в БД (Persistence Check)", () -> {
                Response getResponse = apiExecutor.execute(getListEndpoint, UserRole.ADMIN);
                AllureHelper.attachResponseDetails(getResponse);

                List<RES> allUnits = getResponse.jsonPath().getList("", responseClass);

                // Знаходимо об'єкт за ID (використовуємо рефлексію для отримання ID)
                RES persistedEntity = allUnits.stream()
                        .filter(u -> {
                            try {
                                Object createdId = responseClass.getMethod("getId").invoke(createdEntity);
                                Object currentId = responseClass.getMethod("getId").invoke(u);
                                return createdId.equals(currentId);
                            } catch (Exception e) { return false; }
                        })
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Об'єкт не знайдений у списку після створення"));

                // Перевіряємо, що дані в БД відповідають запиту
                assertFieldsMatch(request, persistedEntity);
            });
        });
    }


    protected <RES> long getDbCount(ApiEndpointDefinition endpoint,
                                    UserRole userRole,
                                    Class<RES> clazz,
                                    Predicate<RES> filter) {
        return DatabaseIntegrityValidator.getRecordCount(apiExecutor, endpoint, userRole, clazz, filter);
    }

    protected <RES> void assertDbUnchanged(ApiEndpointDefinition endpoint,
                                           UserRole userRole,
                                           long countBefore,
                                           Class<RES> clazz,
                                           Predicate<RES> filter) {
        DatabaseIntegrityValidator.assertDatabaseCountUnchanged(apiExecutor, endpoint, userRole, countBefore, clazz, filter);
    }
}