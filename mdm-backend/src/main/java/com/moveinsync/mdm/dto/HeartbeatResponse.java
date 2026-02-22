package com.moveinsync.mdm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatResponse {
    private String imeiNumber;
    private boolean updateAvailable;
    private Long scheduleId;
    private String fromVersion;
    private String targetVersion;
    private boolean isMandatory;
    private String message;
}
