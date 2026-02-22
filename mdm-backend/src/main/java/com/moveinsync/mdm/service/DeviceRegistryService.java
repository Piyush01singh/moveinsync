package com.moveinsync.mdm.service;

import com.moveinsync.mdm.dto.HeartbeatRequest;
import com.moveinsync.mdm.model.Device;
import com.moveinsync.mdm.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DeviceRegistryService {

    private final DeviceRepository deviceRepository;

    @Transactional
    public Device upsertFromHeartbeat(HeartbeatRequest request) {
        Device device = deviceRepository.findByImeiNumber(request.getImeiNumber()).orElse(new Device());
        device.setImeiNumber(request.getImeiNumber());
        device.setAppVersion(request.getAppVersion());
        device.setDeviceOs(request.getDeviceOs());
        device.setDeviceModel(request.getDeviceModel());
        device.setLocationRegion(request.getLocationRegion());
        device.setCustomizationTag(request.getCustomizationTag());
        device.setDeviceGroup(request.getDeviceGroup());
        device.setLastAppOpenTime(LocalDateTime.now());
        return deviceRepository.save(device);
    }
}
