package com.moveinsync.mdm.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuditIntegrityResponse {
    boolean valid;
    long checkedEvents;
    Long firstBrokenEventId;
    String message;
}
