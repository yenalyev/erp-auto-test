package com.erp.utils.data;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.index.qual.NonNegative;

import java.util.Random;
import java.util.UUID;

@Slf4j
@UtilityClass
public class DataUtils {
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private final Random random = new Random();
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


    /**
     * Генерує випадковий рядок довжини ${totalLength} та містить унікальний суфікс: таймстемп + ID потоку.
     * Формат: "AJLJKJ_1712345678_T12"
     */
    public String generateWithUniqueSuffix(int totalLength) {
        // 1. Формуємо суфікс
        String suffix = String.format("_%d_T%d",
                System.currentTimeMillis(),
                Thread.currentThread().getId());

        int suffixLen = suffix.length();

        // 2. Обробка випадку, коли ліміт менший за суфікс
        if (totalLength < suffixLen) {
            log.warn("Requested length {} is shorter than suffix length {}. Truncating suffix.",
                    totalLength, suffixLen);
            // Залишаємо останні totalLength символів суфікса
            return suffix.substring(suffixLen - totalLength);
        }

        // 3. Генеруємо випадкову частину (префікс)
        int prefixLen = totalLength - suffixLen;
        StringBuilder prefix = new StringBuilder(prefixLen);
        for (int i = 0; i < prefixLen; i++) {
            prefix.append(CHARS.charAt(java.util.concurrent.ThreadLocalRandom.current().nextInt(CHARS.length())));
        }

        return prefix.append(suffix).toString();
    }
}
