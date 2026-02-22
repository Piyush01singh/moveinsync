package com.moveinsync.mdm.service;

import com.moveinsync.mdm.dto.AdminScheduleRequest;
import com.moveinsync.mdm.dto.AuditIntegrityResponse;
import com.moveinsync.mdm.dto.DashboardResponse;
import com.moveinsync.mdm.dto.ScheduleLedgerEntry;
import com.moveinsync.mdm.exception.ApiException;
import com.moveinsync.mdm.model.AppVersion;
import com.moveinsync.mdm.model.AuditLog;
import com.moveinsync.mdm.model.Device;
import com.moveinsync.mdm.model.UpdateLifecycleStatus;
import com.moveinsync.mdm.model.UpdateSchedule;
import com.moveinsync.mdm.model.VersionCompatibility;
import com.moveinsync.mdm.repository.AppVersionRepository;
import com.moveinsync.mdm.repository.AuditLogRepository;
import com.moveinsync.mdm.repository.DeviceRepository;
import com.moveinsync.mdm.repository.DeviceUpdateStateRepository;
import com.moveinsync.mdm.repository.LabelCountProjection;
import com.moveinsync.mdm.repository.RegionVersionCountProjection;
import com.moveinsync.mdm.repository.UpdateScheduleRepository;
import com.moveinsync.mdm.repository.VersionCompatibilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminService {
    private static final String SYSTEM_DEVICE_IMEI = "SYSTEM";

    private final AppVersionRepository appVersionRepository;
    private final VersionCompatibilityRepository compatibilityRepository;
    private final UpdateScheduleRepository updateScheduleRepository;
    private final DeviceRepository deviceRepository;
    private final AuditLogRepository auditLogRepository;
    private final DeviceUpdateStateRepository deviceUpdateStateRepository;
    private final VersionComparator versionComparator;
    private final AuditLogService auditLogService;
    private final CacheManager cacheManager;
    private final OsCompatibilityService osCompatibilityService;

    @Transactional
    public AppVersion createVersion(AppVersion request, String actor) {
        if (request.getVersionCode() == null || request.getVersionCode().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VERSION_CODE_REQUIRED", "Version code is required");
        }
        if (!osCompatibilityService.isSupportedRangeFormat(request.getSupportedOsRange())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_OS_RANGE",
                    "supportedOsRange must match format like 'Android 12+', 'Android 12-14', or 'iOS 16+'"
            );
        }
        if (appVersionRepository.existsByVersionCode(request.getVersionCode())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "VERSION_IMMUTABLE",
                    "Version " + request.getVersionCode() + " already exists and cannot be modified"
            );
        }

        if (request.getReleaseDate() == null) {
            request.setReleaseDate(LocalDateTime.now());
        }
        AppVersion saved = appVersionRepository.save(request);
        auditLogService.logAdminAction(
                "VERSION_PUBLISHED",
                actor,
                null,
                null,
                saved.getVersionCode(),
                "mandatory=%s,supportedOsRange=%s,customizationTag=%s".formatted(
                        saved.isMandatory(),
                        saved.getSupportedOsRange(),
                        saved.getCustomizationTag()
                )
        );
        return saved;
    }

    @Transactional
    public VersionCompatibility createCompatibilityRule(VersionCompatibility request, String actor) {
        if (request.getFromVersion() == null || request.getFromVersion().isBlank()
                || request.getToVersion() == null || request.getToVersion().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COMPATIBILITY_INVALID", "From and to version are required");
        }
        ensureVersionExists(request.getFromVersion(), "FROM_VERSION_NOT_FOUND");
        ensureVersionExists(request.getToVersion(), "TO_VERSION_NOT_FOUND");
        if (versionComparator.isDowngrade(request.getFromVersion(), request.getToVersion())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DOWNGRADE_BLOCKED", "Compatibility cannot allow downgrades");
        }
        VersionCompatibility saved = compatibilityRepository.save(request);
        auditLogService.logAdminAction(
                "COMPATIBILITY_CREATED",
                actor,
                null,
                saved.getFromVersion(),
                saved.getToVersion(),
                "mandatoryIntermediate=%s".formatted(saved.getMandatoryIntermediate())
        );
        return saved;
    }

    @Transactional
    public UpdateSchedule createSchedule(AdminScheduleRequest request, String actor) {
        ensureVersionExists(request.getFromVersion(), "FROM_VERSION_NOT_FOUND");
        AppVersion targetVersion = ensureVersionExists(request.getToVersion(), "TO_VERSION_NOT_FOUND");

        if (versionComparator.compare(request.getFromVersion(), request.getToVersion()) == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_OP_SCHEDULE", "From and to versions cannot be identical");
        }
        if (versionComparator.isDowngrade(request.getFromVersion(), request.getToVersion())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DOWNGRADE_BLOCKED", "Downgrade schedules are not allowed");
        }

        compatibilityRepository.findByFromVersionAndToVersion(request.getFromVersion(), request.getToVersion())
                .ifPresent(rule -> {
                    if (rule.hasMandatoryIntermediate()) {
                        throw new ApiException(
                                HttpStatus.CONFLICT,
                                "MANDATORY_INTERMEDIATE_REQUIRED",
                                "Devices must first upgrade to " + rule.getMandatoryIntermediate()
                        );
                    }
                });

        LocalDateTime scheduledTime = resolveScheduleTime(request);
        int rolloutPercentage = request.getRolloutPercentage() == null ? 100 : request.getRolloutPercentage();

        UpdateSchedule schedule = new UpdateSchedule();
        schedule.setFromVersion(request.getFromVersion());
        schedule.setToVersion(request.getToVersion());
        schedule.setTargetRegion(request.getTargetRegion());
        schedule.setCustomizationTag(request.getCustomizationTag());
        schedule.setTargetDeviceGroup(request.getTargetDeviceGroup());
        schedule.setScheduledTime(scheduledTime);
        schedule.setImmediate(request.isImmediate());
        schedule.setRolloutPercentage(rolloutPercentage);
        schedule.setCreatedBy(actor);

        boolean approvalRequired = targetVersion.isMandatory();
        schedule.setApprovalRequired(approvalRequired);
        schedule.setApprovalStatus(approvalRequired ? "PENDING" : "APPROVED");

        UpdateSchedule saved = updateScheduleRepository.save(schedule);
        auditLogService.logAdminAction(
                "UPDATE_SCHEDULED",
                actor,
                saved.getId(),
                saved.getFromVersion(),
                saved.getToVersion(),
                "scope[region=%s,customization=%s,deviceGroup=%s,rollout=%s%%,immediate=%s,scheduledTime=%s]".formatted(
                        saved.getTargetRegion(),
                        saved.getCustomizationTag(),
                        saved.getTargetDeviceGroup(),
                        saved.getRolloutPercentage(),
                        saved.isImmediate(),
                        saved.getScheduledTime()
                )
        );
        return saved;
    }

    @Transactional
    public UpdateSchedule approveSchedule(Long scheduleId, String approver, String comment) {
        UpdateSchedule schedule = updateScheduleRepository.findById(scheduleId).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Schedule not found")
        );
        schedule.setApprovalRequired(true);
        schedule.setApprovalStatus("APPROVED");
        schedule.setApprovedBy(approver);
        UpdateSchedule saved = updateScheduleRepository.save(schedule);
        auditLogService.logAdminAction(
                "SCHEDULE_APPROVED",
                approver,
                saved.getId(),
                saved.getFromVersion(),
                saved.getToVersion(),
                "comment=%s".formatted(comment)
        );
        return saved;
    }

    public List<UpdateSchedule> listSchedules() {
        return updateScheduleRepository.findAll();
    }

    public List<ScheduleLedgerEntry> getScheduleLedger() {
        List<UpdateSchedule> schedules = updateScheduleRepository.findAll();
        if (schedules.isEmpty()) {
            return List.of();
        }

        List<Long> scheduleIds = schedules.stream()
                .map(UpdateSchedule::getId)
                .toList();
        List<AuditLog> events = auditLogRepository.findByScheduleIdInOrderByTimestampAsc(scheduleIds);

        Map<Long, LocalDateTime> createdAtBySchedule = new LinkedHashMap<>();
        Map<Long, LocalDateTime> lastEventAtBySchedule = new LinkedHashMap<>();
        Map<Long, String> lastEventActionBySchedule = new LinkedHashMap<>();
        Map<Long, Set<String>> notifiedDevicesBySchedule = new LinkedHashMap<>();
        Map<Long, Set<String>> successDevicesBySchedule = new LinkedHashMap<>();
        Map<Long, Set<String>> failedDevicesBySchedule = new LinkedHashMap<>();

        for (AuditLog event : events) {
            accumulateLedgerEvent(
                    event,
                    createdAtBySchedule,
                    lastEventAtBySchedule,
                    lastEventActionBySchedule,
                    notifiedDevicesBySchedule,
                    successDevicesBySchedule,
                    failedDevicesBySchedule
            );
        }

        List<ScheduleLedgerEntry> ledger = new ArrayList<>(schedules.size());
        for (UpdateSchedule schedule : schedules) {
            ledger.add(buildLedgerEntry(
                    schedule,
                    createdAtBySchedule,
                    lastEventAtBySchedule,
                    lastEventActionBySchedule,
                    notifiedDevicesBySchedule,
                    successDevicesBySchedule,
                    failedDevicesBySchedule
            ));
        }
        return ledger;
    }

    public DashboardResponse getDashboard(int inactiveMinutes) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusMinutes(inactiveMinutes);
        long total = deviceRepository.count();
        long active = deviceRepository.countByLastAppOpenTimeAfter(threshold);
        long inactive = Math.max(total - active, 0);

        long successfulUpdates = auditLogRepository.countByAction(UpdateLifecycleStatus.INSTALLATION_COMPLETED.name());
        long failedUpdates = auditLogRepository.countByAction(UpdateLifecycleStatus.FAILED.name());
        long pendingUpdates = deviceUpdateStateRepository.countByCurrentStatusNot(UpdateLifecycleStatus.INSTALLATION_COMPLETED);
        long outcomes = successfulUpdates + failedUpdates;
        double successRate = outcomes == 0 ? 0.0 : round2(successfulUpdates * 100.0 / outcomes);
        double failureRate = outcomes == 0 ? 0.0 : round2(failedUpdates * 100.0 / outcomes);
        double rolloutProgress = computeRolloutProgressPercentage(now);

        Map<String, Long> versionDistribution = toDistributionMap(deviceRepository.countByVersion());
        Map<String, Long> regionDistribution = toDistributionMap(deviceRepository.countByRegion());
        Map<String, Long> deviceGroupDistribution = toDistributionMap(deviceRepository.countByDeviceGroup());
        Map<String, Long> failureStageDistribution = toDistributionMap(auditLogRepository.countFailuresByStage());
        Map<String, Map<String, Long>> heatmap = toHeatmap(deviceRepository.countByRegionAndVersion());

        List<String> inactiveImeis = deviceRepository
                .findTop10ByLastAppOpenTimeBeforeOrderByLastAppOpenTimeAsc(threshold)
                .stream()
                .map(Device::getImeiNumber)
                .toList();

        return DashboardResponse.builder()
                .totalDevices(total)
                .activeDevices(active)
                .inactiveDevices(inactive)
                .successfulUpdates(successfulUpdates)
                .failedUpdates(failedUpdates)
                .pendingUpdates(pendingUpdates)
                .successRatePercentage(successRate)
                .failureRatePercentage(failureRate)
                .rolloutProgressPercentage(rolloutProgress)
                .versionDistribution(versionDistribution)
                .regionDistribution(regionDistribution)
                .deviceGroupDistribution(deviceGroupDistribution)
                .failureStageDistribution(failureStageDistribution)
                .versionHeatmap(heatmap)
                .topInactiveDeviceImeis(inactiveImeis)
                .build();
    }

    public long countDevicesByRegionAndVersion(String region, String version) {
        return deviceRepository.countByLocationRegionIgnoreCaseAndAppVersion(region, version);
    }

    public List<AuditLog> getDeviceTimeline(String imeiNumber) {
        return auditLogService.getDeviceTimeline(imeiNumber);
    }

    public AuditIntegrityResponse verifyAuditIntegrity() {
        return auditLogService.verifyIntegrity();
    }

    public void evictVersionCache() {
        if (cacheManager.getCache("appVersions") != null) {
            cacheManager.getCache("appVersions").clear();
        }
    }

    private AppVersion ensureVersionExists(String versionCode, String errorCode) {
        return appVersionRepository.findByVersionCode(versionCode)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, errorCode, "Version " + versionCode + " not found"));
    }

    private void accumulateLedgerEvent(
            AuditLog event,
            Map<Long, LocalDateTime> createdAtBySchedule,
            Map<Long, LocalDateTime> lastEventAtBySchedule,
            Map<Long, String> lastEventActionBySchedule,
            Map<Long, Set<String>> notifiedDevicesBySchedule,
            Map<Long, Set<String>> successDevicesBySchedule,
            Map<Long, Set<String>> failedDevicesBySchedule
    ) {
        Long scheduleId = event.getScheduleId();
        if (scheduleId == null) {
            return;
        }

        createdAtBySchedule.computeIfAbsent(scheduleId, ignored -> event.getTimestamp());
        lastEventAtBySchedule.put(scheduleId, event.getTimestamp());
        lastEventActionBySchedule.put(scheduleId, event.getAction());

        if (isSystemOrUnknownDevice(event.getDeviceImei())) {
            return;
        }

        String action = event.getAction();
        if (UpdateLifecycleStatus.DEVICE_NOTIFIED.name().equals(action)) {
            notifiedDevicesBySchedule.computeIfAbsent(scheduleId, ignored -> new HashSet<>()).add(event.getDeviceImei());
            return;
        }
        if (UpdateLifecycleStatus.INSTALLATION_COMPLETED.name().equals(action)) {
            successDevicesBySchedule.computeIfAbsent(scheduleId, ignored -> new HashSet<>()).add(event.getDeviceImei());
            return;
        }
        if (UpdateLifecycleStatus.FAILED.name().equals(action)) {
            failedDevicesBySchedule.computeIfAbsent(scheduleId, ignored -> new HashSet<>()).add(event.getDeviceImei());
        }
    }

    private ScheduleLedgerEntry buildLedgerEntry(
            UpdateSchedule schedule,
            Map<Long, LocalDateTime> createdAtBySchedule,
            Map<Long, LocalDateTime> lastEventAtBySchedule,
            Map<Long, String> lastEventActionBySchedule,
            Map<Long, Set<String>> notifiedDevicesBySchedule,
            Map<Long, Set<String>> successDevicesBySchedule,
            Map<Long, Set<String>> failedDevicesBySchedule
    ) {
        Long scheduleId = schedule.getId();
        return ScheduleLedgerEntry.builder()
                .scheduleId(scheduleId)
                .fromVersion(schedule.getFromVersion())
                .toVersion(schedule.getToVersion())
                .targetRegion(schedule.getTargetRegion())
                .targetDeviceGroup(schedule.getTargetDeviceGroup())
                .customizationTag(schedule.getCustomizationTag())
                .rolloutPercentage(schedule.getRolloutPercentage())
                .approvalStatus(schedule.getApprovalStatus())
                .createdBy(schedule.getCreatedBy())
                .approvedBy(schedule.getApprovedBy())
                .immediate(schedule.isImmediate())
                .scheduledTime(schedule.getScheduledTime())
                .createdAt(createdAtBySchedule.getOrDefault(scheduleId, schedule.getScheduledTime()))
                .lastEventAt(lastEventAtBySchedule.get(scheduleId))
                .lastEventAction(lastEventActionBySchedule.get(scheduleId))
                .notifiedDevices((long) notifiedDevicesBySchedule.getOrDefault(scheduleId, Set.of()).size())
                .successfulDevices((long) successDevicesBySchedule.getOrDefault(scheduleId, Set.of()).size())
                .failedDevices((long) failedDevicesBySchedule.getOrDefault(scheduleId, Set.of()).size())
                .build();
    }

    private boolean isSystemOrUnknownDevice(String imei) {
        return imei == null || SYSTEM_DEVICE_IMEI.equalsIgnoreCase(imei);
    }

    private LocalDateTime resolveScheduleTime(AdminScheduleRequest request) {
        if (request.isImmediate()) {
            return LocalDateTime.now();
        }
        if (request.getScheduledTime() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SCHEDULE_TIME_REQUIRED",
                    "Scheduled time is required when immediate is false"
            );
        }
        if (request.getScheduledTime().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SCHEDULE_IN_PAST", "Scheduled time must be in the future");
        }
        return request.getScheduledTime();
    }

    private Map<String, Long> toDistributionMap(List<LabelCountProjection> data) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (LabelCountProjection row : data) {
            distribution.put(row.getLabel(), row.getTotal());
        }
        return distribution;
    }

    private Map<String, Map<String, Long>> toHeatmap(List<RegionVersionCountProjection> data) {
        Map<String, Map<String, Long>> heatmap = new LinkedHashMap<>();
        for (RegionVersionCountProjection row : data) {
            heatmap.computeIfAbsent(row.getRegion(), ignored -> new LinkedHashMap<>())
                    .put(row.getVersion(), row.getTotal());
        }
        return heatmap;
    }

    private double computeRolloutProgressPercentage(LocalDateTime now) {
        List<UpdateSchedule> activeSchedules = updateScheduleRepository.findActiveApprovedSchedules(now);
        long eligibleDevices = 0;
        long upgradedDevices = 0;

        for (UpdateSchedule schedule : activeSchedules) {
            String region = normalizeLower(schedule.getTargetRegion());
            String customizationTag = normalizeLower(schedule.getCustomizationTag());
            String deviceGroup = normalizeLower(schedule.getTargetDeviceGroup());
            long fromCount = deviceRepository.countInScopeByVersion(
                    schedule.getFromVersion(),
                    region,
                    customizationTag,
                    deviceGroup
            );
            long toCount = deviceRepository.countInScopeByVersion(
                    schedule.getToVersion(),
                    region,
                    customizationTag,
                    deviceGroup
            );

            eligibleDevices += fromCount + toCount;
            upgradedDevices += toCount;
        }

        if (eligibleDevices == 0) {
            return 0.0;
        }
        return round2(upgradedDevices * 100.0 / eligibleDevices);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String normalizeLower(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
