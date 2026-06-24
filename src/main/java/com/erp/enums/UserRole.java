package com.erp.enums;

import com.erp.utils.config.ConfigProvider;

public enum UserRole {
    ADMIN,
    OWNER_1,
    OWNER_2,
    OWNER_3,
    RESOURCE_VIEWER,
    ACCOUNTANT,
    ANONYMOUS;

    public String getUsername() {
        return switch (this) {
            case ADMIN           -> ConfigProvider.getAdminUsername();
            case OWNER_1         -> ConfigProvider.getOwner1Username();
            case OWNER_2         -> ConfigProvider.getOwner2Username();
            case OWNER_3         -> ConfigProvider.getOwner3Username();
            case RESOURCE_VIEWER -> ConfigProvider.getResourceViewerUsername();
            case ACCOUNTANT      -> ConfigProvider.getAccountantUsername();
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
            case ANONYMOUS       -> "";
        };
    }

    /** Returns the primary storage ID that belongs to this role, as a String path param. */
    public String getStoreId() {
        return switch (this) {
            case ADMIN, RESOURCE_VIEWER, ACCOUNTANT -> "all";
            case OWNER_1   -> String.valueOf(ConfigProvider.getOwner1StorageId());
            case OWNER_2   -> String.valueOf(ConfigProvider.getOwner2StorageId());
            case OWNER_3   -> "";
            case ANONYMOUS -> "";
        };
    }
}
