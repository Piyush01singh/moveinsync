package com.moveinsync.mdm.service;

import com.moveinsync.mdm.dto.HeartbeatRequest;
import com.moveinsync.mdm.dto.HeartbeatResponse;
import com.moveinsync.mdm.model.AppVersion;
import com.moveinsync.mdm.model.Device;
import com.moveinsync.mdm.model.UpdateSchedule;
import com.moveinsync.mdm.model.VersionCompatibility;
import com.moveinsync.mdm.repository.UpdateScheduleRepository;
import com.moveinsync.mdm.repository.VersionCompatibilityRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceService {

    private final DeviceRegistryService deviceRegistryService;
    private final AppVersionService appVersionService;
    private final UpdateScheduleRepository scheduleRepository;
    private final VersionCompatibilityRepository compatibilityRepository;
    private final VersionComparator versionComparator;
    private final OsCompatibilityService osCompatibilityService;
    private final UpdateLifecycleService updateLifecycleService;
    private final MeterRegistry meterRegistry;

    public HeartbeatResponse processHeartbeat(HeartbeatRequest request) {
        log.info("Processing heartbeat for IMEI: {}", request.getImeiNumber());
        Device device = deviceRegistryService.upsertFromHeartbeat(request);
        return determineUpdateAvailability(device);
    }

    private HeartbeatResponse determineUpdateAvailability(Device device) {
        String currentVersion = device.getAppVersion();
        HeartbeatResponse response = buildDefaultResponse(device.getImeiNumber());

        Optional<UpdateSchedule> applicableSchedule = findApplicableSchedule(device, currentVersion);
        if (applicableSchedule.isEmpty()) {
            return response;
        }
        return applyScheduleDecision(device, currentVersion, applicableSchedule.get(), response);
    }

    private boolean isDowngrade(String current, String target) {
        return versionComparator.isDowngrade(current, target);
    }

    private boolean isScheduleEnabled(UpdateSchedule schedule) {
        if (!schedule.isApprovalRequired()) {
            return true;
        }
        return "APPROVED".equalsIgnoreCase(schedule.getApprovalStatus());
    }

    private boolean isDeviceInRolloutBucket(String imeiNumber, Integer rolloutPercentage) {
        int percentage = rolloutPercentage == null ? 100 : rolloutPercentage;
        if (percentage >= 100) {
            return true;
        }
        // Deterministic bucket assignment keeps rollout behavior stable across heartbeats.
        int bucket = Math.floorMod(imeiNumber.hashCode(), 100) + 1;
        return bucket <= percentage;
    }

    private HeartbeatResponse buildDefaultResponse(String imeiNumber) {
        HeartbeatResponse response = new HeartbeatResponse();
        response.setImeiNumber(imeiNumber);
        response.setUpdateAvailable(false);
        response.setMessage("App is up-to-date");
        return response;
    }

    private Optional<UpdateSchedule> findApplicableSchedule(Device device, String currentVersion) {
        List<UpdateSchedule> schedules = scheduleRepository.findApplicableSchedules(
                currentVersion,
                device.getLocationRegion(),
                device.getCustomizationTag(),
                device.getDeviceGroup(),
                LocalDateTime.now()
        );

        return schedules.stream()
                .filter(this::isScheduleEnabled)
                .filter(s -> isDeviceInRolloutBucket(device.getImeiNumber(), s.getRolloutPercentage()))
                .findFirst();
    }

    private HeartbeatResponse applyScheduleDecision(
            Device device,
            String currentVersion,
            UpdateSchedule schedule,
            HeartbeatResponse response
    ) {
        if (isDowngrade(currentVersion, schedule.getToVersion())) {
            log.warn("Attempted downgrade strictly blocked for device {} from {} to {}",
                    device.getImeiNumber(), currentVersion, schedule.getToVersion());
            incrementPolicyBlock("downgrade");
            return response;
        }

        Optional<String> mandatoryIntermediate = findMandatoryIntermediate(currentVersion, schedule.getToVersion());
        if (mandatoryIntermediate.isPresent()) {
            response.setMessage("Upgrade requires intermediate version " + mandatoryIntermediate.get());
            incrementPolicyBlock("mandatory_intermediate");
            return response;
        }

        if (currentVersion.equals(schedule.getToVersion())) {
            return response;
        }

        Optional<AppVersion> targetAppVersion = appVersionService.getVersionByCode(schedule.getToVersion());
        if (targetAppVersion.isEmpty()) {
            response.setMessage("Update blocked: target version metadata not found");
            incrementPolicyBlock("target_version_missing");
            return response;
        }

        AppVersion target = targetAppVersion.get();
        if (!osCompatibilityService.isCompatible(device.getDeviceOs(), target.getSupportedOsRange())) {
            response.setMessage("Update blocked: device OS does not satisfy supported range " + target.getSupportedOsRange());
            incrementPolicyBlock("os_incompatible");
            return response;
        }

        response.setUpdateAvailable(true);
        response.setScheduleId(schedule.getId());
        response.setFromVersion(currentVersion);
        response.setTargetVersion(schedule.getToVersion());
        response.setMandatory(target.isMandatory());
        response.setMessage("Update available to version " + schedule.getToVersion());
        updateLifecycleService.registerScheduleAndNotification(device, schedule);
        return response;
    }

    private Optional<String> findMandatoryIntermediate(String fromVersion, String toVersion) {
        return compatibilityRepository.findByFromVersionAndToVersion(fromVersion, toVersion)
                .filter(VersionCompatibility::hasMandatoryIntermediate)
                .map(VersionCompatibility::getMandatoryIntermediate);
    }

    private void incrementPolicyBlock(String type) {
        meterRegistry.counter("mdm.policy.blocked", "type", type).increment();
    }
}
