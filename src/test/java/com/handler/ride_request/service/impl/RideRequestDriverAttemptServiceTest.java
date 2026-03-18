package com.handler.ride_request.service.impl;

import com.handler.ride_request.entity.RideRequestDriverAttemptEntity;
import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.enums.AttemptStatus;
import com.handler.ride_request.model.Rider;
import com.handler.ride_request.repository.RideRequestDriverAttemptRepository;
import com.handler.ride_request.repository.RiderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RideRequestDriverAttemptServiceTest {

    @Mock
    private RideRequestDriverAttemptRepository attemptRepository;

    @Mock
    private RiderRepository riderRepository;

    @InjectMocks
    private RideRequestDriverAttemptService service;

    private RideRequestEntity rideRequest;

    @BeforeEach
    void setUp() {
        rideRequest = RideRequestEntity.builder().id(42L).build();
    }

    @Test
    void createAttemptsForRoundReturnsEmptyWhenRideRequestIsNull() {
        List<Rider> riders = List.of(sampleRider("rider-1"));

        List<Rider> result = service.createAttemptsForRound(null, riders, 1);

        assertThat(result).isEmpty();
        verifyNoInteractions(riderRepository, attemptRepository);
    }

    @Test
    void createAttemptsForRoundReturnsEmptyWhenRidersAreMissing() {
        List<Rider> result = service.createAttemptsForRound(rideRequest, List.of(), 1);

        assertThat(result).isEmpty();
        verifyNoInteractions(riderRepository, attemptRepository);
    }

    @Test
    void createAttemptsForRoundReturnsEmptyWhenRidersListIsNull() {
        List<Rider> result = service.createAttemptsForRound(rideRequest, null, 1);

        assertThat(result).isEmpty();
        verifyNoInteractions(riderRepository, attemptRepository);
    }

    @Test
    void createAttemptsForRoundPersistsOnlyRidersThatExistInMySql() {
        Rider persistedCandidate = sampleRider("persisted-rider");
        Rider transientCandidate = sampleRider("transient-rider");
        RiderEntity riderEntity = riderEntity(7L, persistedCandidate.getIdentifier());

        when(riderRepository.findByIdentifierIn(Set.of("persisted-rider", "transient-rider")))
                .thenReturn(List.of(riderEntity));

        List<Rider> result = service.createAttemptsForRound(rideRequest,
                List.of(persistedCandidate, transientCandidate), 2);

        assertThat(result).containsExactly(persistedCandidate);

        List<RideRequestDriverAttemptEntity> savedAttempts = captureSavedAttempts();
        assertThat(savedAttempts).hasSize(1);
        RideRequestDriverAttemptEntity attempt = savedAttempts.getFirst();
        assertThat(attempt.getRideRequest()).isEqualTo(rideRequest);
        assertThat(attempt.getRider()).isEqualTo(riderEntity);
        assertThat(attempt.getNotificationRound()).isEqualTo(2);
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.NOTIFIED);
        assertThat(attempt.getNotifiedAt()).isNotNull();
    }

    @Test
    void createAttemptsForRoundPersistsAllRidersWhenEverybodyExists() {
        Rider first = sampleRider("first");
        Rider second = sampleRider("second");
        RiderEntity firstEntity = riderEntity(1L, "first");
        RiderEntity secondEntity = riderEntity(2L, "second");
        when(riderRepository.findByIdentifierIn(Set.of("first", "second")))
                .thenReturn(List.of(firstEntity, secondEntity));

        List<Rider> result = service.createAttemptsForRound(rideRequest, List.of(first, second), 3);

        assertThat(result).containsExactly(first, second);
        List<RideRequestDriverAttemptEntity> savedAttempts = captureSavedAttempts();
        assertThat(savedAttempts).hasSize(2);
        OffsetDateTime notifiedAt = savedAttempts.getFirst().getNotifiedAt();
        assertThat(notifiedAt).isNotNull();
        savedAttempts.forEach(attempt -> {
            assertThat(attempt.getRideRequest()).isEqualTo(rideRequest);
            assertThat(attempt.getNotificationRound()).isEqualTo(3);
            assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.NOTIFIED);
            assertThat(attempt.getNotifiedAt()).isEqualTo(notifiedAt);
        });
    }

    @Test
    void createAttemptsForRoundReturnsEmptyWhenNoRidersArePersisted() {
        Rider lone = sampleRider("ghost");
        when(riderRepository.findByIdentifierIn(Set.of("ghost"))).thenReturn(List.of());

        List<Rider> result = service.createAttemptsForRound(rideRequest, List.of(lone), 1);

        assertThat(result).isEmpty();
        List<RideRequestDriverAttemptEntity> savedAttempts = captureSavedAttempts();
        assertThat(savedAttempts).isEmpty();
    }

    @Test
    void getAttemptedRiderIdentifiersFiltersNullValues() {
        RiderEntity validRider = riderEntity(1L, "valid");
        RiderEntity nullIdentifierRider = riderEntity(2L, null);
        RideRequestDriverAttemptEntity attemptWithValidRider = attempt(validRider);
        RideRequestDriverAttemptEntity attemptWithNullRider = attempt(null);
        RideRequestDriverAttemptEntity attemptWithNullIdentifier = attempt(nullIdentifierRider);

        when(attemptRepository.findByRideRequestIdOrderByNotificationRoundAscNotifiedAtAsc(rideRequest.getId()))
                .thenReturn(List.of(attemptWithValidRider, attemptWithNullRider, attemptWithNullIdentifier));

        Set<String> identifiers = service.getAttemptedRiderIdentifiers(rideRequest.getId());

        assertThat(identifiers).containsExactly("valid");
    }

    @Test
    void getNextNotificationRoundDefaultsToOneWhenRepositoryReturnsNull() {
        when(attemptRepository.findMaxNotificationRound(rideRequest.getId())).thenReturn(null);

        assertEquals(1, service.getNextNotificationRound(rideRequest.getId()));
    }

    @Test
    void getNextNotificationRoundIncrementsExistingRound() {
        when(attemptRepository.findMaxNotificationRound(rideRequest.getId())).thenReturn(3);

        assertEquals(4, service.getNextNotificationRound(rideRequest.getId()));
    }

    @Test
    void markOutstandingAttemptsAsTimedOutUpdatesStatusesAndTimestamps() {
        RideRequestDriverAttemptEntity first = attempt(riderEntity(1L, "first"));
        RideRequestDriverAttemptEntity second = attempt(riderEntity(2L, "second"));

        when(attemptRepository.findByRideRequestIdAndStatus(rideRequest.getId(), AttemptStatus.NOTIFIED))
                .thenReturn(List.of(first, second));

        service.markOutstandingAttemptsAsTimedOut(rideRequest.getId());

        List<RideRequestDriverAttemptEntity> savedAttempts = captureSavedAttempts();
        assertThat(savedAttempts).hasSize(2);
        OffsetDateTime referenceTime = savedAttempts.getFirst().getRespondedAt();
        assertThat(referenceTime).isNotNull();
        savedAttempts.forEach(attempt -> {
            assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.TIMED_OUT);
            assertThat(attempt.getRespondedAt()).isEqualTo(referenceTime);
        });
    }

    @Test
    void markOutstandingAttemptsAsTimedOutSkipsWhenNothingToUpdate() {
        when(attemptRepository.findByRideRequestIdAndStatus(rideRequest.getId(), AttemptStatus.NOTIFIED))
                .thenReturn(List.of());

        service.markOutstandingAttemptsAsTimedOut(rideRequest.getId());

        verify(attemptRepository, never()).saveAll(anyCollection());
    }

    @Test
    void markAcceptedUpdatesStatusAndTimestamp() {
        RiderEntity rider = riderEntity(9L, "accepted");
        RideRequestDriverAttemptEntity attempt = attempt(rider);
        when(attemptRepository.findByRideRequestIdAndRiderIdentifier(rideRequest.getId(), "accepted"))
                .thenReturn(Optional.of(attempt));

        OffsetDateTime respondedAt = OffsetDateTime.now();
        service.markAccepted(rideRequest.getId(), "accepted", respondedAt);

        verify(attemptRepository).save(attempt);
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.ACCEPTED);
        assertThat(attempt.getRespondedAt()).isEqualTo(respondedAt);
    }

    @Test
    void markAcceptedThrowsWhenAttemptMissing() {
        when(attemptRepository.findByRideRequestIdAndRiderIdentifier(rideRequest.getId(), "missing"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> service.markAccepted(rideRequest.getId(), "missing", OffsetDateTime.now()));
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void markOtherOpenAttemptsAsCanceledCancelsAllButAcceptedRider() {
        RiderEntity acceptedRider = riderEntity(10L, "accepted");
        RiderEntity otherRider = riderEntity(11L, "other");
        RideRequestDriverAttemptEntity acceptedAttempt = attempt(acceptedRider);
        RideRequestDriverAttemptEntity otherAttempt = attempt(otherRider);

        when(attemptRepository.findByRideRequestIdAndStatus(rideRequest.getId(), AttemptStatus.NOTIFIED))
                .thenReturn(List.of(acceptedAttempt, otherAttempt));

        OffsetDateTime respondedAt = OffsetDateTime.now();
        service.markOtherOpenAttemptsAsCanceled(rideRequest.getId(), "accepted", respondedAt);

        List<RideRequestDriverAttemptEntity> savedAttempts = captureSavedAttempts();
        assertThat(savedAttempts).containsExactly(otherAttempt);
        assertThat(otherAttempt.getStatus()).isEqualTo(AttemptStatus.CANCELED);
        assertThat(otherAttempt.getRespondedAt()).isEqualTo(respondedAt);
        assertThat(acceptedAttempt.getStatus()).isEqualTo(AttemptStatus.NOTIFIED);
        assertThat(acceptedAttempt.getRespondedAt()).isNull();
    }

    private Rider sampleRider(String identifier) {
        return Rider.builder().identifier(identifier).build();
    }

    private RiderEntity riderEntity(long id, String identifier) {
        return RiderEntity.builder()
                .id(id)
                .identifier(identifier)
                .name("name-" + id)
                .licenseNumber("license-" + id)
                .build();
    }

    private RideRequestDriverAttemptEntity attempt(RiderEntity riderEntity) {
        return RideRequestDriverAttemptEntity.builder()
                .rideRequest(rideRequest)
                .rider(riderEntity)
                .notificationRound(1)
                .notifiedAt(OffsetDateTime.now().minusMinutes(1))
                .status(AttemptStatus.NOTIFIED)
                .build();
    }

    private List<RideRequestDriverAttemptEntity> captureSavedAttempts() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<RideRequestDriverAttemptEntity>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(attemptRepository).saveAll(captor.capture());
        Iterable<RideRequestDriverAttemptEntity> iterable = captor.getValue();
        return StreamSupport.stream(iterable.spliterator(), false).collect(Collectors.toList());
    }
}
