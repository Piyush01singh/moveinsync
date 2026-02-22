package com.moveinsync.mdm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mdm.lifecycle")
@Getter
@Setter
public class MdmLifecycleProperties {
    private int maxRetries = 3;
}
