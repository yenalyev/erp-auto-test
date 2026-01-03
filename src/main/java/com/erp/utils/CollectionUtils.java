package com.erp.utils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 🔧 Utility methods for working with Collections in tests
 * <p>
 * Provides helper methods for:
 * - Random selection from lists
 * - Shuffling and sampling
 * - List manipulation
 * - Collection validation
 */
public class CollectionUtils {

    private CollectionUtils() {
        throw new IllegalStateException("Utility class - cannot be instantiated");
    }

    // ============================================
    // 🎲 Random Selection Methods
    // ============================================

    /**
     * Get random subset of elements from list without duplicates
     * <p>
     * Uses Collections.shuffle() for randomization
     *
     * @param list Source list
     * @param count Number of elements to select
     * @param <T> Type of elements
     * @return Random subset of elements (new list)
     * @throws IllegalArgumentException if count > list.size()
     */
    public static <T> List<T> getRandomSubList(List<T> list, int count) {
        validateSubListParams(list, count);

        // Create a copy to avoid modifying original list
        List<T> shuffled = new ArrayList<>(list);
        Collections.shuffle(shuffled);

        return new ArrayList<>(shuffled.subList(0, count));
    }

    /**
     * Get random element from list
     *
     * @param list Source list
     * @param <T> Type of elements
     * @return Random element
     * @throws IllegalArgumentException if list is null or empty
     */
    public static <T> T getRandomElement(List<T> list) {
        validateNotEmpty(list, "Cannot get random element from empty list");

        Random random = new Random();
        return list.get(random.nextInt(list.size()));
    }

    /**
     * Get multiple random elements (may contain duplicates)
     * <p>
     * Unlike getRandomSubList, this method allows getting more elements than exist in list
     * by allowing duplicates
     *
     * @param list Source list
     * @param count Number of elements to get
     * @param <T> Type of elements
     * @return List with random elements (may contain duplicates)
     */
    public static <T> List<T> getRandomElements(List<T> list, int count) {
        validateNotEmpty(list, "Cannot get random elements from empty list");
        if (count < 0) {
            throw new IllegalArgumentException("Count cannot be negative");
        }

        Random random = new Random();
        return random.ints(count, 0, list.size())
                .mapToObj(list::get)
                .collect(Collectors.toList());
    }

    /**
     * Get random subset with size between min and max
     *
     * @param list Source list
     * @param minCount Minimum number of elements
     * @param maxCount Maximum number of elements
     * @param <T> Type of elements
     * @return Random subset
     */
    public static <T> List<T> getRandomSubList(List<T> list, int minCount, int maxCount) {
        validateSubListParams(list, maxCount);
        if (minCount < 0 || minCount > maxCount) {
            throw new IllegalArgumentException(
                    String.format("Invalid range: min=%d, max=%d", minCount, maxCount)
            );
        }

        Random random = new Random();
        int count = random.nextInt(maxCount - minCount + 1) + minCount;
        return getRandomSubList(list, count);
    }

    // ============================================
    // 🔀 Shuffling Methods
    // ============================================

    /**
     * Shuffle list and return new shuffled copy
     * <p>
     * Original list remains unchanged
     *
     * @param list Source list
     * @param <T> Type of elements
     * @return New shuffled list
     */
    public static <T> List<T> shuffle(List<T> list) {
        List<T> shuffled = new ArrayList<>(list);
        Collections.shuffle(shuffled);
        return shuffled;
    }

    /**
     * Shuffle list in-place
     *
     * @param list List to shuffle (will be modified)
     * @param <T> Type of elements
     */
    public static <T> void shuffleInPlace(List<T> list) {
        Collections.shuffle(list);
    }

    // ============================================
    // ✂️ List Manipulation Methods
    // ============================================

    /**
     * Split list into chunks of specified size
     *
     * @param list Source list
     * @param chunkSize Size of each chunk
     * @param <T> Type of elements
     * @return List of chunks
     */
    public static <T> List<List<T>> partition(List<T> list, int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive");
        }

        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            partitions.add(new ArrayList<>(
                    list.subList(i, Math.min(i + chunkSize, list.size()))
            ));
        }
        return partitions;
    }

    /**
     * Get first N elements from list
     *
     * @param list Source list
     * @param count Number of elements to take
     * @param <T> Type of elements
     * @return Sublist with first N elements
     */
    public static <T> List<T> takeFirst(List<T> list, int count) {
        validateSubListParams(list, count);
        return new ArrayList<>(list.subList(0, count));
    }

    /**
     * Get last N elements from list
     *
     * @param list Source list
     * @param count Number of elements to take
     * @param <T> Type of elements
     * @return Sublist with last N elements
     */
    public static <T> List<T> takeLast(List<T> list, int count) {
        validateSubListParams(list, count);
        return new ArrayList<>(list.subList(list.size() - count, list.size()));
    }

    // ============================================
    // 🔍 Filtering and Search Methods
    // ============================================

    /**
     * Find elements by IDs
     *
     * @param list Source list
     * @param ids IDs to find
     * @param idExtractor Function to extract ID from element
     * @param <T> Type of elements
     * @param <ID> Type of ID
     * @return List of found elements
     */
    public static <T, ID> List<T> findByIds(List<T> list, List<ID> ids,
                                            java.util.function.Function<T, ID> idExtractor) {
        Set<ID> idSet = new HashSet<>(ids);
        return list.stream()
                .filter(item -> idSet.contains(idExtractor.apply(item)))
                .collect(Collectors.toList());
    }

    // ============================================
    // ✅ Validation Methods
    // ============================================

    /**
     * Check if list is null or empty
     */
    public static <T> boolean isNullOrEmpty(List<T> list) {
        return list == null || list.isEmpty();
    }

    /**
     * Check if list is not null and not empty
     */
    public static <T> boolean isNotEmpty(List<T> list) {
        return list != null && !list.isEmpty();
    }

    /**
     * Ensure list is not null or empty
     *
     * @throws IllegalArgumentException if list is null or empty
     */
    public static <T> void validateNotEmpty(List<T> list, String message) {
        if (isNullOrEmpty(list)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Validate parameters for sublist operations
     */
    private static <T> void validateSubListParams(List<T> list, int count) {
        validateNotEmpty(list, "Cannot get sublist from empty list");
        if (count < 0) {
            throw new IllegalArgumentException("Count cannot be negative");
        }
        if (count > list.size()) {
            throw new IllegalArgumentException(
                    String.format("Cannot select %d elements from list of size %d",
                            count, list.size())
            );
        }
    }

    // ============================================
    // 📊 Statistical Methods
    // ============================================

    /**
     * Get random percentage of elements
     *
     * @param list Source list
     * @param percentage Percentage (0.0 to 1.0)
     * @param <T> Type of elements
     * @return Random subset
     */
    public static <T> List<T> getRandomPercentage(List<T> list, double percentage) {
        validateNotEmpty(list, "Cannot get percentage from empty list");
        if (percentage < 0.0 || percentage > 1.0) {
            throw new IllegalArgumentException("Percentage must be between 0.0 and 1.0");
        }

        int count = (int) Math.ceil(list.size() * percentage);
        return getRandomSubList(list, count);
    }

    /**
     * Get approximately N% of random elements
     * <p>
     * Example: 50% of 10 elements = ~5 elements (could be 4-6)
     *
     * @param list Source list
     * @param approximatePercentage Approximate percentage (0.0 to 1.0)
     * @param variance How much variance to allow (0.0 to 1.0)
     * @param <T> Type of elements
     * @return Random subset
     */
    public static <T> List<T> getApproximatePercentage(List<T> list,
                                                       double approximatePercentage,
                                                       double variance) {
        validateNotEmpty(list, "Cannot get percentage from empty list");

        double minPercentage = Math.max(0.0, approximatePercentage - variance);
        double maxPercentage = Math.min(1.0, approximatePercentage + variance);

        int minCount = (int) Math.ceil(list.size() * minPercentage);
        int maxCount = (int) Math.ceil(list.size() * maxPercentage);

        return getRandomSubList(list, minCount, maxCount);
    }
}
