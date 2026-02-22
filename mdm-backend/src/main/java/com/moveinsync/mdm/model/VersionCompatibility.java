package com.moveinsync.mdm.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(
        name = "version_compatibility",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_version_compatibility_from_to",
                columnNames = {"from_version", "to_version"}
        )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VersionCompatibility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_version", nullable = false)
    private String fromVersion;

    @Column(name = "to_version", nullable = false)
    private String toVersion;

    @Column(name = "mandatory_intermediate")
    private String mandatoryIntermediate;

    public boolean hasMandatoryIntermediate() {
        return mandatoryIntermediate != null && !mandatoryIntermediate.isBlank();
    }
}
