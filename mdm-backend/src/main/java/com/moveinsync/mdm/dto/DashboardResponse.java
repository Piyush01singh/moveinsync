package com.moveinsync.mdm.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class DashboardResponse {
    long totalDevices;
    long activeDevices;
    long inactiveDevices;
    long successfulUpdates;
    long failedUpdates;
    long pendingUpdates;
    double successRatePercentage;
    double failureRatePercentage;
    double rolloutProgressPercentage;
    Map<String, Long> versionDistribution;
    Map<String, Long> regionDistribution;
    Map<String, Long> deviceGroupDistribution;
    Map<String, Long> failureStageDistribution;
    Map<String, Map<String, Long>> versionHeatmap;
    List<String> topInactiveDeviceImeis;
}
