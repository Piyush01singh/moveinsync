package com.moveinsync.mdm.service;

import com.moveinsync.mdm.config.MdmLifecycleProperties;
import com.moveinsync.mdm.dto.UpdateStatusRequest;
import com.moveinsync.mdm.exception.ApiException;
import com.moveinsync.mdm.model.Device;
import com.moveinsync.mdm.model.DeviceUpdateState;
import com.moveinsync.mdm.model.UpdateLifecycleStatus;
import com.moveinsync.mdm.model.UpdateSchedule;
import com.moveinsync.mdm.repository.DeviceRepository;
import com.moveinsync.mdm.repository.DeviceUpdateStateRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateLifecycleService {

    private final DeviceUpdateStateRepository deviceUpdateStateRepository;
    private final DeviceRepository deviceRepository;
    private final AuditLogService auditLogService;
    private final MdmLifecycleProperties lifecycleProperties;
    private final VersionComparator versionComparator;
    private final MeterRegistry meterRegistry;

    @Transactional
    public void registerScheduleAndNotification(Device device, UpdateSchedule schedule) {
        LocalDateTime now = LocalDateTime.now();
        DeviceUpdateState state = deviceUpdateStateRepository.findByDeviceImei(device.getImeiNumber()).orElse(null);
        if (shouldStartNewSession(state, schedule)) {
            state = createNewSession(device, schedule, now);
        }

        if (state.getCurrentStatus() == UpdateLifecycleStatus.UPDATE_SCHEDULED) {
            markDeviceNotified(state, device, schedule, now);
        }
    }

    @Transactional
    public DeviceUpdateState processDeviceStatus(UpdateStatusRequest request) {
        LocalDateTime now = LocalDateTime.now();
        DeviceUpdateState state = deviceUpdateStateRepository.findByDeviceImei(request.getImeiNumber()).orElse(null);

        if (state == null) {
            if (request.getStatus() != UpdateLifecycleStatus.UPDATE_SCHEDULED) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "UPDATE_SESSION_NOT_FOUND",
                        "No update session exists for device. Send UPDATE_SCHEDULED first."
                );
            }
            state = new DeviceUpdateState();
            state.setDeviceImei(request.getImeiNumber());
            state.setCreatedAt(now);
            state.setRetryCount(0);
            state.setMaxRetries(lifecycleProperties.getMaxRetries());
        }

        applySessionMetadataFromRequest(state, request);

        if (request.getStatus() == UpdateLifecycleStatus.UPDATE_SCHEDULED) {
            resetToScheduled(state, now);
            return persistTransition(state, request, now);
        }

        if (state.getCurrentStatus() == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "LIFECYCLE_STATE_INVALID",
                    "Current lifecycle state is missing. Send UPDATE_SCHEDULED first."
            );
        }

        if (state.getCurrentStatus() != request.getStatus()) {
            validateTransition(state, request);
        }

        if (request.getStatus() == UpdateLifecycleStatus.FAILED) {
            validateFailurePayload(request);
            state.setRetryCount(state.getRetryCount() + 1);
            state.setLastFailureStage(normalizeStage(request.getFailureStage()));
            state.setLastFailureReason(request.getFailureReason());
        }

        state.setCurrentStatus(request.getStatus());
        state.setLastUpdatedAt(now);
        applyStatusTimestamps(state, request.getStatus(), now);

        if (request.getStatus() == UpdateLifecycleStatus.INSTALLATION_COMPLETED) {
            applyVersionUpgrade(state);
        }

        return persistTransition(state, request, now);
    }

    private void resetToScheduled(DeviceUpdateState state, LocalDateTime now) {
        state.setCurrentStatus(UpdateLifecycleStatus.UPDATE_SCHEDULED);
        state.setRetryCount(0);
        state.setLastFailureStage(null);
        state.setLastFailureReason(null);
        state.setLastUpdatedAt(now);
    }

    private DeviceUpdateState persistTransition(DeviceUpdateState state, UpdateStatusRequest request, LocalDateTime now) {
        DeviceUpdateState saved = deviceUpdateStateRepository.save(state);
        auditLogService.logLifecycleEvent(
                request.getImeiNumber(),
                request.getStatus(),
                "DEVICE",
                "DEVICE_API",
                saved.getScheduleId(),
                saved.getFromVersion(),
                saved.getToVersion(),
                request.getStatus() == UpdateLifecycleStatus.FAILED ? normalizeStage(request.getFailureStage()) : null,
                request.getStatus() == UpdateLifecycleStatus.FAILED ? request.getFailureReason() : null,
                "retryCount=%d/%d,processedAt=%s".formatted(saved.getRetryCount(), saved.getMaxRetries(), now)
        );
        meterRegistry.counter("mdm.lifecycle.transition", "status", request.getStatus().name(), "source", "device")
                .increment();
        return saved;
    }

    private void applySessionMetadataFromRequest(DeviceUpdateState state, UpdateStatusRequest request) {
        if (request.getScheduleId() != null) {
            state.setScheduleId(request.getScheduleId());
        }
        if (request.getFromVersion() != null && !request.getFromVersion().isBlank()) {
            state.setFromVersion(request.getFromVersion().trim());
        }
        if (request.getToVersion() != null && !request.getToVersion().isBlank()) {
            state.setToVersion(request.getToVersion().trim());
        }
    }

    private void validateTransition(DeviceUpdateState state, UpdateStatusRequest request) {
        UpdateLifecycleStatus current = state.getCurrentStatus();
        UpdateLifecycleStatus next = request.getStatus();

        if (current == UpdateLifecycleStatus.FAILED) {
            Set<UpdateLifecycleStatus> allowedAfterFailure = allowedAfterFailure(state);
            if (!allowedAfterFailure.contains(next)) {
                throw invalidTransition(current, next, allowedAfterFailure);
            }
            return;
        }

        Set<UpdateLifecycleStatus> allowed = switch (current) {
            case UPDATE_SCHEDULED -> EnumSet.of(UpdateLifecycleStatus.DEVICE_NOTIFIED, UpdateLifecycleStatus.FAILED);
            case DEVICE_NOTIFIED -> EnumSet.of(UpdateLifecycleStatus.DOWNLOAD_STARTED, UpdateLifecycleStatus.FAILED);
            case DOWNLOAD_STARTED -> EnumSet.of(UpdateLifecycleStatus.DOWNLOAD_COMPLETED, UpdateLifecycleStatus.FAILED);
            case DOWNLOAD_COMPLETED -> EnumSet.of(UpdateLifecycleStatus.INSTALLATION_STARTED, UpdateLifecycleStatus.FAILED);
            case INSTALLATION_STARTED ->
                    EnumSet.of(UpdateLifecycleStatus.INSTALLATION_COMPLETED, UpdateLifecycleStatus.FAILED);
            case INSTALLATION_COMPLETED -> EnumSet.of(UpdateLifecycleStatus.UPDATE_SCHEDULED);
            case FAILED -> EnumSet.noneOf(UpdateLifecycleStatus.class);
        };

        if (!allowed.contains(next)) {
            throw invalidTransition(current, next, allowed);
        }
    }

    private Set<UpdateLifecycleStatus> allowedAfterFailure(DeviceUpdateState state) {
        if (state.getRetryCount() >= state.getMaxRetries()) {
            return EnumSet.of(UpdateLifecycleStatus.UPDATE_SCHEDULED);
        }
        UpdateLifecycleStatus retryStatus = retryStatusForFailureStage(state.getLastFailureStage());
        return EnumSet.of(UpdateLifecycleStatus.UPDATE_SCHEDULED, retryStatus);
    }

    private UpdateLifecycleStatus retryStatusForFailureStage(String failureStage) {
        if (failureStage == null || failureStage.isBlank()) {
            return UpdateLifecycleStatus.DEVICE_NOTIFIED;
        }
        String normalized = normalizeStage(failureStage);
        if (normalized.contains("DOWNLOAD")) {
            return UpdateLifecycleStatus.DOWNLOAD_STARTED;
        }
        if (normalized.contains("INSTALL")) {
            return UpdateLifecycleStatus.INSTALLATION_STARTED;
        }
        if (normalized.contains("NOTIF")) {
            return UpdateLifecycleStatus.DEVICE_NOTIFIED;
        }
        if (normalized.contains("SCHEDULE")) {
            return UpdateLifecycleStatus.UPDATE_SCHEDULED;
        }
        return UpdateLifecycleStatus.DEVICE_NOTIFIED;
    }

    private ApiException invalidTransition(
            UpdateLifecycleStatus current,
            UpdateLifecycleStatus next,
            Set<UpdateLifecycleStatus> allowed
    ) {
        meterRegistry.counter("mdm.lifecycle.blocked", "type", "invalid_transition").increment();
        log.warn("Lifecycle transition blocked: {} -> {}. Allowed: {}", current, next, allowed);
        return new ApiException(
                HttpStatus.CONFLICT,
                "INVALID_LIFECYCLE_TRANSITION",
                "Transition %s -> %s is not allowed. Allowed transitions: %s".formatted(current, next, allowed)
        );
    }

    private void validateFailurePayload(UpdateStatusRequest request) {
        if (request.getFailureStage() == null || request.getFailureStage().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FAILURE_STAGE_REQUIRED", "Failure stage is required");
        }
        if (request.getFailureReason() == null || request.getFailureReason().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FAILURE_REASON_REQUIRED", "Failure reason is required");
        }
    }

    private void applyStatusTimestamps(DeviceUpdateState state, UpdateLifecycleStatus status, LocalDateTime now) {
        switch (status) {
            case DEVICE_NOTIFIED -> state.setNotificationSentAt(now);
            case DOWNLOAD_STARTED -> state.setDownloadStartedAt(now);
            case DOWNLOAD_COMPLETED -> state.setDownloadCompletedAt(now);
            case INSTALLATION_STARTED -> state.setInstallationStartedAt(now);
            case INSTALLATION_COMPLETED -> state.setInstallationCompletedAt(now);
            default -> {
                // No additional timestamp update required for UPDATE_SCHEDULED or FAILED.
            }
        }
    }

    private void applyVersionUpgrade(DeviceUpdateState state) {
        if (state.getToVersion() == null || state.getToVersion().isBlank()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "TARGET_VERSION_MISSING",
                    "Target version is required before installation can complete"
            );
        }
        deviceRepository.findByImeiNumber(state.getDeviceImei()).ifPresent(device -> {
            if (versionComparator.isDowngrade(device.getAppVersion(), state.getToVersion())) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "DOWNGRADE_BLOCKED",
                        "Device-level downgrade attempt blocked during installation"
                );
            }
            device.setAppVersion(state.getToVersion());
            deviceRepository.save(device);
        });
    }

    private String normalizeStage(String rawStage) {
        return rawStage == null ? null : rawStage.trim().toUpperCase();
    }

    private boolean shouldStartNewSession(DeviceUpdateState currentState, UpdateSchedule schedule) {
        if (currentState == null) {
            return true;
        }
        return currentState.getCurrentStatus() == UpdateLifecycleStatus.INSTALLATION_COMPLETED
                || (currentState.getCurrentStatus() == UpdateLifecycleStatus.FAILED
                && currentState.getRetryCount() >= currentState.getMaxRetries())
                || currentState.getToVersion() == null
                || !currentState.getToVersion().equals(schedule.getToVersion());
    }

    private DeviceUpdateState createNewSession(Device device, UpdateSchedule schedule, LocalDateTime now) {
        DeviceUpdateState state = new DeviceUpdateState();
        state.setDeviceImei(device.getImeiNumber());
        state.setScheduleId(schedule.getId());
        state.setFromVersion(device.getAppVersion());
        state.setToVersion(schedule.getToVersion());
        state.setCurrentStatus(UpdateLifecycleStatus.UPDATE_SCHEDULED);
        state.setRetryCount(0);
        state.setMaxRetries(lifecycleProperties.getMaxRetries());
        state.setCreatedAt(now);
        state.setLastUpdatedAt(now);
        DeviceUpdateState saved = deviceUpdateStateRepository.save(state);

        auditLogService.logSystemDeviceEvent(
                device.getImeiNumber(),
                UpdateLifecycleStatus.UPDATE_SCHEDULED,
                schedule.getId(),
                device.getAppVersion(),
                schedule.getToVersion(),
                "scope[region=%s,customization=%s,deviceGroup=%s,rollout=%s%%]".formatted(
                        schedule.getTargetRegion(),
                        schedule.getCustomizationTag(),
                        schedule.getTargetDeviceGroup(),
                        schedule.getRolloutPercentage() == null ? 100 : schedule.getRolloutPercentage()
                )
        );
        meterRegistry.counter("mdm.lifecycle.transition", "status", "UPDATE_SCHEDULED", "source", "system")
                .increment();
        return saved;
    }

    private void markDeviceNotified(
            DeviceUpdateState state,
            Device device,
            UpdateSchedule schedule,
            LocalDateTime now
    ) {
        state.setCurrentStatus(UpdateLifecycleStatus.DEVICE_NOTIFIED);
        state.setNotificationSentAt(now);
        state.setLastUpdatedAt(now);
        state.setScheduleId(schedule.getId());
        state.setFromVersion(device.getAppVersion());
        state.setToVersion(schedule.getToVersion());
        deviceUpdateStateRepository.save(state);

        auditLogService.logSystemDeviceEvent(
                device.getImeiNumber(),
                UpdateLifecycleStatus.DEVICE_NOTIFIED,
                schedule.getId(),
                device.getAppVersion(),
                schedule.getToVersion(),
                "notification-trigger=heartbeat"
        );
        meterRegistry.counter("mdm.lifecycle.transition", "status", "DEVICE_NOTIFIED", "source", "system")
                .increment();
    }
}
