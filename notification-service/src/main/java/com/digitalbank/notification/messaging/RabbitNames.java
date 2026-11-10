package com.digitalbank.notification.messaging;

/** Nomes da topologia RabbitMQ (idênticos nos três serviços). */
public final class RabbitNames {

    private RabbitNames() {
    }

    public static final String SAGA_EXCHANGE = "bank.saga.exchange";
    public static final String DLX = "bank.dlx";
    public static final String ERROR_PREFIX = "error.";
    public static final String DLQ_SUFFIX = ".dlq";

    public static final String TRANSFER_COMPLETED_QUEUE = "notification.transfer-completed.q";
    public static final String TRANSFER_COMPLETED_RK = "transfer.completed";
    public static final String TRANSFER_COMPLETED_DLQ = TRANSFER_COMPLETED_QUEUE + DLQ_SUFFIX;
}
