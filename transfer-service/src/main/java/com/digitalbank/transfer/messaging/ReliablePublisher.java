package com.digitalbank.transfer.messaging;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publica e AGUARDA o publisher confirm do broker (e detecta mensagens não roteáveis).
 * Bloquear aqui é barato: o chamador roda em virtual thread.
 */
@Component
public class ReliablePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final long confirmTimeoutMs;

    public ReliablePublisher(RabbitTemplate rabbitTemplate,
                             @Value("${bank.messaging.confirm-timeout-ms:5000}") long confirmTimeoutMs) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeoutMs = confirmTimeoutMs;
    }

    public void publish(String exchange, String routingKey, Message message, String correlationId) {
        var correlation = new CorrelationData(correlationId);
        rabbitTemplate.send(exchange, routingKey, message, correlation);
        try {
            var confirm = correlation.getFuture().get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new MessagePublishException("Broker recusou (NACK) mensagem para " + routingKey + ": "
                        + confirm.getReason());
            }
            if (correlation.getReturned() != null) {
                throw new MessagePublishException("Mensagem não roteável para " + routingKey + ": "
                        + correlation.getReturned().getReplyText());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessagePublishException("Interrompido aguardando confirmação do broker", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new MessagePublishException("Sem confirmação do broker para " + routingKey, e);
        }
    }

    public static class MessagePublishException extends RuntimeException {

        public MessagePublishException(String message) {
            super(message);
        }

        public MessagePublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
