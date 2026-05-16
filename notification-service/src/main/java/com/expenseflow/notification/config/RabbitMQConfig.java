package com.expenseflow.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "expense.exchange";
    public static final String DLX = "notification.event.dlx";
    public static final String NOTIFY_QUEUE = "notification.event.queue";
    public static final String DLQ = "notification.event.dlq";
    public static final String RESULT_KEY = "expense.result.notified";
    public static final String REVIEW_KEY = "ai.review.completed";
    public static final String SUBMITTED_KEY = "expense.report.submitted";
    public static final String WITHDRAWN_KEY = "expense.report.withdrawn";
    public static final String PAYMENT_KEY = "expense.payment.completed";

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public TopicExchange expenseExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DLQ);
    }

    @Bean
    public Queue notifyQueue() {
        return QueueBuilder.durable(NOTIFY_QUEUE)
            .withArgument("x-dead-letter-exchange", DLX)
            .withArgument("x-dead-letter-routing-key", DLQ)
            .build();
    }

    @Bean
    public Binding resultBinding() {
        return BindingBuilder.bind(notifyQueue()).to(expenseExchange()).with(RESULT_KEY);
    }

    @Bean
    public Binding reviewCompletedBinding() {
        return BindingBuilder.bind(notifyQueue()).to(expenseExchange()).with(REVIEW_KEY);
    }

    @Bean
    public Binding submittedBinding() {
        return BindingBuilder.bind(notifyQueue()).to(expenseExchange()).with(SUBMITTED_KEY);
    }

    @Bean
    public Binding withdrawnBinding() {
        return BindingBuilder.bind(notifyQueue()).to(expenseExchange()).with(WITHDRAWN_KEY);
    }

    @Bean
    public Binding paymentBinding() {
        return BindingBuilder.bind(notifyQueue()).to(expenseExchange()).with(PAYMENT_KEY);
    }
}
