package com.moveinsync.mdm.repository;

import com.moveinsync.mdm.model.AppVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppVersionRepository extends JpaRepository<AppVersion, String> {
    Optional<AppVersion> findByVersionCode(String versionCode);

    boolean existsByVersionCode(String versionCode);
}
