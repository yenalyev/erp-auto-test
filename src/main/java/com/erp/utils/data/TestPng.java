package com.erp.utils.data;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tiny valid PNG for resource/equipment photo upload tests (Thumbnailator rejects truncated files).
 */
public final class TestPng {

    private TestPng() {
    }

    public static byte[] onePixel() {
        try {
            BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) {
                throw new IllegalStateException("JDK has no PNG ImageIO writer");
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Path writeTempFile(String prefix) {
        try {
            Path file = Files.createTempFile(prefix, ".png");
            Files.write(file, onePixel());
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
