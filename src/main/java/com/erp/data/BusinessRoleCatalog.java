package com.erp.data;

import com.erp.enums.BusinessRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Loads additional DB-backed access roles and permissions for business personas. */
public final class BusinessRoleCatalog {

    static final String RESOURCE_NAME = "business-roles.yml";
    public static final String DEFAULT_LOCATION_ROLE_NAME = "Керівник локації";
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static volatile Map<BusinessRole, Definition> cachedDefinitions;

    private BusinessRoleCatalog() {
    }

    public static Definition definition(BusinessRole businessRole) {
        Objects.requireNonNull(businessRole, "businessRole");
        Definition definition = definitions().get(businessRole);
        if (definition == null) {
            throw new IllegalStateException("Business role is absent from " + RESOURCE_NAME + ": " + businessRole);
        }
        return definition;
    }

    public static Map<BusinessRole, Definition> definitions() {
        Map<BusinessRole, Definition> current = cachedDefinitions;
        if (current != null) {
            return current;
        }
        synchronized (BusinessRoleCatalog.class) {
            if (cachedDefinitions == null) {
                cachedDefinitions = loadFromClasspath();
            }
            return cachedDefinitions;
        }
    }

    private static Map<BusinessRole, Definition> loadFromClasspath() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream input = loader.getResourceAsStream(RESOURCE_NAME)) {
            if (input == null) {
                throw new IllegalStateException("Business role dictionary not found on classpath: " + RESOURCE_NAME);
            }
            CatalogFile file = YAML_MAPPER.readValue(input, CatalogFile.class);
            return validate(file);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read business role dictionary: " + RESOURCE_NAME, e);
        }
    }

    private static Map<BusinessRole, Definition> validate(CatalogFile file) {
        if (file == null || file.businessRoles() == null) {
            throw new IllegalStateException("Missing businessRoles section in " + RESOURCE_NAME);
        }

        EnumMap<BusinessRole, Definition> validated = new EnumMap<>(BusinessRole.class);
        for (BusinessRole role : BusinessRole.values()) {
            Definition definition = file.businessRoles().get(role);
            if (definition == null) {
                throw new IllegalStateException("Missing mapping for business role " + role + " in " + RESOURCE_NAME);
            }
            List<String> accessRoles = definition.accessRoles() == null ? List.of() : definition.accessRoles();
            List<String> permissionKeys = definition.permissionKeys() == null ? List.of() : definition.permissionKeys();
            validateValues(role, "access role", accessRoles);
            validateValues(role, "permission key", permissionKeys);
            validated.put(role, new Definition(List.copyOf(accessRoles), List.copyOf(permissionKeys)));
        }
        return Collections.unmodifiableMap(validated);
    }

    private record CatalogFile(Map<BusinessRole, Definition> businessRoles) {
    }

    /** Resolves the complete role set for a regular non-admin actor. */
    public static List<String> effectiveAccessRoles(BusinessRole businessRole) {
        return withDefaultLocationRole(definition(businessRole).accessRoles());
    }

    public static List<String> withDefaultLocationRole(List<String> additionalRoles) {
        Objects.requireNonNull(additionalRoles, "additionalRoles");
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        roles.add(DEFAULT_LOCATION_ROLE_NAME);
        roles.addAll(additionalRoles);
        return List.copyOf(roles);
    }

    private static void validateValues(BusinessRole role, String label, List<String> values) {
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalStateException("Business role " + role + " contains a blank " + label);
        }
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalStateException("Business role " + role + " contains duplicate " + label + "s");
        }
    }

    /** Additional grants applied on top of the fixture's default location-head role. */
    public record Definition(List<String> accessRoles, List<String> permissionKeys) {
    }
}
