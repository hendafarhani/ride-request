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
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RideRequestDriverAttemptService {

    private final RideRequestDriverAttemptRepository attemptRepository;
    private final RiderRepository riderRepository;

    public List<Rider> createAttemptsForRound(RideRequestEntity rideRequestEntity, List<Rider> riders, int notificationRound) {
        if (isInvalidAttemptInput(rideRequestEntity, riders)) {
            return List.of();
        }

        Map<String, RiderEntity> persistedRiders = fetchPersistedRiders(riders);
        if (persistedRiders.isEmpty()) {
            log.warn("No persisted riders found for ride request {}", rideRequestEntity.getId());
            return List.of();
        }

        OffsetDateTime notifiedAt = OffsetDateTime.now();
        List<RideRequestDriverAttemptEntity> attempts = new ArrayList<>();
        List<Rider> persistedCandidates = new ArrayList<>();

        for (Rider rider : riders) {
            RiderEntity persistedRider = persistedRiders.get(rider.getIdentifier());
            if (persistedRider == null) {
                log.warn("Skipping rider {} because no MySQL rider record was found", rider.getIdentifier());
                continue;
            }
            attempts.add(buildAttempt(rideRequestEntity, persistedRider, notificationRound, notifiedAt));
            persistedCandidates.add(rider);
        }

        if (attempts.isEmpty()) {
            log.warn("No attempts created for ride request {} after filtering", rideRequestEntity.getId());
            return List.of();
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
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public int getNextNotificationRound(Long rideRequestId) {
        Integer maxRound = attemptRepository.findMaxNotificationRound(rideRequestId);
        return maxRound == null ? 1 : maxRound + 1;
    }

    public void markOutstandingAttemptsAsTimedOut(Long rideRequestId) {
        List<RideRequestDriverAttemptEntity> openAttempts = attemptRepository.findByRideRequestIdAndStatus(rideRequestId, AttemptStatus.NOTIFIED);
        if (openAttempts.isEmpty()) {
            return;
        }

        updateAttemptsStatus(openAttempts, AttemptStatus.TIMED_OUT, OffsetDateTime.now());
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
        List<RideRequestDriverAttemptEntity> attemptsToCancel = attemptRepository
                .findByRideRequestIdAndStatus(rideRequestId, AttemptStatus.NOTIFIED)
                .stream()
                .filter(this::hasRider)
                .filter(attempt -> !Objects.equals(attempt.getRider().getIdentifier(), acceptedRiderIdentifier))
                .collect(Collectors.toList());

        if (attemptsToCancel.isEmpty()) {
            return;
        }

        updateAttemptsStatus(attemptsToCancel, AttemptStatus.CANCELED, respondedAt);
        attemptRepository.saveAll(attemptsToCancel);
    }

    private Map<String, RiderEntity> fetchPersistedRiders(List<Rider> riders) {
        Set<String> identifiers = extractIdentifiers(riders);
        if (identifiers.isEmpty()) {
            return Map.of();
        }
        return riderRepository.findByIdentifierIn(identifiers).stream()
                .collect(Collectors.toMap(RiderEntity::getIdentifier, Function.identity()));
    }

    private boolean isInvalidAttemptInput(RideRequestEntity rideRequestEntity, List<Rider> riders) {
        return rideRequestEntity == null || riders == null || riders.isEmpty();
    }

    private RideRequestDriverAttemptEntity buildAttempt(RideRequestEntity request, RiderEntity rider, int round, OffsetDateTime notifiedAt) {
        return RideRequestDriverAttemptEntity.builder()
                .rideRequest(request)
                .rider(rider)
                .notificationRound(round)
                .notifiedAt(notifiedAt)
                .status(AttemptStatus.NOTIFIED)
                .build();
    }

    private Set<String> extractIdentifiers(List<Rider> riders) {
        return riders.stream()
                .map(Rider::getIdentifier)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private boolean hasRider(RideRequestDriverAttemptEntity attempt) {
        return attempt.getRider() != null && attempt.getRider().getIdentifier() != null;
    }

    private void updateAttemptsStatus(List<RideRequestDriverAttemptEntity> attempts, AttemptStatus status, OffsetDateTime respondedAt) {
        attempts.forEach(attempt -> {
            attempt.setStatus(status);
            attempt.setRespondedAt(respondedAt);
        });
    }
}
