package com.handler.ride_request.kafka.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverGeneratedEvent {

    private String driverId;
    private String driverDisplayId;
    private String scenario;
}
