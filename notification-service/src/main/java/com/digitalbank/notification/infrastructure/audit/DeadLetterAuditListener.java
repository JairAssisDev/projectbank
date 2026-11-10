package com.digitalbank.notification.infrastructure.audit;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.stereotype.Component;

import com.digitalbank.notification.infrastructure.messaging.config.RabbitNames;

/** Consome as DLQs deste serviço e persiste cada falha definitiva em error_audit. */
@Component
public class DeadLetterAuditListener {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterAuditListener.class);
    private static final int MAX_STACKTRACE = 8000;

    private final ErrorAuditRepository repository;
    private final Clock clock;

    public DeadLetterAuditListener(ErrorAuditRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @RabbitListener(queues = RabbitNames.TRANSFER_COMPLETED_DLQ, containerFactory = "dlqListenerContainerFactory")
    public void onDeadLetter(Message message) {
        var props = message.getMessageProperties();
        Map<String, Object> headers = props.getHeaders();
        var audit = new ErrorAudit(
                props.getConsumerQueue(),
                header(headers, RepublishMessageRecoverer.X_ORIGINAL_EXCHANGE),
                firstNonNull(header(headers, RepublishMessageRecoverer.X_ORIGINAL_ROUTING_KEY),
                        props.getReceivedRoutingKey()),
                props.getMessageId(),
                new String(message.getBody(), StandardCharsets.UTF_8),
                firstNonNull(header(headers, RepublishMessageRecoverer.X_EXCEPTION_MESSAGE),
                        "Rejeitada pelo broker/container (sem exceção registrada)"),
                truncate(header(headers, RepublishMessageRecoverer.X_EXCEPTION_STACKTRACE)),
                clock.instant());
        repository.save(audit);
        log.error("Falha definitiva auditada [{}] fila={} rk={} erro={}", audit.getId(), audit.getDeadLetterQueue(),
                audit.getOriginalRoutingKey(), audit.getExceptionMessage());
    }

    private static String header(Map<String, Object> headers, String name) {
        return Objects.toString(headers.get(name), null);
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_STACKTRACE ? value : value.substring(0, MAX_STACKTRACE);
    }
}
