package com.moveinsync.mdm.repository;

import com.moveinsync.mdm.model.DeviceUpdateState;
import com.moveinsync.mdm.model.UpdateLifecycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceUpdateStateRepository extends JpaRepository<DeviceUpdateState, String> {
    Optional<DeviceUpdateState> findByDeviceImei(String deviceImei);

    List<DeviceUpdateState> findByCurrentStatus(UpdateLifecycleStatus currentStatus);

    long countByCurrentStatusNot(UpdateLifecycleStatus currentStatus);
}
