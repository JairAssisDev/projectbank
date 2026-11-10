package com.digitalbank.notification.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.digitalbank.notification.domain.model.NotificationLog;

public record NotificationResponse(UUID id, UUID transferId, UUID recipientAccountId, NotificationLog.Kind kind,
                                   String message, Instant sentAt) {

    public static NotificationResponse from(NotificationLog n) {
        return new NotificationResponse(n.getId(), n.getTransferId(), n.getRecipientAccountId(), n.getKind(),
                n.getMessage(), n.getSentAt());
    }
}
