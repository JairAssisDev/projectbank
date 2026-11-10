package com.digitalbank.notification.messaging;

import static com.digitalbank.notification.messaging.RabbitNames.*;

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
 * Topologia + política de falhas (ver account-service/RabbitConfig para o fluxo completo):
 * retry com backoff exponencial → RepublishMessageRecoverer → DLX → {@code <fila>.dlq} → error_audit.
 */
@Configuration
public class RabbitConfig {

    @Bean
    TopicExchange sagaExchange() {
        return ExchangeBuilder.topicExchange(SAGA_EXCHANGE).durable(true).build();
    }

    @Bean
    TopicExchange deadLetterExchange() {
        return ExchangeBuilder.topicExchange(DLX).durable(true).build();
    }

    @Bean
    Declarables transferCompletedQueue() {
        return queueWithDlq(TRANSFER_COMPLETED_QUEUE, TRANSFER_COMPLETED_RK);
    }

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

    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    RetryOperationsInterceptor retryInterceptor(RabbitTemplate rabbitTemplate, MessagingRetryProperties props) {
        var retryPolicy = new SimpleRetryPolicy(props.maxAttempts(),
                Map.of(MessageConversionException.class, false,
                        NonRetryableMessageException.class, false),
                true,
                true);
        var backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(props.initialIntervalMs());
        backOff.setMultiplier(props.multiplier());
        backOff.setMaxInterval(props.maxIntervalMs());

        return RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy)
                .backOffPolicy(backOff)
                .recoverer(new RepublishMessageRecoverer(rabbitTemplate, DLX))
                .build();
    }

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
