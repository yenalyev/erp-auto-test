package com.erp.tests.functional;

import com.erp.tests.BaseTest;
import org.testng.annotations.BeforeClass;

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
}