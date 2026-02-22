package com.moveinsync.mdm.controller;

import com.moveinsync.mdm.dto.UpdateStatusRequest;
import com.moveinsync.mdm.model.DeviceUpdateState;
import com.moveinsync.mdm.service.UpdateLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/update")
@RequiredArgsConstructor
public class UpdateLifecycleController {

    private final UpdateLifecycleService updateLifecycleService;

    @PostMapping("/status")
    public ResponseEntity<Map<String, Object>> updateStatus(@Valid @RequestBody UpdateStatusRequest request) {
        DeviceUpdateState state = updateLifecycleService.processDeviceStatus(request);
        return ResponseEntity.accepted().body(Map.of(
                "message", "Lifecycle status persisted",
                "currentStatus", state.getCurrentStatus().name(),
                "retryCount", state.getRetryCount(),
                "maxRetries", state.getMaxRetries()
        ));
    }
}
