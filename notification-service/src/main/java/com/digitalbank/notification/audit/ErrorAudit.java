package com.digitalbank.notification.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Histórico de falhas definitivas de processamento de mensagens (conteúdo das DLQs). */
@Entity
@Table(name = "error_audit")
public class ErrorAudit {

    @Id
    private UUID id;

    @Column(name = "dead_letter_queue", length = 200)
    private String deadLetterQueue;

    @Column(name = "original_exchange", length = 200)
    private String originalExchange;

    @Column(name = "original_routing_key", length = 200)
    private String originalRoutingKey;

    @Column(name = "message_id", length = 100)
    private String messageId;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "exception_message", columnDefinition = "TEXT")
    private String exceptionMessage;

    @Column(columnDefinition = "TEXT")
    private String stacktrace;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ErrorAudit() {
        // JPA
    }

    public ErrorAudit(String deadLetterQueue, String originalExchange, String originalRoutingKey, String messageId,
                      String payload, String exceptionMessage, String stacktrace, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.deadLetterQueue = deadLetterQueue;
        this.originalExchange = originalExchange;
        this.originalRoutingKey = originalRoutingKey;
        this.messageId = messageId;
        this.payload = payload;
        this.exceptionMessage = exceptionMessage;
        this.stacktrace = stacktrace;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getDeadLetterQueue() {
        return deadLetterQueue;
    }

    public String getOriginalExchange() {
        return originalExchange;
    }

    public String getOriginalRoutingKey() {
        return originalRoutingKey;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getPayload() {
        return payload;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public String getStacktrace() {
        return stacktrace;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
