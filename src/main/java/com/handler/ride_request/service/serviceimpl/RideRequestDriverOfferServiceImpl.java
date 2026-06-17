package com.handler.ride_request.service.serviceimpl;

import com.handler.ride_request.entity.RideRequestDriverOfferEntity;
import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.enums.OfferStatus;
import com.handler.ride_request.mapper.RideRequestMapper;
import com.handler.ride_request.model.Rider;
import com.handler.ride_request.repository.RideRequestDriverOfferRepository;
import com.handler.ride_request.repository.RiderRepository;
import com.handler.ride_request.service.RideRequestDriverOfferService;
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
public class RideRequestDriverOfferServiceImpl implements RideRequestDriverOfferService {

    private final RideRequestDriverOfferRepository offerRepository;
    private final RiderRepository riderRepository;

    public List<Rider> createOffersForRound(RideRequestEntity rideRequestEntity, List<Rider> riders, int notificationRound) {
        if (hasNoOfferCandidates(rideRequestEntity, riders)) {
            return List.of();
        }

        Map<String, RiderEntity> persistedRiders = fetchPersistedRiders(riders);
        if (hasNoPersistedRiders(rideRequestEntity, persistedRiders)) {
            return List.of();
        }

        List<Rider> persistedCandidates = findPersistedCandidates(riders, persistedRiders);
        if (hasNoPersistedCandidates(rideRequestEntity, persistedCandidates)) {
            return List.of();
        }

        saveNotificationRecords(rideRequestEntity, persistedRiders, persistedCandidates, notificationRound);
        return persistedCandidates;
    }

    public Set<String> getOfferedRiderIdentifiers(Long rideRequestId) {
        return findOrderedOffers(rideRequestId).stream()
                .map(RideRequestDriverOfferEntity::getRider)
                .filter(Objects::nonNull)
                .map(RiderEntity::getIdentifier)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public int getNextNotificationRound(Long rideRequestId) {
        Integer maxRound = offerRepository.findMaxNotificationRound(rideRequestId);
        return maxRound == null ? 1 : maxRound + 1;
    }

    public void markOutstandingOffersAsTimedOut(Long rideRequestId) {
        List<RideRequestDriverOfferEntity> notifiedOffers = findNotifiedOffers(rideRequestId);
        if (notifiedOffers.isEmpty()) {
            return;
        }

        markOffersAsTimedOut(notifiedOffers, OffsetDateTime.now());
        offerRepository.saveAll(notifiedOffers);
    }

    public void markAccepted(Long rideRequestId, String riderIdentifier, OffsetDateTime respondedAt) {
        markNotifiedOffer(rideRequestId, riderIdentifier, respondedAt, RideRequestMapper::markNotificationAsAccepted);
    }

    public void markDeclined(Long rideRequestId, String riderIdentifier, OffsetDateTime respondedAt) {
        markNotifiedOffer(rideRequestId, riderIdentifier, respondedAt, RideRequestMapper::markNotificationAsDeclined);
    }

    public void markOtherOpenOffersAsCanceled(Long rideRequestId, String acceptedRiderIdentifier, OffsetDateTime respondedAt) {
        List<RideRequestDriverOfferEntity> offersToCancel = findOtherNotifiedOffers(
                rideRequestId,
                acceptedRiderIdentifier);

        if (offersToCancel.isEmpty()) {
            return;
        }

        markOffersAsCanceled(offersToCancel, respondedAt);
        offerRepository.saveAll(offersToCancel);
    }

    private boolean hasNoOfferCandidates(RideRequestEntity rideRequestEntity, List<Rider> riders) {
        return rideRequestEntity == null || riders == null || riders.isEmpty();
    }

    private Map<String, RiderEntity> fetchPersistedRiders(List<Rider> riders) {
        Set<String> identifiers = extractIdentifiers(riders);
        if (identifiers.isEmpty()) {
            return Map.of();
        }
        return riderRepository.findByIdentifierIn(identifiers).stream()
                .collect(Collectors.toMap(RiderEntity::getIdentifier, Function.identity()));
    }

    private boolean hasNoPersistedRiders(RideRequestEntity rideRequestEntity, Map<String, RiderEntity> persistedRiders) {
        if (!persistedRiders.isEmpty()) {
            return false;
        }
        log.warn("No persisted riders found for ride request {}", rideRequestEntity.getId());
        return true;
    }

    private List<Rider> findPersistedCandidates(List<Rider> riders, Map<String, RiderEntity> persistedRiders) {
        List<Rider> candidates = new ArrayList<>();
        for (Rider rider : riders) {
            if (persistedRiders.containsKey(rider.getIdentifier())) {
                candidates.add(rider);
            } else {
                log.warn("Skipping rider {} because no MySQL rider record was found", rider.getIdentifier());
            }
        }
        return candidates;
    }

    private boolean hasNoPersistedCandidates(RideRequestEntity rideRequestEntity, List<Rider> persistedCandidates) {
        if (!persistedCandidates.isEmpty()) {
            return false;
        }
        log.warn("No offers created for ride request {} after filtering", rideRequestEntity.getId());
        return true;
    }

    private void saveNotificationRecords(
            RideRequestEntity rideRequestEntity,
            Map<String, RiderEntity> persistedRiders,
            List<Rider> persistedCandidates,
            int notificationRound) {
        List<RideRequestDriverOfferEntity> notificationRecords = buildNotificationRecords(
                rideRequestEntity,
                persistedRiders,
                persistedCandidates,
                notificationRound);
        offerRepository.saveAll(notificationRecords);
    }

    private List<RideRequestDriverOfferEntity> buildNotificationRecords(
            RideRequestEntity rideRequestEntity,
            Map<String, RiderEntity> persistedRiders,
            List<Rider> persistedCandidates,
            int notificationRound
    ) {
        OffsetDateTime notifiedAt = OffsetDateTime.now();
        return persistedCandidates.stream()
                .map(rider -> RideRequestMapper.buildNotificationRecord(
                        rideRequestEntity,
                        persistedRiders.get(rider.getIdentifier()),
                        notificationRound,
                        notifiedAt
                ))
                .toList();
    }

    private Set<String> extractIdentifiers(List<Rider> riders) {
        return riders.stream()
                .map(Rider::getIdentifier)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private List<RideRequestDriverOfferEntity> findOrderedOffers(Long rideRequestId) {
        return offerRepository.findByRideRequestIdOrderByNotificationRoundAscNotifiedAtAsc(rideRequestId);
    }

    private List<RideRequestDriverOfferEntity> findNotifiedOffers(Long rideRequestId) {
        return offerRepository.findByRideRequestIdAndStatus(rideRequestId, OfferStatus.NOTIFIED);
    }

    private List<RideRequestDriverOfferEntity> findOtherNotifiedOffers(
            Long rideRequestId,
            String acceptedRiderIdentifier) {
        return findNotifiedOffers(rideRequestId).stream()
                .filter(this::hasRider)
                .filter(offer -> isNotAcceptedRider(offer, acceptedRiderIdentifier))
                .toList();
    }

    private boolean hasRider(RideRequestDriverOfferEntity offer) {
        return offer.getRider() != null && offer.getRider().getIdentifier() != null;
    }

    private boolean isNotAcceptedRider(RideRequestDriverOfferEntity offer, String acceptedRiderIdentifier) {
        return !Objects.equals(offer.getRider().getIdentifier(), acceptedRiderIdentifier);
    }

    private void markNotifiedOffer(
            Long rideRequestId,
            String riderIdentifier,
            OffsetDateTime respondedAt,
            OfferStatusUpdater statusUpdater) {
        RideRequestDriverOfferEntity offer = findOfferOrThrow(rideRequestId, riderIdentifier);
        ensureOfferIsNotified(offer, riderIdentifier, rideRequestId);
        statusUpdater.update(offer, respondedAt);
        offerRepository.save(offer);
    }

    private RideRequestDriverOfferEntity findOfferOrThrow(Long rideRequestId, String riderIdentifier) {
        return offerRepository.findByRideRequestIdAndRiderIdentifier(rideRequestId, riderIdentifier)
                .orElseThrow(() -> new IllegalStateException(
                        "Rider " + riderIdentifier + " was not notified for ride " + rideRequestId));
    }

    private void markOffersAsTimedOut(List<RideRequestDriverOfferEntity> offers, OffsetDateTime respondedAt) {
        offers.forEach(offer -> RideRequestMapper.markNotificationAsTimedOut(offer, respondedAt));
    }

    private void markOffersAsCanceled(List<RideRequestDriverOfferEntity> offers, OffsetDateTime respondedAt) {
        offers.forEach(offer -> RideRequestMapper.markNotificationAsCanceled(offer, respondedAt));
    }

    private void ensureOfferIsNotified(RideRequestDriverOfferEntity offer, String riderIdentifier, Long rideRequestId) {
        if (!OfferStatus.NOTIFIED.equals(offer.getStatus())) {
            throw new IllegalStateException("Rider " + riderIdentifier + " notification for ride " + rideRequestId
                    + " is not awaiting response");
        }
    }

    @FunctionalInterface
    private interface OfferStatusUpdater {
        void update(RideRequestDriverOfferEntity offer, OffsetDateTime respondedAt);
    }
}
