package com.handler.ride_request.mapper;

import com.handler.ride_request.entity.RideRequestDriverOfferEntity;
import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.entity.UserEntity;
import com.handler.ride_request.enums.OfferStatus;
import com.handler.ride_request.domain.RideRequest;
import com.handler.ride_request.enums.StatusEnum;
import org.springframework.data.geo.Point;

import java.time.OffsetDateTime;
import java.util.UUID;

public class RideRequestMapper {

    private RideRequestMapper() {
        // Private constructor to prevent instantiation
    }

    public static RideRequestEntity mapToRideRequestEntity(UserEntity userEntity, RideRequest rideRequest, StatusEnum status){
        return RideRequestEntity.builder()
                .user(userEntity)
                .status(status)
                .location(new Point(rideRequest.location().getLongitude(), rideRequest.location().getLatitude()))
                .identifier(rideRequest.userIdentifier() + UUID.randomUUID())
                .build();
    }

    public static RideRequestDriverOfferEntity buildNotificationRecord(
            RideRequestEntity request,
            RiderEntity rider,
            int round,
            OffsetDateTime notifiedAt) {
        return RideRequestDriverOfferEntity.builder()
                .rideRequest(request)
                .rider(rider)
                .notificationRound(round)
                .notifiedAt(notifiedAt)
                .status(OfferStatus.NOTIFIED)
                .build();
    }

    public static void markRideRequestAsAccepted(
            RideRequestEntity rideRequest,
            RiderEntity rider,
            OffsetDateTime acceptedAt) {
        rideRequest.setStatus(StatusEnum.ACCEPTED);
        String driverIdentifier = rider.getDriverIdentifier() != null ? rider.getDriverIdentifier() : rider.getIdentifier();
        rideRequest.setAcceptedDriverIdentifier(driverIdentifier);
        rideRequest.setAcceptedDriverDisplayId(
                rider.getDriverDisplayId() != null
                        ? rider.getDriverDisplayId()
                        : "DRV-" + driverIdentifier.toUpperCase().replaceAll("[^A-Z0-9]+", "-"));
        rideRequest.setAcceptedAt(acceptedAt);
    }

    public static void markRideRequestAsTimedOut(RideRequestEntity rideRequest) {
        rideRequest.setStatus(StatusEnum.TIMED_OUT);
    }

    public static void markNotificationAsAccepted(RideRequestDriverOfferEntity notificationRecord, OffsetDateTime respondedAt) {
        updateNotificationStatus(notificationRecord, OfferStatus.ACCEPTED, respondedAt);
    }

    public static void markNotificationAsDeclined(RideRequestDriverOfferEntity notificationRecord, OffsetDateTime respondedAt) {
        updateNotificationStatus(notificationRecord, OfferStatus.DECLINED, respondedAt);
    }

    public static void markNotificationAsTimedOut(RideRequestDriverOfferEntity notificationRecord, OffsetDateTime respondedAt) {
        updateNotificationStatus(notificationRecord, OfferStatus.TIMED_OUT, respondedAt);
    }

    public static void markNotificationAsCanceled(RideRequestDriverOfferEntity notificationRecord, OffsetDateTime respondedAt) {
        updateNotificationStatus(notificationRecord, OfferStatus.CANCELED, respondedAt);
    }

    private static void updateNotificationStatus(
            RideRequestDriverOfferEntity notificationRecord,
            OfferStatus status,
            OffsetDateTime respondedAt) {
        notificationRecord.setStatus(status);
        notificationRecord.setRespondedAt(respondedAt);
    }
}
