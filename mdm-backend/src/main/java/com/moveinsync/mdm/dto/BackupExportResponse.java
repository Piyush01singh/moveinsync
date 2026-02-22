package com.moveinsync.mdm.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BackupExportResponse {
    String filePath;
    String checksum;
    long exportedDevices;
    long exportedVersions;
    long exportedSchedules;
    long exportedLifecycleStates;
    long exportedAuditLogs;
    long exportedCompatibilityRules;
    String exportedBy;
    String exportedAt;
}
