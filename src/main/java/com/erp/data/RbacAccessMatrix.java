package com.erp.data;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.rbac.EndpointAccessRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Data;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static com.erp.enums.UserRole.ANONYMOUS;

/**
 * 🔐 RBAC Access Matrix - YAML Configuration Loader
 * <p>
 * Loads RBAC rules from YAML and generates test data for TestNG DataProvider.
 * All-in-one solution without extra DTO classes.
 */
@Slf4j
@UtilityClass
public class RbacAccessMatrix {

    private static final String POLICY_FILE = "rbac-policy.yml";
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    // Cache для правил
    private static List<EndpointAccessRule> cachedRules;

    /**
     * ✅ Генерує test data для TestNG DataProvider
     */
    public static Object[][] generateTestData(RbacTestContext context) {
        List<Object[]> testCases = new ArrayList<>();

        // Завантажуємо правила з YAML
        List<EndpointAccessRule> rules = loadRules();

        log.info("📋 Generating RBAC test matrix from {} rules", rules.size());

        for (EndpointAccessRule rule : rules) {
            try {
                // Отримуємо endpoint definition
                ApiEndpointDefinition endpoint = rule.getEndpointDefinition();

                // Генеруємо request body якщо потрібно
                if (endpoint.requiresBody() && rule.getBodyType() != null) {
                    Object requestBody = RequestBodyFactory.generate(
                            rule.getBodyType(),
                            context
                    );
                    rule.setRequestBody(requestBody);
                }

                // Встановлюємо path param якщо потрібно
                if (endpoint.hasPathVariables()) {
                    String pathParam = context.getResourceIdForEndpoint(rule.getEndpointName());
                    rule.setPathParam(pathParam);
                }

                // Генеруємо тести для allowed roles
                if (rule.getAllowedRoles() != null) {
                    for (UserRole allowedRole : rule.getAllowedRoles()) {
                        int expectedStatus = endpoint.getHttpMethod().name().equals("POST")
                                ? 201
                                : 200;

                        testCases.add(new Object[]{
                                rule,
                                allowedRole,
                                expectedStatus,
                                "ALLOWED"
                        });
                    }
                }

                // Генеруємо тести для denied roles
                if (rule.getDeniedRoles() != null) {
                    for (UserRole deniedRole : rule.getDeniedRoles()) {
                        testCases.add(new Object[]{
                                rule,
                                deniedRole,
                                deniedRole.equals(ANONYMOUS) ? 401 : 403,
                                "DENIED"
                        });
                    }
                }

            } catch (Exception e) {
                log.error("❌ Failed to generate test cases for rule: {}",
                        rule.getEndpointName(), e);
                throw new RuntimeException(
                        "Failed to generate test cases for: " + rule.getEndpointName(), e
                );
            }
        }

        log.info("✅ Generated {} test cases", testCases.size());

        return testCases.toArray(new Object[0][]);
    }

    /**
     * ✅ Завантажує правила з YAML файлу
     */
    private static List<EndpointAccessRule> loadRules() {
        if (cachedRules != null) {
            log.debug("📦 Returning cached RBAC rules ({} rules)", cachedRules.size());
            return cachedRules;
        }

        log.info("📂 Loading RBAC policy from: {}", POLICY_FILE);

        try (InputStream inputStream = getRbacPolicyInputStream()) {
            // ✅ Парсимо YAML напряму в Map структуру
            RbacPolicyConfig config = YAML_MAPPER.readValue(inputStream, RbacPolicyConfig.class);

            if (config == null || config.rules == null || config.rules.isEmpty()) {
                throw new IllegalStateException(
                        "RBAC policy is empty or invalid. Check file: " + POLICY_FILE
                );
            }

            log.info("✅ Parsed {} rules from YAML", config.rules.size());

            // Конвертуємо в EndpointAccessRule
            cachedRules = convertToAccessRules(config.rules);

            log.info("✅ Successfully loaded {} RBAC rules", cachedRules.size());

            return cachedRules;

        } catch (IOException e) {
            log.error("❌ Failed to load RBAC policy from: {}", POLICY_FILE, e);
            throw new RuntimeException("Failed to load RBAC policy", e);
        }
    }

    /**
     * ✅ Конвертує YAML правила в EndpointAccessRule об'єкти
     */
    private static List<EndpointAccessRule> convertToAccessRules(List<RbacRuleYaml> yamlRules) {
        List<EndpointAccessRule> rules = new ArrayList<>();

        for (int i = 0; i < yamlRules.size(); i++) {
            RbacRuleYaml yaml = yamlRules.get(i);
            int ruleNumber = i + 1;

            try {
                // Валідуємо що endpoint існує
                if (yaml.endpointName == null || yaml.endpointName.trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            String.format("Rule #%d: endpointName is required", ruleNumber)
                    );
                }

                // Перевіряємо що endpoint definition існує
                try {
                    ApiEndpointDefinition.findByName(yaml.endpointName);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            String.format("Rule #%d: Invalid endpoint name '%s'. %s",
                                    ruleNumber, yaml.endpointName, e.getMessage())
                    );
                }

                // Конвертуємо ролі
                Set<UserRole> allowedRoles = convertRoles(yaml.allowedRoles, "allowedRoles", ruleNumber);
                Set<UserRole> deniedRoles = convertRoles(yaml.deniedRoles, "deniedRoles", ruleNumber);

                // Будуємо правило
                EndpointAccessRule rule = EndpointAccessRule.builder()
                        .endpointName(yaml.endpointName)
                        .allowedRoles(allowedRoles)
                        .deniedRoles(deniedRoles)
                        .bodyType(yaml.bodyType)
                        .build();

                rules.add(rule);

            } catch (Exception e) {
                log.error("❌ Failed to convert rule #{}: {}", ruleNumber, yaml.endpointName, e);
                throw new RuntimeException(
                        String.format("Failed to convert rule #%d (%s): %s",
                                ruleNumber, yaml.endpointName, e.getMessage()),
                        e
                );
            }
        }

        return rules;
    }

    /**
     * ✅ Конвертує список імен ролей в Set<UserRole>
     */
    private static Set<UserRole> convertRoles(List<String> roleNames, String fieldName, int ruleNumber) {
        if (roleNames == null || roleNames.isEmpty()) {
            return new HashSet<>();
        }

        Set<UserRole> roles = new HashSet<>();

        for (String roleName : roleNames) {
            try {
                UserRole role = UserRole.valueOf(roleName.trim());
                roles.add(role);

            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        String.format("Rule #%d: Invalid role name '%s' in %s. Valid roles: %s",
                                ruleNumber,
                                roleName,
                                fieldName,
                                Arrays.toString(UserRole.values()))
                );
            }
        }

        return roles;
    }

    /**
     * ✅ Отримує InputStream для YAML файлу
     */
    private static InputStream getRbacPolicyInputStream() throws IOException {
        InputStream stream = RbacAccessMatrix.class.getClassLoader()
                .getResourceAsStream(POLICY_FILE);

        if (stream == null) {
            throw new IOException(
                    "RBAC policy file not found: " + POLICY_FILE + "\n" +
                            "Expected location: src/test/resources/" + POLICY_FILE
            );
        }

        return stream;
    }

    /**
     * ✅ Отримує статистику матриці
     */
    public static String getMatrixStats() {
        List<EndpointAccessRule> rules = loadRules();

        int totalRules = rules.size();
        long uniqueEndpoints = rules.stream()
                .map(EndpointAccessRule::getEndpointName)
                .distinct()
                .count();

        int totalAllowed = rules.stream()
                .mapToInt(rule -> rule.getAllowedRoles() != null ? rule.getAllowedRoles().size() : 0)
                .sum();

        int totalDenied = rules.stream()
                .mapToInt(rule -> rule.getDeniedRoles() != null ? rule.getDeniedRoles().size() : 0)
                .sum();

        StringBuilder stats = new StringBuilder();
        stats.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        stats.append("📊 RBAC Matrix Statistics\n");
        stats.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        stats.append("Total Rules: ").append(totalRules).append("\n");
        stats.append("Total Test Cases: ").append(totalAllowed + totalDenied).append("\n");
        stats.append("  - ALLOWED tests: ").append(totalAllowed).append("\n");
        stats.append("  - DENIED tests: ").append(totalDenied).append("\n");
        stats.append("Unique Endpoints: ").append(uniqueEndpoints).append("\n");
        stats.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        return stats.toString();
    }

    /**
     * ✅ Очищає кеш (для тестування)
     */
    public static void clearCache() {
        cachedRules = null;
        log.debug("🗑️ Cleared RBAC rules cache");
    }

    // ============================================
    // 📋 Inner Classes для YAML mapping
    // ============================================

    /**
     * Root YAML structure
     */
    @Data
    private static class RbacPolicyConfig {
        private List<RbacRuleYaml> rules;
    }

    /**
     * Single RBAC rule from YAML
     */
    @Data
    private static class RbacRuleYaml {
        private String endpointName;
        private List<String> allowedRoles;
        private List<String> deniedRoles;
        private String bodyType;
    }
}