package com.handler.ride_request.rabbitmq.service.impl;

import com.handler.ride_request.rabbitmq.service.QueueChecker;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class QueueCheckerImpl implements QueueChecker {

    private final RabbitAdmin rabbitAdmin;

    public QueueCheckerImpl(ConnectionFactory connectionFactory) {
        this.rabbitAdmin = new RabbitAdmin(connectionFactory);
    }

    @Override
    public boolean doesQueueExist(String queueName) {
            // Check if the queue exists
            return Optional.ofNullable(rabbitAdmin.getQueueProperties(queueName)).isPresent();
    }
}
