package com.erp.utils.helpers;

import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@UtilityClass
public class HashtagTestData {

    /**
     * Generates a unique tag with hyphens, compatible with tk-ui {@code #[\p{L}\p{N}_-]+}
     * and backend {@code #[\S]+}.
     */
    public static String uniqueTag(String prefix) {
        String safePrefix = sanitizeTagPart(prefix == null || prefix.isBlank() ? "tag" : prefix);
        return "#auto-" + safePrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String sanitizeTagPart(String value) {
        return value.replaceAll("[^\\p{L}\\p{N}_-]", "-").replaceAll("-+", "-");
    }

    public static String notesWithTags(String... tags) {
        if (tags == null || tags.length == 0) {
            return "";
        }
        List<String> present = Arrays.stream(tags)
                .filter(Objects::nonNull)
                .filter(tag -> !tag.isBlank())
                .toList();
        return String.join(" ", present);
    }
}