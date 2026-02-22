package com.moveinsync.mdm.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminScheduleRequest {
    @NotBlank(message = "From version is required for safe rollout targeting")
    private String fromVersion;

    @NotBlank(message = "To version is required")
    private String toVersion;

    private String targetRegion;
    private String customizationTag;
    private String targetDeviceGroup;
    private LocalDateTime scheduledTime;
    private boolean immediate;

    @Min(value = 1, message = "Rollout percentage must be between 1 and 100")
    @Max(value = 100, message = "Rollout percentage must be between 1 and 100")
    private Integer rolloutPercentage;
}
