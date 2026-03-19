package com.handler.ride_request.service.impl;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.enums.StatusEnum;
import com.handler.ride_request.rabbitmq.service.NotificationService;
import com.handler.ride_request.repository.RideRequestRepository;
import com.handler.ride_request.repository.RiderRepository;
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
class RideAcceptanceServiceImplTest {

    @Mock
    private RideRequestRepository rideRequestRepository;

    @Mock
    private RiderRepository riderRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private RideRequestDriverAttemptService attemptService;

    @InjectMocks
    private RideAcceptanceServiceImpl service;

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

        verifyNoInteractions(rideRequestRepository, riderRepository, attemptService, notificationService);
    }

    @Test
    void shouldThrowWhenRiderIdentifierIsBlank() {
        assertThatThrownBy(() -> service.acceptRide("ride-1", null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(rideRequestRepository, riderRepository, attemptService, notificationService);
    }

    @Test
    void shouldThrowWhenRideRequestIsMissing() {
        when(rideRequestRepository.findByIdentifier("ride-404"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptRide("ride-404", "rider-1"))
                .isInstanceOf(EntityNotFoundException.class);

        verify(rideRequestRepository).findByIdentifier("ride-404");
        verifyNoInteractions(riderRepository, attemptService, notificationService);
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
        verifyNoInteractions(attemptService, notificationService);
    }

    @Test
    void shouldThrowWhenRideRequestIsNotPending() {
        pendingRequest.setStatus(StatusEnum.ACCEPTED);
        when(rideRequestRepository.findByIdentifier("ride-123"))
                .thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.acceptRide("ride-123", "rider-999"))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(riderRepository, attemptService, notificationService);
    }

    @Test
    void shouldAcceptRideUpdateRequestAndNotify() {
        when(rideRequestRepository.findByIdentifier("ride-123"))
                .thenReturn(Optional.of(pendingRequest));
        when(riderRepository.findByIdentifier("rider-999"))
                .thenReturn(Optional.of(rider));
        when(rideRequestRepository.save(pendingRequest)).thenReturn(pendingRequest);

        service.acceptRide("ride-123", "rider-999");

        verify(attemptService).markAccepted(eq(10L), eq("rider-999"), any(OffsetDateTime.class));
        verify(attemptService).markOtherOpenAttemptsAsCanceled(eq(10L), eq("rider-999"), any(OffsetDateTime.class));
        verify(rideRequestRepository).save(pendingRequest);
        assertThat(pendingRequest.getStatus()).isEqualTo(StatusEnum.ACCEPTED);
        assertThat(pendingRequest.getAcceptedRiderIdentifier()).isEqualTo("rider-999");
        assertThat(pendingRequest.getAcceptedAt()).isNotNull();
        verify(notificationService).notifyRideAccepted(pendingRequest, "rider-999");
    }
}

