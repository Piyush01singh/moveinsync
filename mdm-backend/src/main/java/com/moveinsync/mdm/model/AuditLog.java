package com.moveinsync.mdm.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_device_ts", columnList = "device_imei,timestamp"),
                @Index(name = "idx_audit_schedule_ts", columnList = "schedule_id,timestamp"),
                @Index(name = "idx_audit_action_ts", columnList = "action,timestamp")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_imei")
    private String deviceImei;

    @Column(name = "actor_id")
    private String actorId;

    @Column(name = "event_source")
    private String eventSource;

    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "action", nullable = false)
    private String action; // e.g. "Update Scheduled", "Download Started", "Failed"

    @Column(name = "from_version")
    private String fromVersion;

    @Column(name = "to_version")
    private String toVersion;

    @Column(name = "failure_stage")
    private String failureStage;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "details")
    private String details;

    @Column(name = "previous_hash", length = 64)
    private String previousHash;

    @Column(name = "event_hash", length = 64)
    private String eventHash;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;
}
