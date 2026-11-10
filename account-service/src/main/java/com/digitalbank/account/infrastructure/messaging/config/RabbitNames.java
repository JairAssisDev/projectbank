package com.digitalbank.account.infrastructure.messaging.config;

/** Nomes da topologia RabbitMQ (idênticos nos três serviços). */
public final class RabbitNames {

    private RabbitNames() {
    }

    /** Exchange topic por onde trafegam comandos, respostas e eventos da Saga. */
    public static final String SAGA_EXCHANGE = "bank.saga.exchange";
    /** Dead Letter Exchange: recebe mensagens com falha definitiva (routing key "error.&lt;original&gt;"). */
    public static final String DLX = "bank.dlx";
    public static final String ERROR_PREFIX = "error.";
    public static final String DLQ_SUFFIX = ".dlq";

    // Comandos consumidos por este serviço
    public static final String DEBIT_QUEUE = "account.debit.command.q";
    public static final String DEBIT_RK = "account.debit";
    public static final String CREDIT_QUEUE = "account.credit.command.q";
    public static final String CREDIT_RK = "account.credit";
    public static final String REFUND_QUEUE = "account.refund.command.q";
    public static final String REFUND_RK = "account.refund";

    public static final String DEBIT_DLQ = DEBIT_QUEUE + DLQ_SUFFIX;
    public static final String CREDIT_DLQ = CREDIT_QUEUE + DLQ_SUFFIX;
    public static final String REFUND_DLQ = REFUND_QUEUE + DLQ_SUFFIX;

    /** Respostas para o orquestrador: transfer.reply.{debit|credit|refund}. */
    public static final String REPLY_RK_PREFIX = "transfer.reply.";
}
