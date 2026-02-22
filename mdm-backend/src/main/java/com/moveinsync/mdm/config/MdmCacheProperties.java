package com.moveinsync.mdm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mdm.cache")
@Getter
@Setter
public class MdmCacheProperties {
    private long appVersionTtlMinutes = 10;
    private long appVersionMaxSize = 1000;
}
