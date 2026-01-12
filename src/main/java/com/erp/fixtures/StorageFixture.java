package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.request.StorageRequest;
import com.erp.models.response.StorageResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class StorageFixture extends BaseFixture {

    public StorageFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    /**
     * Готує базове середовище для тестів складів.
     * Створює один основний склад для загального використання.
     */
    @Step("FIXTURE: Підготовка базового складу")
    public void prepareContext() {
        if (testContext.get(ContextKey.DYNAMIC_STORAGE) == null) {
            setupSharedStorage();
        }
    }

    /**
     * Створює та зберігає в контекст один склад.
     */
    @Step("FIXTURE: Створення спільного складу")
    public StorageResponse setupSharedStorage() {
        StorageRequest request = StorageDataFactory.randomStorage().build();

        StorageResponse response = apiExecutor.execute(ApiEndpointDefinition.STORAGE_POST_CREATE, UserRole.ADMIN, request)
                .as(StorageResponse.class);

        List<StorageResponse> storageResponseList = List.of(response);

        testContext.set(ContextKey.DYNAMIC_STORAGE, response);
        testContext.set(ContextKey.SHARED_STORAGE_LIST, storageResponseList);
        log.info("Shared Storage created: {} (ID: {})", response.getName(), response.getId());
        return response;
    }

    /**
     * Гарантує наявність щонайменше {count} складів у системі.
     * Перевіряє існуючі записи в базі і створює нові лише за потреби.
     * * @param count Бажана кількість складів.
     * @return Повний список складів (існуючі + новостворені).
     */
    @Step("FIXTURE: Забезпечення наявності списку складів (мінімум {count})")
    public List<StorageResponse> setupSharedStorageList(int count) {
        // 1. Отримуємо всі існуючі склади з бази
        Response response = apiExecutor.execute(ApiEndpointDefinition.STORAGE_GET_ALL, UserRole.ADMIN);

        // Ініціалізуємо список, який можна змінювати
        List<StorageResponse> allStorages = new ArrayList<>();
        if (response.statusCode() == 200) {
            List<StorageResponse> existing = response.jsonPath().getList("", StorageResponse.class);
            if (existing != null) {
                allStorages.addAll(existing);
            }
        }

        // 2. Якщо в базі менше складів, ніж нам потрібно — створюємо відсутні
        if (allStorages.size() < count) {
            int currentSize = allStorages.size();
            int needed = count - currentSize;
            log.info("Database has {} storages. Creating {} more to reach {}", currentSize, needed, count);

            for (int i = 0; i < needed; i++) {
                // Використовуємо метод створення одного складу
                allStorages.add(setupSharedStorage());
            }
        }

        // 3. Зберігаємо фінальний список у контекст для використання в тестах
        testContext.set(ContextKey.SHARED_STORAGE_LIST, allStorages);

        // Також оновлюємо DYNAMIC_STORAGE першим доступним елементом для зручності
        if (!allStorages.isEmpty()) {
            testContext.set(ContextKey.DYNAMIC_STORAGE, allStorages.getFirst());
        }

        return allStorages;
    }
}