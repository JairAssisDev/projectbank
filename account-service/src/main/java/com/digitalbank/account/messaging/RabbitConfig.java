package com.digitalbank.account.messaging;

import static com.digitalbank.account.messaging.RabbitNames.*;

import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Binding.DestinationType;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Topologia + política de falhas:
 * <ol>
 *   <li>Listener falha → retry local com backoff exponencial (virtual thread dorme, custo ~zero).</li>
 *   <li>Esgotou as tentativas (ou exceção não-retentável) → {@link RepublishMessageRecoverer} republica na
 *       DLX com cabeçalhos x-exception-* → fila {@code <fila>.dlq}.</li>
 *   <li>Mensagens rejeitadas pelo broker/container também caem na DLX via argumento x-dead-letter-exchange.</li>
 *   <li>{@code DeadLetterAuditListener} consome as DLQs e grava na tabela error_audit.</li>
 * </ol>
 */
@Configuration
public class RabbitConfig {

    // ------------------------------------------------------------------ topologia

    @Bean
    TopicExchange sagaExchange() {
        return ExchangeBuilder.topicExchange(SAGA_EXCHANGE).durable(true).build();
    }

    @Bean
    TopicExchange deadLetterExchange() {
        return ExchangeBuilder.topicExchange(DLX).durable(true).build();
    }

    @Bean
    Declarables debitQueue() {
        return queueWithDlq(DEBIT_QUEUE, DEBIT_RK);
    }

    @Bean
    Declarables creditQueue() {
        return queueWithDlq(CREDIT_QUEUE, CREDIT_RK);
    }

    @Bean
    Declarables refundQueue() {
        return queueWithDlq(REFUND_QUEUE, REFUND_RK);
    }

    /** Fila durável + DLQ ligada à DLX pela chave "error.&lt;bindingKey&gt;". */
    static Declarables queueWithDlq(String queueName, String bindingKey) {
        String errorBindingKey = ERROR_PREFIX + bindingKey;
        String deadLetterRoutingKey = ERROR_PREFIX + bindingKey.replace(".#", "").replace(".*", "");
        Queue queue = QueueBuilder.durable(queueName)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(deadLetterRoutingKey)
                .build();
        Queue dlq = QueueBuilder.durable(queueName + DLQ_SUFFIX).build();
        return new Declarables(
                queue,
                dlq,
                new Binding(queueName, DestinationType.QUEUE, SAGA_EXCHANGE, bindingKey, null),
                new Binding(dlq.getName(), DestinationType.QUEUE, DLX, errorBindingKey, null));
    }

    // ------------------------------------------------------------------ conversão / consumidores

    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    RetryOperationsInterceptor retryInterceptor(RabbitTemplate rabbitTemplate, MessagingRetryProperties props) {
        var retryPolicy = new SimpleRetryPolicy(props.maxAttempts(),
                Map.of(MessageConversionException.class, false,
                        NonRetryableMessageException.class, false),
                true,   // inspeciona a cadeia de causas (ListenerExecutionFailedException → causa real)
                true);  // demais exceções (técnicas) são retentáveis
        var backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(props.initialIntervalMs());
        backOff.setMultiplier(props.multiplier());
        backOff.setMaxInterval(props.maxIntervalMs());

        return RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy)
                .backOffPolicy(backOff)
                // routing key = "error." + routing key original → DLQ correspondente
                .recoverer(new RepublishMessageRecoverer(rabbitTemplate, DLX))
                .build();
    }

    /** Factory padrão (@RabbitListener): virtual threads + retry + DLQ. */
    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            RetryOperationsInterceptor retryInterceptor) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setTaskExecutor(new VirtualThreadTaskExecutor("amqp-"));
        factory.setAdviceChain(retryInterceptor);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    /** Factory das DLQs: poucas tentativas e nunca republica (evita loop de erro do próprio auditor). */
    @Bean
    SimpleRabbitListenerContainerFactory dlqListenerContainerFactory(ConnectionFactory connectionFactory) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setTaskExecutor(new VirtualThreadTaskExecutor("amqp-dlq-"));
        factory.setConcurrentConsumers(1);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(5)
                .backOffOptions(1000, 2.0, 10000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }
}
