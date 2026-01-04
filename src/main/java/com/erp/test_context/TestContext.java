package com.erp.test_context;

public interface TestContext {
   // життєвий цикл
    void clear();
    boolean isEmpty();

    // доступ до об'єктів
    <T> void set(ContextKey key, T value);
    <T> T get(ContextKey key);

    // Залишаємо логування
    void logState();
    String toSummary();
}