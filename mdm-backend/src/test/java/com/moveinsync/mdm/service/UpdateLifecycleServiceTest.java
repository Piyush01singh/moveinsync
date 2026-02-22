package com.moveinsync.mdm.service;

import com.moveinsync.mdm.config.MdmLifecycleProperties;
import com.moveinsync.mdm.dto.UpdateStatusRequest;
import com.moveinsync.mdm.exception.ApiException;
import com.moveinsync.mdm.model.Device;
import com.moveinsync.mdm.model.DeviceUpdateState;
import com.moveinsync.mdm.model.UpdateLifecycleStatus;
import com.moveinsync.mdm.repository.DeviceRepository;
import com.moveinsync.mdm.repository.DeviceUpdateStateRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateLifecycleServiceTest {

    @Mock
    private DeviceUpdateStateRepository deviceUpdateStateRepository;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private AuditLogService auditLogService;

    private UpdateLifecycleService updateLifecycleService;

    @BeforeEach
    void setUp() {
        MdmLifecycleProperties lifecycleProperties = new MdmLifecycleProperties();
        lifecycleProperties.setMaxRetries(2);
        updateLifecycleService = new UpdateLifecycleService(
                deviceUpdateStateRepository,
                deviceRepository,
                auditLogService,
                lifecycleProperties,
                new VersionComparator(),
                new SimpleMeterRegistry()
        );
    }

    @Test
    void processDeviceStatus_ShouldRejectOutOfOrderWithoutSession() {
        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setImeiNumber("IMEI-1");
        request.setStatus(UpdateLifecycleStatus.DOWNLOAD_STARTED);
        when(deviceUpdateStateRepository.findByDeviceImei("IMEI-1")).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> updateLifecycleService.processDeviceStatus(request));
        assertEquals("UPDATE_SESSION_NOT_FOUND", exception.getCode());
    }

    @Test
    void processDeviceStatus_ShouldAllowValidTransition() {
        DeviceUpdateState state = state("IMEI-2", UpdateLifecycleStatus.DEVICE_NOTIFIED, "4.0", "4.1", 0);
        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setImeiNumber("IMEI-2");
        request.setStatus(UpdateLifecycleStatus.DOWNLOAD_STARTED);

        when(deviceUpdateStateRepository.findByDeviceImei("IMEI-2")).thenReturn(Optional.of(state));
        when(deviceUpdateStateRepository.save(any(DeviceUpdateState.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceUpdateState updated = updateLifecycleService.processDeviceStatus(request);

        assertEquals(UpdateLifecycleStatus.DOWNLOAD_STARTED, updated.getCurrentStatus());
        assertNotNull(updated.getDownloadStartedAt());
        verify(auditLogService).logLifecycleEvent(
                eq("IMEI-2"),
                eq(UpdateLifecycleStatus.DOWNLOAD_STARTED),
                eq("DEVICE"),
                eq("DEVICE_API"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void processDeviceStatus_ShouldCaptureFailureAndAllowRetryFromSameStage() {
        DeviceUpdateState state = state("IMEI-3", UpdateLifecycleStatus.DOWNLOAD_STARTED, "4.0", "4.1", 0);
        when(deviceUpdateStateRepository.findByDeviceImei("IMEI-3")).thenReturn(Optional.of(state));
        when(deviceUpdateStateRepository.save(any(DeviceUpdateState.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateStatusRequest failed = new UpdateStatusRequest();
        failed.setImeiNumber("IMEI-3");
        failed.setStatus(UpdateLifecycleStatus.FAILED);
        failed.setFailureStage("DOWNLOAD");
        failed.setFailureReason("Network timeout");
        updateLifecycleService.processDeviceStatus(failed);

        assertEquals(UpdateLifecycleStatus.FAILED, state.getCurrentStatus());
        assertEquals(1, state.getRetryCount());
        assertEquals("DOWNLOAD", state.getLastFailureStage());

        UpdateStatusRequest invalidRetry = new UpdateStatusRequest();
        invalidRetry.setImeiNumber("IMEI-3");
        invalidRetry.setStatus(UpdateLifecycleStatus.INSTALLATION_STARTED);
        ApiException invalid = assertThrows(ApiException.class, () -> updateLifecycleService.processDeviceStatus(invalidRetry));
        assertEquals("INVALID_LIFECYCLE_TRANSITION", invalid.getCode());

        UpdateStatusRequest validRetry = new UpdateStatusRequest();
        validRetry.setImeiNumber("IMEI-3");
        validRetry.setStatus(UpdateLifecycleStatus.DOWNLOAD_STARTED);
        DeviceUpdateState retryState = updateLifecycleService.processDeviceStatus(validRetry);
        assertEquals(UpdateLifecycleStatus.DOWNLOAD_STARTED, retryState.getCurrentStatus());
    }

    @Test
    void processDeviceStatus_ShouldRequireFailureStage() {
        DeviceUpdateState state = state("IMEI-4", UpdateLifecycleStatus.DOWNLOAD_STARTED, "4.0", "4.1", 0);
        when(deviceUpdateStateRepository.findByDeviceImei("IMEI-4")).thenReturn(Optional.of(state));

        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setImeiNumber("IMEI-4");
        request.setStatus(UpdateLifecycleStatus.FAILED);
        request.setFailureReason("Disk full");

        ApiException exception = assertThrows(ApiException.class, () -> updateLifecycleService.processDeviceStatus(request));
        assertEquals("FAILURE_STAGE_REQUIRED", exception.getCode());
    }

    @Test
    void processDeviceStatus_ShouldUpdateDeviceVersionOnInstallComplete() {
        DeviceUpdateState state = state("IMEI-5", UpdateLifecycleStatus.INSTALLATION_STARTED, "4.0", "4.1", 0);
        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setImeiNumber("IMEI-5");
        request.setStatus(UpdateLifecycleStatus.INSTALLATION_COMPLETED);

        Device device = new Device();
        device.setImeiNumber("IMEI-5");
        device.setAppVersion("4.0");

        when(deviceUpdateStateRepository.findByDeviceImei("IMEI-5")).thenReturn(Optional.of(state));
        when(deviceUpdateStateRepository.save(any(DeviceUpdateState.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deviceRepository.findByImeiNumber("IMEI-5")).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        updateLifecycleService.processDeviceStatus(request);

        assertEquals("4.1", device.getAppVersion());
    }

    private DeviceUpdateState state(
            String imei,
            UpdateLifecycleStatus status,
            String fromVersion,
            String toVersion,
            int retryCount
    ) {
        DeviceUpdateState state = new DeviceUpdateState();
        state.setDeviceImei(imei);
        state.setCurrentStatus(status);
        state.setFromVersion(fromVersion);
        state.setToVersion(toVersion);
        state.setRetryCount(retryCount);
        state.setMaxRetries(2);
        return state;
    }
}
