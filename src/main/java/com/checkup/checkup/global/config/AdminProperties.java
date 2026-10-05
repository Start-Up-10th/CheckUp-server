package com.checkup.checkup.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@ConfigurationProperties("checkup.admin")
public record AdminProperties(Set<Long> datagsmIds) {
    public AdminProperties {
        if (datagsmIds == null) {
            datagsmIds = Set.of();
        }
    }

    public boolean isAllowed(Long datagsmId) {
        return datagsmId != null && datagsmIds.contains(datagsmId);
    }
}
