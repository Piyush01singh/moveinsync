package com.moveinsync.mdm.service;

import com.moveinsync.mdm.dto.AuditIntegrityResponse;
import com.moveinsync.mdm.model.AuditLog;
import com.moveinsync.mdm.model.UpdateLifecycleStatus;
import com.moveinsync.mdm.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public AuditLog logLifecycleEvent(
            String deviceImei,
            UpdateLifecycleStatus status,
            String actorId,
            String eventSource,
            Long scheduleId,
            String fromVersion,
            String toVersion,
            String failureStage,
            String failureReason,
            String details
    ) {
        AuditLog auditLog = baseLog(deviceImei, status.name(), actorId, eventSource, details);
        auditLog.setScheduleId(scheduleId);
        auditLog.setFromVersion(fromVersion);
        auditLog.setToVersion(toVersion);
        auditLog.setFailureStage(failureStage);
        auditLog.setFailureReason(failureReason);
        return auditLogRepository.save(auditLog);
    }

    @Transactional
    public AuditLog logAdminAction(
            String action,
            String actorId,
            Long scheduleId,
            String fromVersion,
            String toVersion,
            String details
    ) {
        AuditLog auditLog = baseLog("SYSTEM", action, actorId, "ADMIN", details);
        auditLog.setScheduleId(scheduleId);
        auditLog.setFromVersion(fromVersion);
        auditLog.setToVersion(toVersion);
        return auditLogRepository.save(auditLog);
    }

    @Transactional
    public AuditLog logSystemDeviceEvent(
            String deviceImei,
            UpdateLifecycleStatus status,
            Long scheduleId,
            String fromVersion,
            String toVersion,
            String details
    ) {
        AuditLog auditLog = baseLog(deviceImei, status.name(), "SYSTEM", "SYSTEM", details);
        auditLog.setScheduleId(scheduleId);
        auditLog.setFromVersion(fromVersion);
        auditLog.setToVersion(toVersion);
        return auditLogRepository.save(auditLog);
    }

    public List<AuditLog> getDeviceTimeline(String imeiNumber) {
        return auditLogRepository.findByDeviceImeiOrderByTimestampAsc(imeiNumber);
    }

    public AuditIntegrityResponse verifyIntegrity() {
        List<AuditLog> logs = auditLogRepository.findAllByOrderByIdAsc();
        String expectedPrevious = null;
        long legacyUnsealed = 0;
        for (AuditLog log : logs) {
            String storedEventHash = normalizeHash(log.getEventHash());
            String storedPreviousHash = normalizeHash(log.getPreviousHash());
            String recomputed = computeEventHash(log);

            if (storedEventHash == null) {
                legacyUnsealed++;
                expectedPrevious = recomputed;
                continue;
            }

            if (!Objects.equals(storedPreviousHash, normalizeHash(expectedPrevious))) {
                if (storedPreviousHash == null && expectedPrevious != null && legacyUnsealed > 0) {
                    // Legacy boundary: previous event existed before hash-chain sealing.
                    expectedPrevious = recomputed;
                    continue;
                }
                return AuditIntegrityResponse.builder()
                        .valid(false)
                        .checkedEvents(logs.size())
                        .firstBrokenEventId(log.getId())
                        .message("Previous hash pointer mismatch at event " + log.getId())
                        .build();
            }
            if (!Objects.equals(storedEventHash, recomputed)) {
                return AuditIntegrityResponse.builder()
                        .valid(false)
                        .checkedEvents(logs.size())
                        .firstBrokenEventId(log.getId())
                        .message("Event hash mismatch at event " + log.getId())
                        .build();
            }
            expectedPrevious = recomputed;
        }
        return AuditIntegrityResponse.builder()
                .valid(true)
                .checkedEvents(logs.size())
                .firstBrokenEventId(null)
                .message(legacyUnsealed == 0
                        ? "Audit chain integrity verified"
                        : "Audit chain verified with " + legacyUnsealed + " legacy unsealed events")
                .build();
    }

    @Transactional
    public void resealChainForBootstrap() {
        List<AuditLog> logs = auditLogRepository.findAllByOrderByIdAsc();
        String previousHash = null;
        for (AuditLog log : logs) {
            log.setPreviousHash(previousHash);
            log.setEventHash(computeEventHash(log));
            previousHash = log.getEventHash();
        }
        auditLogRepository.saveAll(logs);
    }

    @Transactional
    public AuditLog appendRecoveredEvent(AuditLog source) {
        AuditLog recovered = new AuditLog();
        recovered.setDeviceImei(source.getDeviceImei());
        recovered.setActorId(source.getActorId());
        recovered.setEventSource(source.getEventSource());
        recovered.setScheduleId(source.getScheduleId());
        recovered.setAction(source.getAction());
        recovered.setFromVersion(source.getFromVersion());
        recovered.setToVersion(source.getToVersion());
        recovered.setFailureStage(source.getFailureStage());
        recovered.setFailureReason(source.getFailureReason());
        recovered.setDetails(source.getDetails());
        recovered.setTimestamp(source.getTimestamp() == null ? LocalDateTime.now() : source.getTimestamp());
        enrichHashes(recovered);
        return auditLogRepository.save(recovered);
    }

    private AuditLog baseLog(
            String deviceImei,
            String action,
            String actorId,
            String eventSource,
            String details
    ) {
        AuditLog auditLog = new AuditLog();
        auditLog.setDeviceImei(deviceImei);
        auditLog.setAction(action);
        auditLog.setActorId(actorId);
        auditLog.setEventSource(eventSource);
        auditLog.setDetails(details);
        auditLog.setTimestamp(LocalDateTime.now());
        enrichHashes(auditLog);
        return auditLog;
    }

    private void enrichHashes(AuditLog auditLog) {
        AuditLog previous = auditLogRepository.findTopByOrderByIdDesc();
        String previousHash = previous == null ? null : normalizeHash(previous.getEventHash());
        auditLog.setPreviousHash(previousHash);
        auditLog.setEventHash(computeEventHash(auditLog));
    }

    private String computeEventHash(AuditLog auditLog) {
        String payload = String.join("|",
                safe(auditLog.getDeviceImei()),
                safe(auditLog.getActorId()),
                safe(auditLog.getEventSource()),
                safe(auditLog.getAction()),
                safe(auditLog.getFromVersion()),
                safe(auditLog.getToVersion()),
                safe(auditLog.getFailureStage()),
                safe(auditLog.getFailureReason()),
                safe(auditLog.getDetails()),
                safe(auditLog.getScheduleId()),
                safe(auditLog.getTimestamp()),
                safe(auditLog.getPreviousHash())
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String normalizeHash(String hash) {
        return hash == null ? null : hash.trim().toLowerCase();
    }
}
