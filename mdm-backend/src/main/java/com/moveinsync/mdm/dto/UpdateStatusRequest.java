package com.moveinsync.mdm.dto;

import com.moveinsync.mdm.model.UpdateLifecycleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStatusRequest {
    @NotBlank
    private String imeiNumber;

    @NotNull
    private UpdateLifecycleStatus status;

    private Long scheduleId;
    private String fromVersion;
    private String toVersion;
    private String failureStage;
    private String failureReason;
}
