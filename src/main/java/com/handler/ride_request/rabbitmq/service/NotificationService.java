package com.handler.ride_request.rabbitmq.service;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.model.Rider;

import java.util.List;

public interface NotificationService {


    void sendRabbitMqNotification(List<Rider> riders, RideRequestEntity rideRequestEntity);

    void notifyRideAccepted(RideRequestEntity rideRequestEntity, String acceptedRiderIdentifier);
}
