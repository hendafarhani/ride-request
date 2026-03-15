package com.handler.ride_request.rabbitmq.listener;

import com.handler.ride_request.rabbitmq.model.RideAcceptanceMessage;
import com.handler.ride_request.service.RideAcceptanceService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RideAcceptanceListenerTest {

    @Mock
    private RideAcceptanceService rideAcceptanceService;

    private RideAcceptanceListener listener;

    @BeforeEach
    void setUp() {
        listener = new RideAcceptanceListener(rideAcceptanceService);
    }

    @Test
    void onRideAccepted_invokesService() {
        RideAcceptanceMessage message = new RideAcceptanceMessage("ride-1", "rider-9");

        listener.onRideAccepted(message);

        verify(rideAcceptanceService).acceptRide("ride-1", "rider-9");
    }

    @Test
    void onRideAccepted_missingIdentifiers_isIgnored() {
        listener.onRideAccepted(new RideAcceptanceMessage("ride-1", ""));

        verifyNoInteractions(rideAcceptanceService);
    }

    @Test
    void onRideAccepted_businessExceptionsAreSwallowed() {
        RideAcceptanceMessage message = new RideAcceptanceMessage("ride-1", "rider-9");
        doThrow(new EntityNotFoundException("not found"))
                .when(rideAcceptanceService).acceptRide("ride-1", "rider-9");

        listener.onRideAccepted(message);

        verify(rideAcceptanceService).acceptRide("ride-1", "rider-9");
    }
}

