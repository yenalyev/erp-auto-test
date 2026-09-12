package com.erp.data;

import com.erp.enums.LocationProfile;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.StorageRelation;
import com.erp.enums.UnitType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Loads reusable location templates and their parent location pools. */
public final class LocationProfileCatalog {

    static final String RESOURCE_NAME = "location-profiles.yml";
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static volatile Catalog cachedCatalog;

    private LocationProfileCatalog() {
    }

    public static Definition definition(LocationProfile profile) {
        Definition definition = catalog().locationProfiles().get(profile);
        if (definition == null) {
            throw new IllegalStateException("Location profile is absent from " + RESOURCE_NAME + ": " + profile);
        }
        return definition;
    }

    public static ParentPool parentPool(String poolName) {
        ParentPool pool = catalog().parentPools().get(poolName);
        if (pool == null) {
            throw new IllegalStateException("Parent pool is absent from " + RESOURCE_NAME + ": " + poolName);
        }
        return pool;
    }

    public static Catalog catalog() {
        Catalog current = cachedCatalog;
        if (current != null) {
            return current;
        }
        synchronized (LocationProfileCatalog.class) {
            if (cachedCatalog == null) {
                cachedCatalog = loadFromClasspath();
            }
            return cachedCatalog;
        }
    }

    private static Catalog loadFromClasspath() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream input = loader.getResourceAsStream(RESOURCE_NAME)) {
            if (input == null) {
                throw new IllegalStateException("Location profile dictionary not found on classpath: " + RESOURCE_NAME);
            }
            CatalogFile file = YAML_MAPPER.readValue(input, CatalogFile.class);
            return validate(file);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read location profile dictionary: " + RESOURCE_NAME, e);
        }
    }

    private static Catalog validate(CatalogFile file) {
        if (file == null || file.locationProfiles() == null || file.parentPools() == null) {
            throw new IllegalStateException("locationProfiles and parentPools are required in " + RESOURCE_NAME);
        }

        EnumMap<LocationProfile, Definition> definitions = new EnumMap<>(LocationProfile.class);
        for (LocationProfile profile : LocationProfile.values()) {
            Definition definition = file.locationProfiles().get(profile);
            if (definition == null) {
                throw new IllegalStateException("Missing location profile " + profile + " in " + RESOURCE_NAME);
            }
            if (definition.unitType() == null || definition.relation() == null
                    || definition.accessMode() == null) {
                throw new IllegalStateException("Location profile " + profile + " has incomplete location fields");
            }
            boolean hasPool = definition.parentPool() != null && !definition.parentPool().isBlank();
            boolean hasParentProfile = definition.parentProfile() != null;
            if (hasPool == hasParentProfile) {
                throw new IllegalStateException("Location profile " + profile
                        + " must declare exactly one of parentPool or parentProfile");
            }
            if (definition.namePrefix() == null || definition.namePrefix().isBlank()) {
                throw new IllegalStateException("Location profile " + profile + " has no namePrefix");
            }
            definitions.put(profile, definition);
        }

        Map<String, ParentPool> pools = file.parentPools().entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> validatePool(entry.getKey(), entry.getValue())));
        definitions.forEach((profile, definition) -> {
            if (definition.parentPool() != null && !pools.containsKey(definition.parentPool())) {
                throw new IllegalStateException("Location profile " + profile
                        + " references missing parent pool " + definition.parentPool());
            }
            if (definition.parentProfile() == profile) {
                throw new IllegalStateException("Location profile " + profile + " cannot be its own parent");
            }
        });

        return new Catalog(Collections.unmodifiableMap(definitions), pools);
    }

    private static ParentPool validatePool(String name, ParentPool pool) {
        if (name == null || name.isBlank() || pool == null
                || pool.candidates() == null || pool.candidates().isEmpty()) {
            throw new IllegalStateException("Parent pool must have a name and candidates: " + name);
        }
        if (pool.candidates().stream().distinct().count() != pool.candidates().size()) {
            throw new IllegalStateException("Parent pool contains duplicate candidates: " + name);
        }
        if (pool.candidates().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalStateException("Parent pool contains invalid location IDs: " + name);
        }
        return new ParentPool(List.copyOf(pool.candidates()));
    }

    private record CatalogFile(
            Map<LocationProfile, Definition> locationProfiles,
            Map<String, ParentPool> parentPools) {
    }

    public record Catalog(
            Map<LocationProfile, Definition> locationProfiles,
            Map<String, ParentPool> parentPools) {
    }

    /** milUnitType is intentionally absent: profiles only set fields declared here. */
    public record Definition(
            UnitType unitType,
            StorageRelation relation,
            StorageAccessMode accessMode,
            String parentPool,
            LocationProfile parentProfile,
            String namePrefix) {
    }

    public record ParentPool(List<Long> candidates) {
    }
}
