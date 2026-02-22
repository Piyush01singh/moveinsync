package com.moveinsync.mdm.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;

@Value
@Builder
public class ApiErrorResponse {
    LocalDateTime timestamp;
    int status;
    String error;
    String code;
    Object message;
    String path;
    Map<String, String> details;
}
