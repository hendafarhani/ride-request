package com.handler.ride_request.service.impl;

import com.handler.ride_request.entity.RideRequestDriverAttemptEntity;
import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.enums.AttemptStatus;
import com.handler.ride_request.model.Rider;
import com.handler.ride_request.repository.RideRequestDriverAttemptRepository;
import com.handler.ride_request.repository.RiderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RideRequestDriverAttemptService {

    private final RideRequestDriverAttemptRepository attemptRepository;
    private final RiderRepository riderRepository;

    public List<Rider> createAttemptsForRound(RideRequestEntity rideRequestEntity, List<Rider> riders, int notificationRound) {
        if (Objects.isNull(rideRequestEntity) || Objects.isNull(riders) || riders.isEmpty()) {
            return List.of();
        }

        Map<String, RiderEntity> persistedRiders = riderRepository.findByIdentifierIn(extractIdentifiers(riders)).stream()
                .collect(Collectors.toMap(RiderEntity::getIdentifier, rider -> rider));

        OffsetDateTime notifiedAt = OffsetDateTime.now();
        List<RideRequestDriverAttemptEntity> attempts = new ArrayList<>();
        List<Rider> persistedCandidates = new ArrayList<>();

        for (Rider rider : riders) {
            RiderEntity persistedRider = persistedRiders.get(rider.getIdentifier());
            if (Objects.isNull(persistedRider)) {
                log.warn("Skipping rider {} because no MySQL rider record was found", rider.getIdentifier());
                continue;
            }

            attempts.add(RideRequestDriverAttemptEntity.builder()
                    .rideRequest(rideRequestEntity)
                    .rider(persistedRider)
                    .notificationRound(notificationRound)
                    .notifiedAt(notifiedAt)
                    .status(AttemptStatus.NOTIFIED)
                    .build());
            persistedCandidates.add(rider);
        }

        attemptRepository.saveAll(attempts);
        return persistedCandidates;
    }

    public Set<String> getAttemptedRiderIdentifiers(Long rideRequestId) {
        return attemptRepository.findByRideRequestIdOrderByNotificationRoundAscNotifiedAtAsc(rideRequestId).stream()
                .map(RideRequestDriverAttemptEntity::getRider)
                .filter(Objects::nonNull)
                .map(RiderEntity::getIdentifier)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public int getNextNotificationRound(Long rideRequestId) {
        Integer maxRound = attemptRepository.findMaxNotificationRound(rideRequestId);
        return Objects.isNull(maxRound) ? 1 : maxRound + 1;
    }

    public void markOutstandingAttemptsAsTimedOut(Long rideRequestId) {
        List<RideRequestDriverAttemptEntity> openAttempts = attemptRepository.findByRideRequestIdAndStatus(rideRequestId, AttemptStatus.NOTIFIED);
        if (openAttempts.isEmpty()) {
            return;
        }

        OffsetDateTime respondedAt = OffsetDateTime.now();
        openAttempts.forEach(attempt -> {
            attempt.setStatus(AttemptStatus.TIMED_OUT);
            attempt.setRespondedAt(respondedAt);
        });
        attemptRepository.saveAll(openAttempts);
    }

    public void markAccepted(Long rideRequestId, String riderIdentifier, OffsetDateTime respondedAt) {
        RideRequestDriverAttemptEntity attempt = attemptRepository.findByRideRequestIdAndRiderIdentifier(rideRequestId, riderIdentifier)
                .orElseThrow(() -> new IllegalStateException("Rider " + riderIdentifier + " was not notified for ride " + rideRequestId));
        attempt.setStatus(AttemptStatus.ACCEPTED);
        attempt.setRespondedAt(respondedAt);
        attemptRepository.save(attempt);
    }

    public void markOtherOpenAttemptsAsCanceled(Long rideRequestId, String acceptedRiderIdentifier, OffsetDateTime respondedAt) {
        List<RideRequestDriverAttemptEntity> openAttempts = attemptRepository.findByRideRequestIdAndStatus(rideRequestId, AttemptStatus.NOTIFIED);
        List<RideRequestDriverAttemptEntity> attemptsToCancel = openAttempts.stream()
                .filter(attempt -> !Objects.equals(attempt.getRider().getIdentifier(), acceptedRiderIdentifier))
                .toList();

        attemptsToCancel.forEach(attempt -> {
            attempt.setStatus(AttemptStatus.CANCELED);
            attempt.setRespondedAt(respondedAt);
        });
        attemptRepository.saveAll(attemptsToCancel);
    }

    private Set<String> extractIdentifiers(List<Rider> riders) {
        return riders.stream()
                .map(Rider::getIdentifier)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
