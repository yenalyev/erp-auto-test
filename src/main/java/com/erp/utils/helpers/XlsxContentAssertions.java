package com.erp.utils.helpers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Lightweight text probe for OOXML .xlsx without Apache POI —
 * scans ZIP entry payloads (sharedStrings / sheets) as UTF-8.
 */
public final class XlsxContentAssertions {

    private XlsxContentAssertions() {
    }

    public static boolean zipContainsText(Path xlsxPath, String needle) {
        if (xlsxPath == null || needle == null || needle.isBlank()) {
            return false;
        }
        String target = needle.toLowerCase(Locale.ROOT);
        try (ZipFile zip = new ZipFile(xlsxPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".xml") && !name.endsWith(".rels")) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                    if (xml.contains(target)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (IOException e) {
            throw new AssertionError("Cannot parse xlsx " + xlsxPath + ": " + e.getMessage(), e);
        }
    }

    public static boolean zipContainsText(byte[] xlsxBytes, String needle) {
        if (xlsxBytes == null) {
            return false;
        }
        try {
            Path tmp = Files.createTempFile("erp-xlsx-", ".xlsx");
            try {
                Files.write(tmp, xlsxBytes);
                return zipContainsText(tmp, needle);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new AssertionError("Cannot write temp xlsx: " + e.getMessage(), e);
        }
    }
}
