package com.digitalbank.transfer.messaging;

/** Mensagem inválida: retentar não resolve, vai direto para a DLQ (e para a auditoria). */
public class NonRetryableMessageException extends RuntimeException {

    public NonRetryableMessageException(String message) {
        super(message);
    }
}
