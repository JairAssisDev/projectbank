package com.digitalbank.notification.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Registro de uma notificação enviada (único por transferência + destinatário → idempotência). */
@Entity
@Table(name = "notification_log")
public class NotificationLog {

    public enum Kind {
        TRANSFER_SENT,
        TRANSFER_RECEIVED
    }

    @Id
    private UUID id;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Column(name = "recipient_account_id", nullable = false)
    private UUID recipientAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Kind kind;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected NotificationLog() {
        // JPA
    }

    public NotificationLog(UUID transferId, UUID recipientAccountId, Kind kind, String message, Instant sentAt) {
        this.id = UUID.randomUUID();
        this.transferId = transferId;
        this.recipientAccountId = recipientAccountId;
        this.kind = kind;
        this.message = message;
        this.sentAt = sentAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public UUID getRecipientAccountId() {
        return recipientAccountId;
    }

    public Kind getKind() {
        return kind;
    }

    public String getMessage() {
        return message;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
