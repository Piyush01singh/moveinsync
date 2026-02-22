package com.moveinsync.mdm.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BackupImportResponse {
    String importedFrom;
    boolean replacedExistingData;
    long importedDevices;
    long importedVersions;
    long importedSchedules;
    long importedLifecycleStates;
    long importedAuditLogs;
    long importedCompatibilityRules;
}
