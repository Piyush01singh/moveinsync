package com.moveinsync.mdm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "device_update_state",
        indexes = {
                @Index(name = "idx_state_schedule", columnList = "schedule_id"),
                @Index(name = "idx_state_status", columnList = "current_status"),
                @Index(name = "idx_state_updated", columnList = "last_updated_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceUpdateState {

    @Id
    @Column(name = "device_imei", nullable = false, unique = true)
    private String deviceImei;

    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "from_version")
    private String fromVersion;

    @Column(name = "to_version")
    private String toVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status")
    private UpdateLifecycleStatus currentStatus;

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "max_retries")
    private int maxRetries;

    @Column(name = "last_failure_stage")
    private String lastFailureStage;

    @Column(name = "last_failure_reason")
    private String lastFailureReason;

    @Column(name = "notification_sent_at")
    private LocalDateTime notificationSentAt;

    @Column(name = "download_started_at")
    private LocalDateTime downloadStartedAt;

    @Column(name = "download_completed_at")
    private LocalDateTime downloadCompletedAt;

    @Column(name = "installation_started_at")
    private LocalDateTime installationStartedAt;

    @Column(name = "installation_completed_at")
    private LocalDateTime installationCompletedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_updated_at")
    private LocalDateTime lastUpdatedAt;
}
