package com.digitalbank.transfer.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import com.digitalbank.transfer.messaging.ReliablePublisher;

/**
 * Publica as mensagens pendentes do outbox no RabbitMQ (at-least-once).
 * Consumidores são idempotentes, então uma eventual republicação é inofensiva.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository repository;
    private final ReliablePublisher publisher;
    private final TransactionOperations tx;
    private final Clock clock;
    private final int batchSize;

    public OutboxRelay(OutboxRepository repository, ReliablePublisher publisher, TransactionOperations tx,
                       Clock clock, @Value("${bank.outbox.batch-size:100}") int batchSize) {
        this.repository = repository;
        this.publisher = publisher;
        this.tx = tx;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${bank.outbox.poll-interval-ms:200}")
    public void publishPending() {
        Integer published = tx.execute(status -> {
            int count = 0;
            for (OutboxMessage message : repository.lockNextBatch(batchSize)) {
                try {
                    publisher.publish(message.getExchange(), message.getRoutingKey(), toAmqp(message),
                            message.getId().toString());
                    message.markPublished(clock.instant());
                    count++;
                } catch (RuntimeException e) {
                    message.markFailed(e.getMessage());
                    log.warn("Falha ao publicar outbox {} ({}), nova tentativa no próximo ciclo: {}",
                            message.getId(), message.getRoutingKey(), e.getMessage());
                }
            }
            return count;
        });
        if (published != null && published > 0) {
            log.debug("Outbox: {} mensagem(ns) publicada(s)", published);
        }
    }

    static Message toAmqp(OutboxMessage message) {
        return MessageBuilder.withBody(message.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(message.getId().toString())
                .setCorrelationId(message.getAggregateId().toString())
                .setType(message.getMessageType())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
    }
}
