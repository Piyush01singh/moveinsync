package com.moveinsync.mdm.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(MdmCacheProperties cacheProperties) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("appVersions");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .recordStats()
                .expireAfterWrite(cacheProperties.getAppVersionTtlMinutes(), TimeUnit.MINUTES)
                .maximumSize(cacheProperties.getAppVersionMaxSize()));
        return cacheManager;
    }
}
