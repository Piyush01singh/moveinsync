package com.moveinsync.mdm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HeartbeatRequest {
    @NotBlank(message = "IMEI Number is required")
    private String imeiNumber;
    
    @NotBlank(message = "App Version is required")
    private String appVersion;
    
    private String deviceOs;
    private String deviceModel;
    
    @NotBlank(message = "Location Region is required")
    private String locationRegion;

    private String customizationTag;
    private String deviceGroup;
}
