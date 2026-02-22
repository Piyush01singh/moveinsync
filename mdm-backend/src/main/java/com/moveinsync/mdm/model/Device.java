package com.moveinsync.mdm.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "devices",
        indexes = {
                @Index(name = "idx_devices_app_version", columnList = "app_version"),
                @Index(name = "idx_devices_region", columnList = "location_region"),
                @Index(name = "idx_devices_last_open", columnList = "last_app_open_time"),
                @Index(name = "idx_devices_group", columnList = "device_group")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Device {

    @Id
    @Column(name = "imei_number", unique = true, nullable = false)
    private String imeiNumber;

    @Column(name = "app_version", nullable = false)
    private String appVersion;

    @Column(name = "device_os")
    private String deviceOs;

    @Column(name = "device_model")
    private String deviceModel;

    @Column(name = "last_app_open_time")
    private LocalDateTime lastAppOpenTime;

    @Column(name = "location_region", nullable = false)
    private String locationRegion;

    @Column(name = "customization_tag")
    private String customizationTag;

    @Column(name = "device_group")
    private String deviceGroup;
}
