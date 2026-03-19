package com.handler.ride_request.rabbitmq.service.impl;

import com.handler.ride_request.rabbitmq.service.RabbitMQUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.*;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class RabbitMQUserServiceImpl implements RabbitMQUserService {

    private final AmqpAdmin amqpAdmin;

    private final DirectExchange userExchange;

    // Create and bind a queue for the user
    @Override
    public void createUserQueue(String userId) {
        String queueName = "queue.user." + userId;

        // Declare a new queue
        Queue userQueue = new Queue(queueName, true);
        amqpAdmin.declareQueue(userQueue);

        // Bind the queue to the exchange with the user's identifier as the routing key
        Binding binding = BindingBuilder.bind(userQueue)
                .to(userExchange)
                .with(userId);
        amqpAdmin.declareBinding(binding);
    }
}