package com.handler.ride_request.repository;

import com.handler.ride_request.entity.RideRequestDriverAttemptEntity;
import com.handler.ride_request.enums.AttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RideRequestDriverAttemptRepository extends JpaRepository<RideRequestDriverAttemptEntity, Long> {

    @Query("""
            select attempt
            from RideRequestDriverAttemptEntity attempt
            join fetch attempt.rider
            where attempt.rideRequest.id = :rideRequestId
            order by attempt.notificationRound asc, attempt.notifiedAt asc
            """)
    List<RideRequestDriverAttemptEntity> findByRideRequestIdOrderByNotificationRoundAscNotifiedAtAsc(@Param("rideRequestId") Long rideRequestId);

    @Query("""
            select attempt
            from RideRequestDriverAttemptEntity attempt
            join fetch attempt.rider
            where attempt.rideRequest.id = :rideRequestId
              and attempt.status = :status
            """)
    List<RideRequestDriverAttemptEntity> findByRideRequestIdAndStatus(@Param("rideRequestId") Long rideRequestId,
                                                                      @Param("status") AttemptStatus status);

    @Query("""
            select attempt
            from RideRequestDriverAttemptEntity attempt
            where attempt.rideRequest.id = :rideRequestId
              and attempt.rider.identifier = :riderIdentifier
            """)
    Optional<RideRequestDriverAttemptEntity> findByRideRequestIdAndRiderIdentifier(@Param("rideRequestId") Long rideRequestId,
                                                                                   @Param("riderIdentifier") String riderIdentifier);

    @Query("""
            select max(attempt.notificationRound)
            from RideRequestDriverAttemptEntity attempt
            where attempt.rideRequest.id = :rideRequestId
            """)
    Integer findMaxNotificationRound(@Param("rideRequestId") Long rideRequestId);
}
