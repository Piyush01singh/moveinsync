package com.moveinsync.mdm.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "update_schedules",
        indexes = {
                @Index(name = "idx_schedules_from_version", columnList = "from_version"),
                @Index(name = "idx_schedules_region", columnList = "target_region"),
                @Index(name = "idx_schedules_scheduled_time", columnList = "scheduled_time"),
                @Index(name = "idx_schedules_device_group", columnList = "target_device_group")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_version")
    private String fromVersion;

    @Column(name = "to_version", nullable = false)
    private String toVersion;

    @Column(name = "target_region")
    private String targetRegion;

    @Column(name = "customization_tag")
    private String customizationTag;

    @Column(name = "target_device_group")
    private String targetDeviceGroup;

    @Column(name = "scheduled_time")
    private LocalDateTime scheduledTime;

    @Column(name = "is_immediate")
    private boolean isImmediate;

    @Column(name = "rollout_percentage")
    private Integer rolloutPercentage;

    @Column(name = "approval_required")
    private boolean approvalRequired;

    @Column(name = "approval_status")
    private String approvalStatus;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "approved_by")
    private String approvedBy;
}
