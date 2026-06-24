package com.handler.ride_request.service;

import com.handler.ride_request.kafka.model.DriverGeneratedEvent;

public interface DriverProjectionService {

    void upsertDriver(DriverGeneratedEvent event);
}
