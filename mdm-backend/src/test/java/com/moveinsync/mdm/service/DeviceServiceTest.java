package com.moveinsync.mdm.service;

import com.moveinsync.mdm.dto.HeartbeatRequest;
import com.moveinsync.mdm.dto.HeartbeatResponse;
import com.moveinsync.mdm.model.AppVersion;
import com.moveinsync.mdm.model.Device;
import com.moveinsync.mdm.model.UpdateSchedule;
import com.moveinsync.mdm.repository.UpdateScheduleRepository;
import com.moveinsync.mdm.repository.VersionCompatibilityRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRegistryService deviceRegistryService;

    @Mock
    private AppVersionService appVersionService;

    @Mock
    private UpdateScheduleRepository scheduleRepository;

    @Mock
    private VersionCompatibilityRepository compatibilityRepository;

    @Mock
    private OsCompatibilityService osCompatibilityService;

    @Mock
    private UpdateLifecycleService updateLifecycleService;

    private DeviceService deviceService;
    private HeartbeatRequest request;

    @BeforeEach
    void setUp() {
        deviceService = new DeviceService(
                deviceRegistryService,
                appVersionService,
                scheduleRepository,
                compatibilityRepository,
                new VersionComparator(),
                osCompatibilityService,
                updateLifecycleService,
                new SimpleMeterRegistry()
        );

        request = new HeartbeatRequest();
        request.setImeiNumber("123456789012345");
        request.setAppVersion("4.0");
        request.setLocationRegion("Bangalore");
        request.setDeviceOs("Android 14");
    }

    @Test
    void processHeartbeat_NoUpdateAvailable() {
        when(deviceRegistryService.upsertFromHeartbeat(request)).thenReturn(deviceFrom(request));
        when(scheduleRepository.findApplicableSchedules(anyString(), anyString(), any(), any(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        HeartbeatResponse response = deviceService.processHeartbeat(request);

        assertFalse(response.isUpdateAvailable());
        assertEquals("App is up-to-date", response.getMessage());
        verify(updateLifecycleService, never()).registerScheduleAndNotification(any(Device.class), any(UpdateSchedule.class));
    }

    @Test
    void processHeartbeat_UpdateAvailable() {
        Device device = deviceFrom(request);
        when(deviceRegistryService.upsertFromHeartbeat(request)).thenReturn(device);

        UpdateSchedule schedule = new UpdateSchedule();
        schedule.setId(11L);
        schedule.setFromVersion("4.0");
        schedule.setToVersion("4.3");
        schedule.setTargetRegion("Bangalore");
        schedule.setImmediate(true);
        schedule.setRolloutPercentage(100);
        schedule.setApprovalRequired(false);

        AppVersion appVersion = new AppVersion();
        appVersion.setVersionCode("4.3");
        appVersion.setMandatory(false);
        appVersion.setSupportedOsRange("Android 12+");

        when(scheduleRepository.findApplicableSchedules(anyString(), anyString(), any(), any(), any(LocalDateTime.class)))
                .thenReturn(List.of(schedule));
        when(compatibilityRepository.findByFromVersionAndToVersion("4.0", "4.3")).thenReturn(Optional.empty());
        when(appVersionService.getVersionByCode("4.3")).thenReturn(Optional.of(appVersion));
        when(osCompatibilityService.isCompatible("Android 14", "Android 12+")).thenReturn(true);

        HeartbeatResponse response = deviceService.processHeartbeat(request);

        assertTrue(response.isUpdateAvailable());
        assertEquals(11L, response.getScheduleId());
        assertEquals("4.0", response.getFromVersion());
        assertEquals("4.3", response.getTargetVersion());
        assertEquals("Update available to version 4.3", response.getMessage());
        verify(updateLifecycleService).registerScheduleAndNotification(device, schedule);
    }

    @Test
    void processHeartbeat_DowngradeBlocked() {
        Device device = deviceFrom(request);
        device.setAppVersion("4.3");
        when(deviceRegistryService.upsertFromHeartbeat(request)).thenReturn(device);

        UpdateSchedule schedule = new UpdateSchedule();
        schedule.setFromVersion("4.3");
        schedule.setToVersion("4.0");
        schedule.setImmediate(true);
        schedule.setRolloutPercentage(100);
        schedule.setApprovalRequired(false);

        when(scheduleRepository.findApplicableSchedules(anyString(), anyString(), any(), any(), any(LocalDateTime.class)))
                .thenReturn(List.of(schedule));

        HeartbeatResponse response = deviceService.processHeartbeat(request);

        assertFalse(response.isUpdateAvailable());
        assertEquals("App is up-to-date", response.getMessage());
        verify(updateLifecycleService, never()).registerScheduleAndNotification(any(Device.class), any(UpdateSchedule.class));
    }

    @Test
    void processHeartbeat_TargetVersionMetadataMissing() {
        Device device = deviceFrom(request);
        when(deviceRegistryService.upsertFromHeartbeat(request)).thenReturn(device);

        UpdateSchedule schedule = new UpdateSchedule();
        schedule.setFromVersion("4.0");
        schedule.setToVersion("4.3");
        schedule.setImmediate(true);
        schedule.setRolloutPercentage(100);
        schedule.setApprovalRequired(false);

        when(scheduleRepository.findApplicableSchedules(anyString(), anyString(), any(), any(), any(LocalDateTime.class)))
                .thenReturn(List.of(schedule));
        when(compatibilityRepository.findByFromVersionAndToVersion("4.0", "4.3")).thenReturn(Optional.empty());
        when(appVersionService.getVersionByCode("4.3")).thenReturn(Optional.empty());

        HeartbeatResponse response = deviceService.processHeartbeat(request);

        assertFalse(response.isUpdateAvailable());
        assertEquals("Update blocked: target version metadata not found", response.getMessage());
        verify(updateLifecycleService, never()).registerScheduleAndNotification(any(Device.class), any(UpdateSchedule.class));
    }

    @Test
    void processHeartbeat_OsIncompatible() {
        Device device = deviceFrom(request);
        when(deviceRegistryService.upsertFromHeartbeat(request)).thenReturn(device);

        UpdateSchedule schedule = new UpdateSchedule();
        schedule.setFromVersion("4.0");
        schedule.setToVersion("4.3");
        schedule.setImmediate(true);
        schedule.setRolloutPercentage(100);
        schedule.setApprovalRequired(false);

        AppVersion appVersion = new AppVersion();
        appVersion.setVersionCode("4.3");
        appVersion.setMandatory(true);
        appVersion.setSupportedOsRange("Android 15+");

        when(scheduleRepository.findApplicableSchedules(anyString(), anyString(), any(), any(), any(LocalDateTime.class)))
                .thenReturn(List.of(schedule));
        when(compatibilityRepository.findByFromVersionAndToVersion("4.0", "4.3")).thenReturn(Optional.empty());
        when(appVersionService.getVersionByCode("4.3")).thenReturn(Optional.of(appVersion));
        when(osCompatibilityService.isCompatible("Android 14", "Android 15+")).thenReturn(false);

        HeartbeatResponse response = deviceService.processHeartbeat(request);

        assertFalse(response.isUpdateAvailable());
        assertTrue(response.getMessage().contains("does not satisfy supported range"));
        verify(updateLifecycleService, never()).registerScheduleAndNotification(any(Device.class), any(UpdateSchedule.class));
    }

    private Device deviceFrom(HeartbeatRequest request) {
        Device device = new Device();
        device.setImeiNumber(request.getImeiNumber());
        device.setAppVersion(request.getAppVersion());
        device.setLocationRegion(request.getLocationRegion());
        device.setCustomizationTag(request.getCustomizationTag());
        device.setDeviceGroup(request.getDeviceGroup());
        device.setDeviceOs(request.getDeviceOs());
        return device;
    }
}
