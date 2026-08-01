package com.erp.utils.helpers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts for Playwright {@code Download} payloads.
 *
 * <p>Do not rely on {@code Download.suggestedFilename()} for blob + {@code <a download>} flows
 * with Cyrillic names — Chromium often reports the literal {@code "download"}.
 */
public final class UiDownloadAssertions {

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};
    /** OOXML (docx/xlsx) and classic ZIP local-file header. */
    private static final byte[] ZIP_MAGIC = {'P', 'K', 0x03, 0x04};

    private UiDownloadAssertions() {
    }

    public static void assertNonEmptyOfficeOrPdf(Path path, long sizeBytes, String description) {
        assertThat(path)
                .as("%s: download path", description)
                .isNotNull();
        assertThat(sizeBytes)
                .as("%s: file not empty", description)
                .isGreaterThan(100);
        byte[] header = readHeader(path, 4);
        boolean pdf = startsWith(header, PDF_MAGIC);
        boolean zip = startsWith(header, ZIP_MAGIC);
        assertThat(pdf || zip)
                .as("%s: expected PDF (%%PDF) or OOXML/ZIP (PK) magic, got %s",
                        description, formatBytes(header))
                .isTrue();
    }

    public static void assertNonEmptyXlsx(Path path, long sizeBytes, String description) {
        assertThat(path)
                .as("%s: download path", description)
                .isNotNull();
        assertThat(sizeBytes)
                .as("%s: file not empty", description)
                .isGreaterThan(100);
        byte[] header = readHeader(path, 4);
        assertThat(startsWith(header, ZIP_MAGIC))
                .as("%s: expected XLSX (ZIP PK) magic, got %s", description, formatBytes(header))
                .isTrue();
    }

    private static byte[] readHeader(Path path, int length) {
        try (InputStream in = Files.newInputStream(path)) {
            return in.readNBytes(length);
        } catch (IOException e) {
            throw new AssertionError("Cannot read download header from " + path + ": " + e.getMessage(), e);
        }
    }

    private static boolean startsWith(byte[] actual, byte[] expected) {
        if (actual == null || actual.length < expected.length) {
            return false;
        }
        return Arrays.equals(actual, 0, expected.length, expected, 0, expected.length);
    }

    private static String formatBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            int b = bytes[i] & 0xFF;
            if (b >= 0x20 && b < 0x7F) {
                sb.append((char) b);
            } else {
                sb.append(String.format(Locale.ROOT, "0x%02X", b));
            }
        }
        return sb.append(']').toString();
    }
}
