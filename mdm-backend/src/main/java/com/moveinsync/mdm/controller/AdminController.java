package com.moveinsync.mdm.controller;

import com.moveinsync.mdm.dto.AdminScheduleRequest;
import com.moveinsync.mdm.dto.AuditIntegrityResponse;
import com.moveinsync.mdm.dto.BackupExportResponse;
import com.moveinsync.mdm.dto.BackupImportRequest;
import com.moveinsync.mdm.dto.BackupImportResponse;
import com.moveinsync.mdm.dto.DashboardResponse;
import com.moveinsync.mdm.dto.ScheduleApprovalRequest;
import com.moveinsync.mdm.dto.ScheduleLedgerEntry;
import com.moveinsync.mdm.model.AppVersion;
import com.moveinsync.mdm.model.AuditLog;
import com.moveinsync.mdm.model.UpdateSchedule;
import com.moveinsync.mdm.model.VersionCompatibility;
import com.moveinsync.mdm.service.AdminService;
import com.moveinsync.mdm.service.BackupRecoveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final BackupRecoveryService backupRecoveryService;

    @PostMapping("/versions")
    @PreAuthorize("hasAnyRole('ADMIN','RELEASE_MANAGER')")
    public ResponseEntity<AppVersion> createVersion(@Valid @RequestBody AppVersion version, Authentication authentication) {
        return ResponseEntity.ok(adminService.createVersion(version, authentication.getName()));
    }

    @PostMapping("/schedules")
    @PreAuthorize("hasAnyRole('ADMIN','RELEASE_MANAGER')")
    public ResponseEntity<UpdateSchedule> scheduleUpdate(
            @Valid @RequestBody AdminScheduleRequest scheduleRequest,
            Authentication authentication
    ) {
        return ResponseEntity.ok(adminService.createSchedule(scheduleRequest, authentication.getName()));
    }

    @PostMapping("/compatibility")
    @PreAuthorize("hasAnyRole('ADMIN','RELEASE_MANAGER')")
    public ResponseEntity<VersionCompatibility> createCompatibility(
            @Valid @RequestBody VersionCompatibility compatibility,
            Authentication authentication
    ) {
        return ResponseEntity.ok(adminService.createCompatibilityRule(compatibility, authentication.getName()));
    }

    @GetMapping("/schedules")
    @PreAuthorize("hasAnyRole('ADMIN','RELEASE_MANAGER','PRODUCT_HEAD')")
    public ResponseEntity<List<UpdateSchedule>> getSchedules() {
        return ResponseEntity.ok(adminService.listSchedules());
    }

    @GetMapping("/schedules/ledger")
    @PreAuthorize("hasAnyRole('ADMIN','RELEASE_MANAGER','PRODUCT_HEAD')")
    public ResponseEntity<List<ScheduleLedgerEntry>> getScheduleLedger() {
        return ResponseEntity.ok(adminService.getScheduleLedger());
    }

    @PostMapping("/schedules/{scheduleId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCT_HEAD')")
    public ResponseEntity<UpdateSchedule> approveSchedule(
            @PathVariable Long scheduleId,
            @RequestBody(required = false) ScheduleApprovalRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(adminService.approveSchedule(
                scheduleId,
                authentication.getName(),
                request != null ? request.getComment() : null
        ));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN','RELEASE_MANAGER','PRODUCT_HEAD')")
    public ResponseEntity<DashboardResponse> getDashboard(
            @RequestParam(name = "inactiveMinutes", defaultValue = "60") int inactiveMinutes
    ) {
        return ResponseEntity.ok(adminService.getDashboard(inactiveMinutes));
    }

    @GetMapping("/devices/count")
    @PreAuthorize("hasAnyRole('ADMIN','RELEASE_MANAGER','PRODUCT_HEAD')")
    public ResponseEntity<Long> getDeviceCountByRegionAndVersion(
            @RequestParam String region,
            @RequestParam String version
    ) {
        return ResponseEntity.ok(adminService.countDevicesByRegionAndVersion(region, version));
    }

    @GetMapping("/audit/{imeiNumber}")
    @PreAuthorize("hasAnyRole('ADMIN','RELEASE_MANAGER','PRODUCT_HEAD')")
    public ResponseEntity<List<AuditLog>> getAuditTimeline(@PathVariable String imeiNumber) {
        return ResponseEntity.ok(adminService.getDeviceTimeline(imeiNumber));
    }

    @GetMapping("/audit/verify")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCT_HEAD')")
    public ResponseEntity<AuditIntegrityResponse> verifyAuditIntegrity() {
        return ResponseEntity.ok(adminService.verifyAuditIntegrity());
    }

    @PostMapping("/cache/app-versions/evict")
    @PreAuthorize("hasAnyRole('ADMIN','RELEASE_MANAGER')")
    public ResponseEntity<String> evictVersionCache() {
        adminService.evictVersionCache();
        return ResponseEntity.ok("App version cache evicted");
    }

    @PostMapping("/backup/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BackupExportResponse> exportBackup(Authentication authentication) {
        return ResponseEntity.ok(backupRecoveryService.exportSnapshot(authentication.getName()));
    }

    @PostMapping("/backup/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BackupImportResponse> importBackup(@RequestBody @Valid BackupImportRequest request) {
        return ResponseEntity.ok(backupRecoveryService.importSnapshot(request));
    }
}
