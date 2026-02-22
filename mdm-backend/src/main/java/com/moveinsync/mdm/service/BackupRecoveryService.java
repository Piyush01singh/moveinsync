package com.moveinsync.mdm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.mdm.dto.BackupExportResponse;
import com.moveinsync.mdm.dto.BackupImportRequest;
import com.moveinsync.mdm.dto.BackupImportResponse;
import com.moveinsync.mdm.dto.BackupSnapshot;
import com.moveinsync.mdm.exception.ApiException;
import com.moveinsync.mdm.model.AuditLog;
import com.moveinsync.mdm.repository.AppVersionRepository;
import com.moveinsync.mdm.repository.AuditLogRepository;
import com.moveinsync.mdm.repository.DeviceRepository;
import com.moveinsync.mdm.repository.DeviceUpdateStateRepository;
import com.moveinsync.mdm.repository.UpdateScheduleRepository;
import com.moveinsync.mdm.repository.VersionCompatibilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BackupRecoveryService {

    private final DeviceRepository deviceRepository;
    private final AppVersionRepository appVersionRepository;
    private final VersionCompatibilityRepository compatibilityRepository;
    private final UpdateScheduleRepository scheduleRepository;
    private final DeviceUpdateStateRepository deviceUpdateStateRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public BackupExportResponse exportSnapshot(String actor) {
        BackupSnapshot snapshot = new BackupSnapshot();
        snapshot.setCreatedAt(LocalDateTime.now());
        snapshot.setCreatedBy(actor);
        snapshot.setDevices(deviceRepository.findAll());
        snapshot.setAppVersions(appVersionRepository.findAll());
        snapshot.setCompatibilityRules(compatibilityRepository.findAll());
        snapshot.setUpdateSchedules(scheduleRepository.findAll());
        snapshot.setDeviceUpdateStates(deviceUpdateStateRepository.findAll());
        snapshot.setAuditLogs(auditLogRepository.findAllByOrderByIdAsc());

        String checksum = calculateChecksum(snapshot);
        snapshot.setChecksum(checksum);

        Path backupDir = Path.of("tools", "backups");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path filePath = backupDir.resolve("mdm-backup-" + timestamp + ".json").toAbsolutePath();

        try {
            Files.createDirectories(backupDir);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), snapshot);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "BACKUP_EXPORT_FAILED", ex.getMessage());
        }

        return BackupExportResponse.builder()
                .filePath(filePath.toString())
                .checksum(checksum)
                .exportedDevices(snapshot.getDevices().size())
                .exportedVersions(snapshot.getAppVersions().size())
                .exportedSchedules(snapshot.getUpdateSchedules().size())
                .exportedLifecycleStates(snapshot.getDeviceUpdateStates().size())
                .exportedAuditLogs(snapshot.getAuditLogs().size())
                .exportedCompatibilityRules(snapshot.getCompatibilityRules().size())
                .exportedBy(actor)
                .exportedAt(snapshot.getCreatedAt().toString())
                .build();
    }

    @Transactional
    public BackupImportResponse importSnapshot(BackupImportRequest request) {
        Path filePath = Path.of(request.getFilePath()).toAbsolutePath();
        if (!Files.exists(filePath)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BACKUP_FILE_NOT_FOUND", "Backup file not found");
        }

        BackupSnapshot snapshot;
        try {
            snapshot = objectMapper.readValue(filePath.toFile(), BackupSnapshot.class);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BACKUP_IMPORT_INVALID", ex.getMessage());
        }

        String expectedChecksum = calculateChecksum(snapshot);
        if (snapshot.getChecksum() == null || !snapshot.getChecksum().equals(expectedChecksum)) {
            throw new ApiException(HttpStatus.CONFLICT, "BACKUP_CHECKSUM_MISMATCH", "Backup checksum validation failed");
        }

        if (request.isReplaceExisting()) {
            if (auditLogRepository.count() > 0) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "AUDIT_IMMUTABLE",
                        "Audit log is immutable. Use replaceExisting=false to append restored audit events."
                );
            }
            deviceUpdateStateRepository.deleteAllInBatch();
            scheduleRepository.deleteAllInBatch();
            compatibilityRepository.deleteAllInBatch();
            deviceRepository.deleteAllInBatch();
            appVersionRepository.deleteAllInBatch();
        }

        appVersionRepository.saveAll(snapshot.getAppVersions());
        compatibilityRepository.saveAll(snapshot.getCompatibilityRules());
        scheduleRepository.saveAll(snapshot.getUpdateSchedules());
        deviceRepository.saveAll(snapshot.getDevices());
        deviceUpdateStateRepository.saveAll(snapshot.getDeviceUpdateStates());

        List<AuditLog> sortedAudit = snapshot.getAuditLogs().stream()
                .sorted(Comparator.comparing(AuditLog::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AuditLog::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        for (AuditLog auditLog : sortedAudit) {
            auditLogService.appendRecoveredEvent(auditLog);
        }

        return BackupImportResponse.builder()
                .importedFrom(filePath.toString())
                .replacedExistingData(request.isReplaceExisting())
                .importedDevices(snapshot.getDevices().size())
                .importedVersions(snapshot.getAppVersions().size())
                .importedSchedules(snapshot.getUpdateSchedules().size())
                .importedLifecycleStates(snapshot.getDeviceUpdateStates().size())
                .importedAuditLogs(snapshot.getAuditLogs().size())
                .importedCompatibilityRules(snapshot.getCompatibilityRules().size())
                .build();
    }

    private String calculateChecksum(BackupSnapshot snapshot) {
        String originalChecksum = snapshot.getChecksum();
        snapshot.setChecksum(null);
        try {
            byte[] payload = objectMapper.writeValueAsBytes(snapshot);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to calculate backup checksum", ex);
        } finally {
            snapshot.setChecksum(originalChecksum);
        }
    }
}
