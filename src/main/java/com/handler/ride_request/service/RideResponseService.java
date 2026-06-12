package com.handler.ride_request.service;

public interface RideResponseService {

    void acceptRide(String rideRequestIdentifier, String riderIdentifier);

    void declineRide(String rideRequestIdentifier, String riderIdentifier);
}
