package com.erp.tests.support;

import com.erp.utils.data.TestPng;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/** Temporary PDF fixtures for multipart upload limit tests. */
public final class UploadLimitTestFiles {
    public static final String EXPECTED_ERROR = "Розмір файлу перевищує допустимий 50MB.";
    public static final long FIVE_MB = 5L * 1024 * 1024;
    public static final long OVER_LIMIT_BYTES = 51L * 1024 * 1024;
    private UploadLimitTestFiles() { }

    public static Path createPdf(String label, long size) throws IOException {
        Path file = Files.createTempFile("tc-upload-50-" + label + "-", ".pdf");
        try (RandomAccessFile output = new RandomAccessFile(file.toFile(), "rw")) {
            write(output, "%PDF-1.4\n");
            long first = output.getFilePointer();
            write(output, "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n");
            long second = output.getFilePointer();
            write(output, "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n");
            long third = output.getFilePointer();
            write(output, "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 100 100]>>endobj\n");
            long xref = output.getFilePointer();
            write(output, "xref\n0 4\n0000000000 65535 f \n");
            for (long offset : new long[]{first, second, third}) {
                write(output, String.format(Locale.ROOT, "%010d 00000 n \n", offset));
            }
            write(output, "trailer<</Root 1 0 R/Size 4>>\nstartxref\n" + xref + "\n%%EOF\n");
            if (size < output.getFilePointer()) {
                throw new IllegalArgumentException("PDF fixture size is too small");
            }
            byte[] spaces = new byte[8192];
            Arrays.fill(spaces, (byte) ' ');
            while (output.getFilePointer() < size) {
                int count = (int) Math.min(spaces.length, size - output.getFilePointer());
                output.write(spaces, 0, count);
            }
        }
        return file;
    }

    private static void write(RandomAccessFile output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    public static Path createOversizedPng() throws IOException {
        return createPng("resource-photo", OVER_LIMIT_BYTES);
    }

    public static Path createPng(String label, long size) throws IOException {
        Path file = Files.createTempFile("tc-upload-50-" + label + "-", ".png");
        try (RandomAccessFile output = new RandomAccessFile(file.toFile(), "rw")) {
            output.write(TestPng.onePixel());
            output.setLength(size);
        }
        return file;
    }
}
