package com.handler.ride_request.service.serviceimpl;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.enums.RideRequestEventType;
import com.handler.ride_request.enums.StatusEnum;
import com.handler.ride_request.rabbitmq.service.NotificationService;
import com.handler.ride_request.repository.RideRequestRepository;
import com.handler.ride_request.repository.RiderRepository;
import com.handler.ride_request.service.EventOutboxService;
import com.handler.ride_request.service.RideRequestDriverOfferService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RideResponseServiceImplTest {

    @Mock
    private RideRequestRepository rideRequestRepository;

    @Mock
    private RiderRepository riderRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private RideRequestDriverOfferService offerService;

    @Mock
    private EventOutboxService eventOutboxService;

    @InjectMocks
    private RideResponseServiceImpl service;

    private RideRequestEntity pendingRequest;
    private RiderEntity rider;

    @BeforeEach
    void setUp() {
        pendingRequest = RideRequestEntity.builder()
                .id(10L)
                .identifier("ride-123")
                .status(StatusEnum.PENDING)
                .build();

        rider = RiderEntity.builder()
                .identifier("rider-999")
                .build();
    }

    @Test
    void shouldThrowWhenRideRequestIdentifierIsBlank() {
        assertThatThrownBy(() -> service.acceptRide("  ", "rider-1"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(rideRequestRepository, riderRepository, offerService, notificationService);
    }

    @Test
    void shouldThrowWhenRiderIdentifierIsBlank() {
        assertThatThrownBy(() -> service.acceptRide("ride-1", null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(rideRequestRepository, riderRepository, offerService, notificationService);
    }

    @Test
    void shouldThrowWhenRideRequestIsMissing() {
        when(rideRequestRepository.findByIdentifier("ride-404"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptRide("ride-404", "rider-1"))
                .isInstanceOf(EntityNotFoundException.class);

        verify(rideRequestRepository).findByIdentifier("ride-404");
        verifyNoInteractions(riderRepository, offerService, notificationService);
    }

    @Test
    void shouldThrowWhenRiderIsMissing() {
        when(rideRequestRepository.findByIdentifier("ride-123"))
                .thenReturn(Optional.of(pendingRequest));
        when(riderRepository.findByIdentifier("unknown"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptRide("ride-123", "unknown"))
                .isInstanceOf(EntityNotFoundException.class);

        verify(riderRepository).findByIdentifier("unknown");
        verifyNoInteractions(offerService, notificationService);
    }

    @Test
    void shouldThrowWhenRideRequestIsNotPending() {
        pendingRequest.setStatus(StatusEnum.ACCEPTED);
        when(rideRequestRepository.findByIdentifier("ride-123"))
                .thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.acceptRide("ride-123", "rider-999"))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(riderRepository, offerService, notificationService);
    }

    @Test
    void shouldAcceptRideUpdateRequestAndNotify() {
        when(rideRequestRepository.findByIdentifier("ride-123"))
                .thenReturn(Optional.of(pendingRequest));
        when(riderRepository.findByIdentifier("rider-999"))
                .thenReturn(Optional.of(rider));
        when(rideRequestRepository.save(pendingRequest)).thenReturn(pendingRequest);

        service.acceptRide("ride-123", "rider-999");

        verify(offerService).markAccepted(eq(10L), eq("rider-999"), any(OffsetDateTime.class));
        verify(offerService).markOtherOpenOffersAsCanceled(eq(10L), eq("rider-999"), any(OffsetDateTime.class));
        verify(rideRequestRepository).save(pendingRequest);
        assertThat(pendingRequest.getStatus()).isEqualTo(StatusEnum.ACCEPTED);
        assertThat(pendingRequest.getAcceptedRiderIdentifier()).isEqualTo("rider-999");
        assertThat(pendingRequest.getAcceptedAt()).isNotNull();
        verify(eventOutboxService).recordRideRequestEvent(RideRequestEventType.REQUEST_ACCEPTED, pendingRequest);
        verify(notificationService).notifyRideAccepted(pendingRequest, "rider-999");
    }

    @Test
    void shouldThrowWhenDeclinedRideRequestIsMissing() {
        when(rideRequestRepository.findByIdentifier("ride-404"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.declineRide("ride-404", "rider-1"))
                .isInstanceOf(EntityNotFoundException.class);

        verify(rideRequestRepository).findByIdentifier("ride-404");
        verifyNoInteractions(riderRepository, offerService, notificationService);
    }

    @Test
    void shouldThrowWhenDeclinedRideRequestIsNotPending() {
        pendingRequest.setStatus(StatusEnum.ACCEPTED);
        when(rideRequestRepository.findByIdentifier("ride-123"))
                .thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.declineRide("ride-123", "rider-999"))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(riderRepository, offerService, notificationService);
    }

    @Test
    void shouldDeclineRideOfferOnly() {
        when(rideRequestRepository.findByIdentifier("ride-123"))
                .thenReturn(Optional.of(pendingRequest));

        service.declineRide("ride-123", "rider-999");

        verify(offerService).markDeclined(eq(10L), eq("rider-999"), any(OffsetDateTime.class));
        verify(eventOutboxService).recordRiderEvent(RideRequestEventType.RIDER_DECLINED, pendingRequest, "rider-999");
        verifyNoInteractions(riderRepository, notificationService);
        verify(rideRequestRepository, never()).save(any());
        assertThat(pendingRequest.getStatus()).isEqualTo(StatusEnum.PENDING);
        assertThat(pendingRequest.getAcceptedRiderIdentifier()).isNull();
        assertThat(pendingRequest.getAcceptedAt()).isNull();
    }
}
