package com.handler.ride_request.rabbitmq.listener;

import com.handler.ride_request.rabbitmq.model.RideResponseMessage;
import com.handler.ride_request.rabbitmq.model.RideResponseType;
import com.handler.ride_request.service.RideResponseService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RideResponseListenerTest {

    @Mock
    private RideResponseService rideResponseService;

    private RideResponseListener listener;

    @BeforeEach
    void setUp() {
        listener = new RideResponseListener(rideResponseService);
    }

    @Test
    void onRideResponse_acceptanceInvokesAcceptService() {
        listener.onRideResponse(new RideResponseMessage("ride-1", "rider-9", RideResponseType.ACCEPTED));

        verify(rideResponseService).acceptRide("ride-1", "rider-9");
        verify(rideResponseService, never()).declineRide(any(), any());
    }

    @Test
    void onRideResponse_declineInvokesDeclineService() {
        listener.onRideResponse(new RideResponseMessage("ride-1", "rider-9", RideResponseType.DECLINED));

        verify(rideResponseService).declineRide("ride-1", "rider-9");
        verify(rideResponseService, never()).acceptRide(any(), any());
    }

    @Test
    void onRideResponse_invalidMessageIsIgnored() {
        listener.onRideResponse(new RideResponseMessage("ride-1", "", RideResponseType.DECLINED));
        listener.onRideResponse(new RideResponseMessage("ride-1", "rider-9", null));
        listener.onRideResponse(null);

        verifyNoInteractions(rideResponseService);
    }

    @Test
    void onRideResponse_businessExceptionsAreSwallowed() {
        doThrow(new EntityNotFoundException("not found"))
                .when(rideResponseService).declineRide("ride-1", "rider-9");

        listener.onRideResponse(new RideResponseMessage("ride-1", "rider-9", RideResponseType.DECLINED));

        verify(rideResponseService).declineRide("ride-1", "rider-9");
    }
}
