package com.moveinsync.mdm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum UpdateLifecycleStatus {
    UPDATE_SCHEDULED,
    DEVICE_NOTIFIED,
    DOWNLOAD_STARTED,
    DOWNLOAD_COMPLETED,
    INSTALLATION_STARTED,
    INSTALLATION_COMPLETED,
    FAILED;

    @JsonCreator
    public static UpdateLifecycleStatus fromInput(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        return UpdateLifecycleStatus.valueOf(normalized);
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
