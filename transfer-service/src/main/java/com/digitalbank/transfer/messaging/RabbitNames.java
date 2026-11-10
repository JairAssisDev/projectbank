package com.digitalbank.transfer.messaging;

/** Nomes da topologia RabbitMQ (idênticos nos três serviços). */
public final class RabbitNames {

    private RabbitNames() {
    }

    public static final String SAGA_EXCHANGE = "bank.saga.exchange";
    public static final String DLX = "bank.dlx";
    public static final String ERROR_PREFIX = "error.";
    public static final String DLQ_SUFFIX = ".dlq";

    // Comandos emitidos para o account-service
    public static final String DEBIT_RK = "account.debit";
    public static final String CREDIT_RK = "account.credit";
    public static final String REFUND_RK = "account.refund";

    // Respostas consumidas por este serviço (orquestrador)
    public static final String REPLY_QUEUE = "transfer.saga.reply.q";
    public static final String REPLY_BINDING = "transfer.reply.#";
    public static final String REPLY_DLQ = REPLY_QUEUE + DLQ_SUFFIX;

    // Evento de domínio publicado ao final da Saga
    public static final String TRANSFER_COMPLETED_RK = "transfer.completed";
}
