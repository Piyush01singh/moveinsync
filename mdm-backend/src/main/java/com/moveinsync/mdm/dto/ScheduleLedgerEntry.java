package com.moveinsync.mdm.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class ScheduleLedgerEntry {
    Long scheduleId;
    String fromVersion;
    String toVersion;
    String targetRegion;
    String targetDeviceGroup;
    String customizationTag;
    Integer rolloutPercentage;
    String approvalStatus;
    String createdBy;
    String approvedBy;
    boolean immediate;
    LocalDateTime scheduledTime;
    LocalDateTime createdAt;
    LocalDateTime lastEventAt;
    String lastEventAction;
    Long notifiedDevices;
    Long successfulDevices;
    Long failedDevices;
}
