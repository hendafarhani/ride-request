package com.handler.ride_request.kafka.handler;

import com.handler.ride_request.kafka.model.DriverGeneratedEvent;
import com.handler.ride_request.service.DriverProjectionService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DriverGeneratedEventHandlerTest {

    @Test
    void delegatesProjectionToService() {
        DriverProjectionService projectionService = mock(DriverProjectionService.class);
        DriverGeneratedEventHandler handler = new DriverGeneratedEventHandler(projectionService);
        DriverGeneratedEvent event = DriverGeneratedEvent.builder()
                .driverId("driver-101")
                .driverDisplayId("DRV-DRIVER-101")
                .scenario("AIRPORT_RUSH")
                .build();

        handler.onDriverGenerated(event);

        verify(projectionService).upsertDriver(event);
    }
}
