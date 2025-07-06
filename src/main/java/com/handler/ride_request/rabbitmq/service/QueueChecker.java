package com.handler.ride_request.rabbitmq.service;

public interface QueueChecker {


    boolean doesQueueExist(String queueName);
}
