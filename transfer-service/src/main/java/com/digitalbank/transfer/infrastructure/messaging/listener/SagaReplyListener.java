package com.digitalbank.transfer.infrastructure.messaging.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.digitalbank.transfer.application.saga.TransferSagaOrchestrator;
import com.digitalbank.transfer.infrastructure.messaging.config.RabbitNames;
import com.digitalbank.transfer.infrastructure.messaging.contract.SagaReply;
import com.digitalbank.transfer.infrastructure.messaging.exception.NonRetryableMessageException;

@Component
public class SagaReplyListener {

    private final TransferSagaOrchestrator orchestrator;

    public SagaReplyListener(TransferSagaOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @RabbitListener(queues = RabbitNames.REPLY_QUEUE)
    public void onReply(SagaReply reply) {
        if (reply == null || reply.transferId() == null || reply.operation() == null) {
            throw new NonRetryableMessageException("Resposta da Saga inválida: " + reply);
        }
        orchestrator.onReply(reply);
    }
}
