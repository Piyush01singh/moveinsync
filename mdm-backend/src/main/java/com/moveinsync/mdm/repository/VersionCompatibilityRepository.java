package com.moveinsync.mdm.repository;

import com.moveinsync.mdm.model.VersionCompatibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VersionCompatibilityRepository extends JpaRepository<VersionCompatibility, Long> {
    Optional<VersionCompatibility> findByFromVersionAndToVersion(String fromVersion, String toVersion);
}
