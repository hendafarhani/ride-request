package com.handler.ride_request.repository;

import com.handler.ride_request.entity.EventOutboxEntity;
import com.handler.ride_request.enums.OutboxEventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, Long> {

    List<EventOutboxEntity> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status);

    List<EventOutboxEntity> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status, Pageable pageable);

    List<EventOutboxEntity> findByRideRequestIdAndStatusOrderByCreatedAtAsc(Long rideRequestId, OutboxEventStatus status);

    List<EventOutboxEntity> findByRequesterIdAndStatusOrderByCreatedAtAsc(String requesterId, OutboxEventStatus status);

    List<EventOutboxEntity> findByDriverIdentifierAndStatusOrderByCreatedAtAsc(String driverIdentifier, OutboxEventStatus status);

    default List<EventOutboxEntity> findByRiderIdAndStatusOrderByCreatedAtAsc(String riderId, OutboxEventStatus status) {
        return findByDriverIdentifierAndStatusOrderByCreatedAtAsc(riderId, status);
    }
}
