package com.digitalbank.notification.infrastructure.messaging.exception;

/** Mensagem inválida: retentar não resolve, vai direto para a DLQ (e para a auditoria). */
public class NonRetryableMessageException extends RuntimeException {

    public NonRetryableMessageException(String message) {
        super(message);
    }
}
