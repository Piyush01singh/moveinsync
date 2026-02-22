package com.moveinsync.mdm.service;

import com.moveinsync.mdm.dto.AdminScheduleRequest;
import com.moveinsync.mdm.exception.ApiException;
import com.moveinsync.mdm.model.AppVersion;
import com.moveinsync.mdm.model.UpdateSchedule;
import com.moveinsync.mdm.model.VersionCompatibility;
import com.moveinsync.mdm.repository.AppVersionRepository;
import com.moveinsync.mdm.repository.AuditLogRepository;
import com.moveinsync.mdm.repository.DeviceRepository;
import com.moveinsync.mdm.repository.DeviceUpdateStateRepository;
import com.moveinsync.mdm.repository.UpdateScheduleRepository;
import com.moveinsync.mdm.repository.VersionCompatibilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private AppVersionRepository appVersionRepository;
    @Mock
    private VersionCompatibilityRepository compatibilityRepository;
    @Mock
    private UpdateScheduleRepository updateScheduleRepository;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private DeviceUpdateStateRepository deviceUpdateStateRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private OsCompatibilityService osCompatibilityService;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
                appVersionRepository,
                compatibilityRepository,
                updateScheduleRepository,
                deviceRepository,
                auditLogRepository,
                deviceUpdateStateRepository,
                new VersionComparator(),
                auditLogService,
                new ConcurrentMapCacheManager("appVersions"),
                osCompatibilityService
        );
    }

    @Test
    void createSchedule_ShouldBlockDowngrade() {
        AdminScheduleRequest request = new AdminScheduleRequest();
        request.setFromVersion("4.5");
        request.setToVersion("4.3");
        request.setImmediate(true);
        request.setRolloutPercentage(100);

        when(appVersionRepository.findByVersionCode("4.5")).thenReturn(Optional.of(version("4.5", false)));
        when(appVersionRepository.findByVersionCode("4.3")).thenReturn(Optional.of(version("4.3", false)));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> adminService.createSchedule(request, "admin")
        );
        assertEquals("DOWNGRADE_BLOCKED", exception.getCode());
    }

    @Test
    void createSchedule_ShouldRequireApprovalForMandatoryTarget() {
        AdminScheduleRequest request = new AdminScheduleRequest();
        request.setFromVersion("4.0");
        request.setToVersion("4.2");
        request.setImmediate(true);
        request.setRolloutPercentage(100);

        when(appVersionRepository.findByVersionCode("4.0")).thenReturn(Optional.of(version("4.0", false)));
        when(appVersionRepository.findByVersionCode("4.2")).thenReturn(Optional.of(version("4.2", true)));
        when(compatibilityRepository.findByFromVersionAndToVersion("4.0", "4.2")).thenReturn(Optional.empty());
        when(updateScheduleRepository.save(any(UpdateSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateSchedule schedule = adminService.createSchedule(request, "admin");

        assertEquals("PENDING", schedule.getApprovalStatus());
    }

    @Test
    void createSchedule_ShouldBlockMandatoryIntermediate() {
        AdminScheduleRequest request = new AdminScheduleRequest();
        request.setFromVersion("3.8");
        request.setToVersion("4.3");
        request.setImmediate(true);
        request.setRolloutPercentage(100);

        VersionCompatibility compatibility = new VersionCompatibility();
        compatibility.setFromVersion("3.8");
        compatibility.setToVersion("4.3");
        compatibility.setMandatoryIntermediate("4.0");

        when(appVersionRepository.findByVersionCode("3.8")).thenReturn(Optional.of(version("3.8", false)));
        when(appVersionRepository.findByVersionCode("4.3")).thenReturn(Optional.of(version("4.3", false)));
        when(compatibilityRepository.findByFromVersionAndToVersion("3.8", "4.3"))
                .thenReturn(Optional.of(compatibility));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> adminService.createSchedule(request, "admin")
        );
        assertEquals("MANDATORY_INTERMEDIATE_REQUIRED", exception.getCode());
    }

    @Test
    void createVersion_ShouldRejectInvalidOsRange() {
        AppVersion request = version("6.0", false);
        request.setSupportedOsRange("android-version-any");

        when(osCompatibilityService.isSupportedRangeFormat("android-version-any")).thenReturn(false);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> adminService.createVersion(request, "admin")
        );
        assertEquals("INVALID_OS_RANGE", exception.getCode());
    }

    private AppVersion version(String code, boolean mandatory) {
        AppVersion appVersion = new AppVersion();
        appVersion.setVersionCode(code);
        appVersion.setVersionName(code);
        appVersion.setMandatory(mandatory);
        return appVersion;
    }
}
