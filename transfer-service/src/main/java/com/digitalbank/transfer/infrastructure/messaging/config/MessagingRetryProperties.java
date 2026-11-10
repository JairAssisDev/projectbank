package com.digitalbank.transfer.infrastructure.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Política de retry dos consumidores (backoff exponencial). */
@ConfigurationProperties("bank.messaging.retry")
public record MessagingRetryProperties(
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("1000") long initialIntervalMs,
        @DefaultValue("2.0") double multiplier,
        @DefaultValue("10000") long maxIntervalMs) {
}
