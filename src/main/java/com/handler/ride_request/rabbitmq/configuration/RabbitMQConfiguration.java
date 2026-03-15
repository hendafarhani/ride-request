package com.handler.ride_request.rabbitmq.configuration;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableRabbit
@Configuration
public class RabbitMQConfiguration {

    // Declare a direct exchange
    @Bean
    public DirectExchange userExchange() {
        return new DirectExchange("user.notifications.exchange");
    }

    @Bean
    public DirectExchange rideAcceptanceExchange(@Value("${ride.acceptance.exchange:ride.acceptance.exchange}") String exchangeName) {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public Queue rideAcceptanceQueue(@Value("${ride.acceptance.queue:ride.acceptance.queue}") String queueName) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Binding rideAcceptanceBinding(@Qualifier("rideAcceptanceQueue") Queue rideAcceptanceQueue,
                                         @Qualifier("rideAcceptanceExchange") DirectExchange rideAcceptanceExchange,
                                         @Value("${ride.acceptance.routing-key:ride.acceptance}") String routingKey) {
        return BindingBuilder.bind(rideAcceptanceQueue).to(rideAcceptanceExchange).with(routingKey);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
