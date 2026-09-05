package com.erp.enums;

import com.erp.utils.config.ConfigProvider;

public enum UserRole {
    ADMIN,
    OWNER_1,
    OWNER_2,
    OWNER_3,
    RESOURCE_VIEWER,
    ACCOUNTANT,
    /**
     * Logistics cabinet ({@code Logist-ROLE}: {@code perm_logistic::view}, no {@code relocation::view}).
     * staging/dev: {@code logist}.
     */
    LOGIST,
    /** Dev/staging: argument — Crew-Manager-ROLE, UNIT storage ({@code unit.storage.id}). */
    CREW_MANAGER,
    /** Battalion warehouse keeper — Crew-Read-ROLE ({@code perm_crews-stocks::view}). */
    CREW_READ,
    /** Battalion warehouse keeper — Crew-Write-ROLE (read + crew inventory conduct). */
    CREW_WRITE,
    /**
     * Project production catalog admin ({@code Project-Production-ROLE}) —
     * staging/dev: {@code projectprod}, storage = owner1.
     */
    PROJECT_ADMIN,
    /**
     * Project production manager ({@code Project-Production-ROLE}) —
     * staging/dev: {@code projectprodab}, storage = owner1.
     */
    PROJECT_MANAGER,
    /**
     * Mixed full + view-only locations (CPMA-644):
     * full on owner1 + unit, RO on owner2 + optional ro2.
     */
    LOCATION_MIXED,
    /**
     * Order gathering storage owner (REQ-ORD).
     * staging/dev: {@code order.gathering.*} (e.g. tyolki / storage 10); falls back to owner2.
     */
    ORDER_GATHERER,
    /**
     * Owner of the requester UNIT (підрозділ) + unit-analytics reader.
     * staging/dev: {@code user.unit-analyst.*} (e.g. {@code 3bat}).
     */
    UNIT_ANALYST,
    ANONYMOUS;

    public String getUsername() {
        return switch (this) {
            case ADMIN           -> ConfigProvider.getAdminUsername();
            case OWNER_1         -> ConfigProvider.getOwner1Username();
            case OWNER_2         -> ConfigProvider.getOwner2Username();
            case OWNER_3         -> ConfigProvider.getOwner3Username();
            case RESOURCE_VIEWER -> ConfigProvider.getResourceViewerUsername();
            case ACCOUNTANT      -> ConfigProvider.getAccountantUsername();
            case LOGIST          -> ConfigProvider.getLogistUsername();
            case CREW_MANAGER    -> ConfigProvider.getCrewManagerUsername();
            case CREW_READ       -> ConfigProvider.getCrewReadUsername();
            case CREW_WRITE      -> ConfigProvider.getCrewWriteUsername();
            case PROJECT_ADMIN   -> ConfigProvider.getProjectAdminUsername();
            case PROJECT_MANAGER -> ConfigProvider.getProjectManagerUsername();
            case LOCATION_MIXED  -> ConfigProvider.getLocationMixedUsername();
            case ORDER_GATHERER  -> ConfigProvider.getOrderGatheringUsername();
            case UNIT_ANALYST    -> ConfigProvider.getUnitAnalystUsername();
            case ANONYMOUS       -> "";
        };
    }

    public String getPassword() {
        return switch (this) {
            case ADMIN           -> ConfigProvider.getAdminPassword();
            case OWNER_1         -> ConfigProvider.getOwner1Password();
            case OWNER_2         -> ConfigProvider.getOwner2Password();
            case OWNER_3         -> ConfigProvider.getOwner3Password();
            case RESOURCE_VIEWER -> ConfigProvider.getResourceViewerPassword();
            case ACCOUNTANT      -> ConfigProvider.getAccountantPassword();
            case LOGIST          -> ConfigProvider.getLogistPassword();
            case CREW_MANAGER    -> ConfigProvider.getCrewManagerPassword();
            case CREW_READ       -> ConfigProvider.getCrewReadPassword();
            case CREW_WRITE      -> ConfigProvider.getCrewWritePassword();
            case PROJECT_ADMIN   -> ConfigProvider.getProjectAdminPassword();
            case PROJECT_MANAGER -> ConfigProvider.getProjectManagerPassword();
            case LOCATION_MIXED  -> ConfigProvider.getLocationMixedPassword();
            case ORDER_GATHERER  -> ConfigProvider.getOrderGatheringPassword();
            case UNIT_ANALYST    -> ConfigProvider.getUnitAnalystPassword();
            case ANONYMOUS       -> "";
        };
    }

    /** Returns the primary storage ID that belongs to this role, as a String path param. */
    public String getStoreId() {
        return switch (this) {
            case ADMIN, RESOURCE_VIEWER, ACCOUNTANT, LOGIST -> "all";
            case OWNER_1, PROJECT_ADMIN, PROJECT_MANAGER, LOCATION_MIXED
                    -> String.valueOf(ConfigProvider.getOwner1StorageId());
            case OWNER_2       -> String.valueOf(ConfigProvider.getOwner2StorageId());
            case ORDER_GATHERER -> String.valueOf(ConfigProvider.getOrderGatheringStorageId());
            case CREW_MANAGER, CREW_READ, CREW_WRITE
                    -> String.valueOf(ConfigProvider.getUnitStorageId());
            case UNIT_ANALYST -> {
                long id = ConfigProvider.getOrderRequesterStorageId();
                yield id > 0 ? String.valueOf(id) : "";
            }
            case OWNER_3, ANONYMOUS -> "";
        };
    }
}
