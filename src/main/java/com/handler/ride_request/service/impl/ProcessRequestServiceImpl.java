package com.handler.ride_request.service.impl;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.UserEntity;
import com.handler.ride_request.mapper.RideRequestMapper;
import com.handler.ride_request.model.RideRequest;
import com.handler.ride_request.model.Rider;
import com.handler.ride_request.rabbitmq.service.NotificationService;
import com.handler.ride_request.repository.RideRequestRepository;
import com.handler.ride_request.repository.UserRepository;
import com.handler.ride_request.scheduler.RiderSearchScheduler;
import com.handler.ride_request.service.ProcessRequestService;
import com.handler.ride_request.service.RidersSearchService;
import com.handler.ride_request.tools.StatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@Service
public class ProcessRequestServiceImpl implements ProcessRequestService {

    private final UserRepository userRepository;
    private final RideRequestRepository rideRequestRepository;
    private final NotificationService notificationService;
    private final RidersSearchService ridersSearchService;
    private final RiderSearchScheduler scheduleRidersSearch;

    @Override
    public void processRideRequest(RideRequest rideRequest) {

        RideRequestEntity rideRequestEntity = saveRideRequest(rideRequest);
        if (isRideRequestEntityEmpty(rideRequestEntity)) return;

        // Search for the 5 nearest riders to the requesters => By reading from database Redis.
        List<Rider> riderData = ridersSearchService.findNearestVehicles(rideRequestEntity.getLocation());

        //Once the 5 nearest riders are found => send them a notification via rabbitMq
        //send RabbitMq requests where the key = rider identifier
        notificationService.sendRabbitMqNotification(riderData, rideRequestEntity);

        scheduleRidersSearch.scheduleRidersSearch(rideRequestEntity.getId());

        //TODO once I finish the part above and organize components and test it manually I will continue the other part.
        //If one rider confirms => send the confirmation via kafka by order => use a key to guarantee order.
        //Get the rider new location and send to all other riders whose ids are saved to a temporary database that the
        // ride has been taken
        //Send this notification via RabbitMQ.
        //Store the confirmation in database with all other details
        //How to send a rabbitmq notification for payment ? Will check this later.
    }


    private RideRequestEntity saveRideRequest(RideRequest rideRequest) {
        UserEntity userEntity = userRepository.findByIdentifier(rideRequest.userIdentifier()).orElse(null);

        if (Objects.isNull(userEntity)) return null;

        return rideRequestRepository.save(RideRequestMapper.mapToRideRequestEntity(userEntity, rideRequest, StatusEnum.PENDING));
    }

    private boolean isRideRequestEntityEmpty(RideRequestEntity rideRequestEntity){
        return Objects.isNull(rideRequestEntity);
    }

}
