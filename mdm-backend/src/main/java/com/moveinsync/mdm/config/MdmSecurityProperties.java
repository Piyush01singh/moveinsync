package com.moveinsync.mdm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mdm.security")
@Getter
@Setter
public class MdmSecurityProperties {
    private String adminUsername;
    private String adminPassword;
    private String releaseManagerUsername;
    private String releaseManagerPassword;
    private String productHeadUsername;
    private String productHeadPassword;
    private String deviceApiKey;
}
