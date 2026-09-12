package com.erp.data;

import com.erp.enums.BusinessRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Loads the business role to Keycloak realm-role mapping used for test actors. */
public final class BusinessRoleCatalog {

    static final String RESOURCE_NAME = "business-roles.yml";
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
            List<String> keycloakRoles = definition.keycloakRoles();
            if (keycloakRoles == null || keycloakRoles.isEmpty()) {
                throw new IllegalStateException("Business role " + role + " has no Keycloak roles");
            }
            if (keycloakRoles.stream().anyMatch(name -> name == null || name.isBlank())) {
                throw new IllegalStateException("Business role " + role + " contains a blank Keycloak role");
            }
            Set<String> unique = new HashSet<>(keycloakRoles);
            if (unique.size() != keycloakRoles.size()) {
                throw new IllegalStateException("Business role " + role + " contains duplicate Keycloak roles");
            }
            validated.put(role, new Definition(List.copyOf(keycloakRoles)));
        }
        return Collections.unmodifiableMap(validated);
    }

    private record CatalogFile(Map<BusinessRole, Definition> businessRoles) {
    }

    public record Definition(List<String> keycloakRoles) {
    }
}
