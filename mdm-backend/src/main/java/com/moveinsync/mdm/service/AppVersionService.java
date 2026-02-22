package com.moveinsync.mdm.service;

import com.moveinsync.mdm.model.AppVersion;
import com.moveinsync.mdm.repository.AppVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppVersionService {

    private final AppVersionRepository appVersionRepository;

    @Cacheable(value = "appVersions", key = "#versionCode")
    public Optional<AppVersion> getVersionByCode(String versionCode) {
        log.info("Fetching AppVersion from DB for code: {}", versionCode);
        return appVersionRepository.findByVersionCode(versionCode);
    }
}
