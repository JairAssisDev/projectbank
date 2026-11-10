package com.digitalbank.transfer.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.digitalbank.transfer.command.TransferSagaOrchestrator;
import com.digitalbank.transfer.messaging.contract.SagaReply;

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
