package com.moveinsync.mdm.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_versions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppVersion implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "version_code", nullable = false, unique = true)
    private String versionCode;

    @Column(name = "version_name", nullable = false)
    private String versionName;

    @Column(name = "release_date")
    private LocalDateTime releaseDate;

    @Column(name = "supported_os_range")
    private String supportedOsRange;

    @Column(name = "customization_tag")
    private String customizationTag; // e.g., Client/Region Specific

    @Column(name = "is_mandatory")
    private boolean isMandatory;
}
