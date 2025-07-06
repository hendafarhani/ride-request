package com.handler.ride_request.kafka.handler;

import com.handler.ride_request.kafka.helper.RequestHandlerTestHelper;
import com.handler.ride_request.model.RideRequest;
import com.handler.ride_request.service.ProcessRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class RequestHandlerTest {

    ProcessRequestService processServiceMock;
    RequestHandler handler;

    @BeforeEach
    void setUp() {
        processServiceMock = mock(ProcessRequestService.class);
        handler = new RequestHandler(processServiceMock);
    }


    @Test
    void listen() {
        RideRequest rideRequest = RequestHandlerTestHelper.getRideRequest();

        processServiceMock.processRideRequest(rideRequest);

        verify(processServiceMock, times(1)).processRideRequest(rideRequest);
    }
}