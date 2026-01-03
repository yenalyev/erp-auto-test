package com.erp.builders.common;

import com.github.javafaker.Faker;
import lombok.experimental.UtilityClass;


import java.util.Locale;

/**
 * Centralized Faker provider for all test data builders
 * Provides Ukrainian and English Faker instances + common helper methods
 *
 * Usage:
 * - FakerProvider.ukrainian() - for Ukrainian realistic data (names, addresses, etc.)
 * - FakerProvider.english() - for technical data (usernames, SKUs, emails)
 * - FakerProvider.securePassword() - generate secure password
 * - FakerProvider.uniqueUsername() - generate unique username
 */
@UtilityClass
public class FakerProvider {

    // ═══════════════════════════════════════════════════════════════
    // FAKER INSTANCES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Ukrainian Faker for realistic Ukrainian data
     * Use for: names, addresses, cities, product names (in Ukrainian)
     */
    private static final Faker UKRAINIAN = new Faker(new Locale("uk"));

    /**
     * English Faker for technical data
     * Use for: usernames, SKUs, order numbers, emails
     */
    private static final Faker ENGLISH = new Faker(Locale.ENGLISH);

    // ═══════════════════════════════════════════════════════════════
    // FAKER GETTERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get Ukrainian Faker instance
     *
     * @return Faker with Ukrainian locale
     *
     * Example:
     * Faker faker = FakerProvider.ukrainian();
     * String name = faker.name().fullName(); // "Олександр Петренко"
     * String city = faker.address().city(); // "Київ"
     */
    public static Faker ukrainian() {
        return UKRAINIAN;
    }

    /**
     * Get English Faker instance
     *
     * @return Faker with English locale
     *
     * Example:
     * Faker faker = FakerProvider.english();
     * String username = faker.internet().username(); // "john.doe123"
     * String email = faker.internet().emailAddress(); // "test@example.com"
     */
    public static Faker english() {
        return ENGLISH;
    }

    // ═══════════════════════════════════════════════════════════════
    // COMMON HELPERS - PASSWORD
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generate secure password (12-20 chars, mixed case, digits, special chars)
     *
     * @return secure password string
     *
     * Example:
     * String password = FakerProvider.securePassword();
     * // "aB3$xY9@mK1!"
     */
    public static String securePassword() {
        return ENGLISH.internet().password(12, 20, true, true, true);
    }

    /**
     * Generate secure password with custom length
     *
     * @param minLength minimum password length
     * @param maxLength maximum password length
     * @return secure password string
     */
    public static String securePassword(int minLength, int maxLength) {
        return ENGLISH.internet().password(minLength, maxLength, true, true, true);
    }

    /**
     * Generate weak password for negative tests
     *
     * @return weak password (3-5 chars, lowercase only)
     */
    public static String weakPassword() {
        return ENGLISH.internet().password(3, 5, false, false, false);
    }

    /**
     * Generate random email address
     *
     * @return email address
     *
     * Example:
     * String email = FakerProvider.email();
     * // "test123@example.com"
     */
    public static String email() {
        return ENGLISH.internet().emailAddress();
    }


    /**
     * Generate invalid email for negative tests
     *
     * @return invalid email
     */
    public static String invalidEmail() {
        return "invalid-email-" + ENGLISH.number().digits(3);
    }

    // ═══════════════════════════════════════════════════════════════
    // COMMON HELPERS - ORDER NUMBER
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generate order number (ORD-XXXXXXXX format)
     *
     * @return order number
     *
     * Example:
     * String orderNumber = FakerProvider.orderNumber();
     * // "ORD-12345678"
     */
    public static String orderNumber() {
        return "ORD-" + ENGLISH.number().digits(8);
    }

    /**
     * Generate order number with custom prefix
     *
     * @param prefix order number prefix
     * @return order number
     *
     * Example:
     * String orderNumber = FakerProvider.orderNumber("INV");
     * // "INV-12345678"
     */
    public static String orderNumber(String prefix) {
        return prefix + "-" + ENGLISH.number().digits(8);
    }


    // ═══════════════════════════════════════════════════════════════
    // COMMON HELPERS - PHONE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generate Ukrainian phone number
     *
     * @return phone number in Ukrainian format
     *
     * Example:
     * String phone = FakerProvider.ukrainianPhone();
     * // "+380501234567"
     */
    public static String ukrainianPhone() {
        return "+380" + UKRAINIAN.number().digits(9);
    }

    /**
     * Generate international phone number
     *
     * @return phone number
     */
    public static String phone() {
        return ENGLISH.phoneNumber().phoneNumber();
    }

    // ═══════════════════════════════════════════════════════════════
    // COMMON HELPERS - AMOUNT/PRICE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generate random price (double, 2 decimal places)
     *
     * @param min minimum price
     * @param max maximum price
     * @return price as double
     *
     * Example:
     * Double price = FakerProvider.price(10.0, 1000.0);
     * // 345.67
     */
    public static Double price(double min, double max) {
        return ENGLISH.number().randomDouble(2, (long) min, (long) max);
    }

    /**
     * Generate small price (0.01 - 100.00)
     *
     * @return small price
     */
    public static Double smallPrice() {
        return price(0.01, 100.0);
    }

    /**
     * Generate medium price (100.00 - 10000.00)
     *
     * @return medium price
     */
    public static Double mediumPrice() {
        return price(100.0, 10000.0);
    }

    /**
     * Generate large price (10000.00 - 1000000.00)
     *
     * @return large price
     */
    public static Double largePrice() {
        return price(10000.0, 1000000.0);
    }

    // ═══════════════════════════════════════════════════════════════
    // COMMON HELPERS - DATE/TIME
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generate random date in the past
     *
     * @param daysBack how many days back
     * @return date string
     */
    public static String pastDate(int daysBack) {
        return ENGLISH.date().past(daysBack, java.util.concurrent.TimeUnit.DAYS).toString();
    }

    /**
     * Generate random date in the future
     *
     * @param daysForward how many days forward
     * @return date string
     */
    public static String futureDate(int daysForward) {
        return ENGLISH.date().future(daysForward, java.util.concurrent.TimeUnit.DAYS).toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // COMMON HELPERS - NUMBERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generate random positive integer
     *
     * @param min minimum value
     * @param max maximum value
     * @return random integer
     */
    public static Integer randomInt(int min, int max) {
        return ENGLISH.number().numberBetween(min, max);
    }

    /**
     * Generate random positive long
     *
     * @param min minimum value
     * @param max maximum value
     * @return random long
     */
    public static Long randomLong(long min, long max) {
        return ENGLISH.number().numberBetween(min, max);
    }

    /**
     * Generate N random digits as string
     *
     * @param count number of digits
     * @return digits as string
     */
    public static String digits(int count) {
        return ENGLISH.number().digits(count);
    }

    // ═══════════════════════════════════════════════════════════════
    // COMMON HELPERS - TEXT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generate random sentence
     *
     * @return random sentence
     */
    public static String sentence() {
        return ENGLISH.lorem().sentence();
    }

    /**
     * Generate random paragraph
     *
     * @return random paragraph
     */
    public static String paragraph() {
        return ENGLISH.lorem().paragraph();
    }

    /**
     * Generate random text of specific length
     *
     * @param length text length
     * @return random text
     */
    public static String text(int length) {
        return ENGLISH.lorem().characters(length);
    }

    // ═══════════════════════════════════════════════════════════════
    // UKRAINIAN SPECIFIC HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generate Ukrainian first name
     *
     * @return Ukrainian first name
     *
     * Example:
     * String name = FakerProvider.ukrainianFirstName();
     * // "Олександр"
     */
    public static String ukrainianFirstName() {
        return UKRAINIAN.name().firstName();
    }

    /**
     * Generate Ukrainian last name
     *
     * @return Ukrainian last name
     *
     * Example:
     * String lastName = FakerProvider.ukrainianLastName();
     * // "Петренко"
     */
    public static String ukrainianLastName() {
        return UKRAINIAN.name().lastName();
    }

    /**
     * Generate Ukrainian full name
     *
     * @return Ukrainian full name
     *
     * Example:
     * String fullName = FakerProvider.ukrainianFullName();
     * // "Олександр Петренко"
     */
    public static String ukrainianFullName() {
        return UKRAINIAN.name().fullName();
    }

    /**
     * Generate Ukrainian city
     *
     * @return Ukrainian city name
     *
     * Example:
     * String city = FakerProvider.ukrainianCity();
     * // "Київ"
     */
    public static String ukrainianCity() {
        return UKRAINIAN.address().city();
    }

    /**
     * Generate Ukrainian address
     *
     * @return Ukrainian address
     */
    public static String ukrainianAddress() {
        return UKRAINIAN.address().fullAddress();
    }

    /**
     * Generate Ukrainian street name
     *
     * @return Ukrainian street name
     */
    public static String ukrainianStreet() {
        return UKRAINIAN.address().streetName();
    }
}