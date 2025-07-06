package com.handler.ride_request.rabbitmq.service;


public interface RabbitMQUserService {
    void createUserQueue(String userId);
}
