package com.handler.ride_request.service.impl;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.UserEntity;
import com.handler.ride_request.model.RideRequest;
import com.handler.ride_request.model.Rider;
import com.handler.ride_request.rabbitmq.service.NotificationService;
import com.handler.ride_request.repository.RideRequestRepository;
import com.handler.ride_request.repository.UserRepository;
import com.handler.ride_request.scheduler.RiderSearchScheduler;
import com.handler.ride_request.service.RidersSearchService;
import com.handler.ride_request.service.impl.helper.ProcessRequestServiceTestHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

class ProcessRequestServiceImplTest {


    ProcessRequestServiceImpl service;
    UserRepository userRepositoryMock;
    RideRequestRepository rideRequestRepositoryMock;
    NotificationService notificationServiceMock;
    RiderSearchScheduler scheduleRidersSearchMock;
    RidersSearchService ridersSearchServiceMock;

    @Captor
    ArgumentCaptor<RideRequestEntity> rideRequestEntityCaptor1;

    @Captor
    ArgumentCaptor<RideRequestEntity> rideRequestEntityCaptor2;
    @Captor
    ArgumentCaptor<List<Rider>> riderDataRequestCaptor;
    @Captor
    ArgumentCaptor<Long> idCaptor;


    @BeforeEach
    void setUp(){
        userRepositoryMock = mock(UserRepository.class);
        rideRequestRepositoryMock = mock(RideRequestRepository.class);
        notificationServiceMock = mock(NotificationService.class);
        ridersSearchServiceMock = mock(RidersSearchService.class);
        scheduleRidersSearchMock = mock(RiderSearchScheduler.class);

        service = new ProcessRequestServiceImpl(userRepositoryMock,
        rideRequestRepositoryMock,
        notificationServiceMock,
        ridersSearchServiceMock,
        scheduleRidersSearchMock);

        MockitoAnnotations.openMocks(this); // Initialize mocks and captors
    }


    @Test
    void processRideRequest() {
        RideRequest rideRequest = ProcessRequestServiceTestHelper.getRideRequest();
        UserEntity userEntity = ProcessRequestServiceTestHelper.getUserEntity();
        RideRequestEntity rideRequestEntity = ProcessRequestServiceTestHelper.getRideRequestEntity();
        List<Rider> riders = ProcessRequestServiceTestHelper.getListOfRiders();

        when(userRepositoryMock.findByIdentifier(rideRequest.userIdentifier())).thenReturn(Optional.of(userEntity));
        when(rideRequestRepositoryMock.save(any(RideRequestEntity.class))).thenReturn(rideRequestEntity);
        when(ridersSearchServiceMock.findNearestVehicles(rideRequestEntity.getLocation())).thenReturn(riders);
        doNothing().when(notificationServiceMock).sendRabbitMqNotification(riderDataRequestCaptor.capture(), rideRequestEntityCaptor2.capture());
        doNothing().when(scheduleRidersSearchMock).scheduleRidersSearch(idCaptor.capture());

        service.processRideRequest(rideRequest);

        Assertions.assertEquals(riders, riderDataRequestCaptor.getValue());
        Assertions.assertEquals(rideRequestEntity, rideRequestEntityCaptor2.getValue());
        Assertions.assertEquals(rideRequestEntity.getId(), idCaptor.getValue());
        verify(rideRequestRepositoryMock, atLeastOnce()).save(any(RideRequestEntity.class));
    }
}
