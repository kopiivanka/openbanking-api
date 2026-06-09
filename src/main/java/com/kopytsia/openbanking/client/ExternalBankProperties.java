package com.kopytsia.openbanking.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openbanking.external")
public record ExternalBankProperties(
        String baseUrl,
        int connectTimeoutMs,
        int readTimeoutMs
) {
}
