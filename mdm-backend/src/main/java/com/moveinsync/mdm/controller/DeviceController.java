package com.moveinsync.mdm.controller;

import com.moveinsync.mdm.dto.HeartbeatRequest;
import com.moveinsync.mdm.dto.HeartbeatResponse;
import com.moveinsync.mdm.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/device")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping("/heartbeat")
    public ResponseEntity<HeartbeatResponse> heartbeat(@Valid @RequestBody HeartbeatRequest request) {
        return ResponseEntity.ok(deviceService.processHeartbeat(request));
    }
}
