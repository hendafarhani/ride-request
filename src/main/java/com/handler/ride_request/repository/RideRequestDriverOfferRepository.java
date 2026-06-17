package com.handler.ride_request.repository;

import com.handler.ride_request.entity.RideRequestDriverOfferEntity;
import com.handler.ride_request.enums.OfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RideRequestDriverOfferRepository extends JpaRepository<RideRequestDriverOfferEntity, Long> {

    @Query("""
            select offer
            from RideRequestDriverOfferEntity offer
            join fetch offer.rider
            where offer.rideRequest.id = :rideRequestId
            order by offer.notificationRound asc, offer.notifiedAt asc
            """)
    List<RideRequestDriverOfferEntity> findByRideRequestIdOrderByNotificationRoundAscNotifiedAtAsc(@Param("rideRequestId") Long rideRequestId);

    @Query("""
            select offer
            from RideRequestDriverOfferEntity offer
            join fetch offer.rider
            where offer.rideRequest.id = :rideRequestId
              and offer.status = :status
            """)
    List<RideRequestDriverOfferEntity> findByRideRequestIdAndStatus(@Param("rideRequestId") Long rideRequestId,
                                                                      @Param("status") OfferStatus status);

    @Query("""
            select offer
            from RideRequestDriverOfferEntity offer
            where offer.rideRequest.id = :rideRequestId
              and offer.rider.identifier = :riderIdentifier
            """)
    Optional<RideRequestDriverOfferEntity> findByRideRequestIdAndRiderIdentifier(@Param("rideRequestId") Long rideRequestId,
                                                                                   @Param("riderIdentifier") String riderIdentifier);

    @Query("""
            select max(offer.notificationRound)
            from RideRequestDriverOfferEntity offer
            where offer.rideRequest.id = :rideRequestId
            """)
    Integer findMaxNotificationRound(@Param("rideRequestId") Long rideRequestId);
}
