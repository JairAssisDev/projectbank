package com.digitalbank.transfer.domain.saga;

/**
 * Passo da Saga executado por um participante (account-service).
 * Pertence ao domínio; o contrato de mensagem ({@code AccountOperation}) é traduzido para ele pelo orquestrador,
 * assim a máquina de estados não depende da camada de mensageria.
 */
public enum SagaStep {
    DEBIT,
    CREDIT,
    REFUND
}
