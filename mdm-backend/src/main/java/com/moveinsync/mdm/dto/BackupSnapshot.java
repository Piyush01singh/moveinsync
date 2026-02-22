package com.moveinsync.mdm.dto;

import com.moveinsync.mdm.model.AppVersion;
import com.moveinsync.mdm.model.AuditLog;
import com.moveinsync.mdm.model.Device;
import com.moveinsync.mdm.model.DeviceUpdateState;
import com.moveinsync.mdm.model.UpdateSchedule;
import com.moveinsync.mdm.model.VersionCompatibility;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class BackupSnapshot {
    private String schemaVersion = "1";
    private String checksum;
    private String createdBy;
    private LocalDateTime createdAt;

    private List<Device> devices = new ArrayList<>();
    private List<AppVersion> appVersions = new ArrayList<>();
    private List<VersionCompatibility> compatibilityRules = new ArrayList<>();
    private List<UpdateSchedule> updateSchedules = new ArrayList<>();
    private List<DeviceUpdateState> deviceUpdateStates = new ArrayList<>();
    private List<AuditLog> auditLogs = new ArrayList<>();
}
