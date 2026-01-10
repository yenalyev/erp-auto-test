package com.erp.utils.data;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DataUtils {
    /**
     * Генерує унікальний суфікс: таймстемп + ID потоку.
     * Формат: "_1712345678_T12"
     */
    public static String getUniqueSuffix() {
        return String.format("_%d_T%d",
                System.currentTimeMillis(),
                Thread.currentThread().getId());
    }

    /**
     * Робить рядок унікальним, додаючи суфікс
     */
    public static String makeUnique(String base) {
        return base + getUniqueSuffix();
    }
}
