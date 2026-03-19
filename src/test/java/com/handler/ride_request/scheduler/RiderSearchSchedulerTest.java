package com.handler.ride_request.scheduler;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.UserEntity;
import com.handler.ride_request.enums.StatusEnum;
import com.handler.ride_request.model.Rider;
import com.handler.ride_request.rabbitmq.service.NotificationService;
import com.handler.ride_request.repository.RideRequestRepository;
import com.handler.ride_request.service.RidersSearchService;
import com.handler.ride_request.service.impl.RideRequestDriverAttemptServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Point;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiderSearchSchedulerTest {

    @Mock
    private RideRequestRepository rideRequestRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private RidersSearchService ridersSearchService;

    @Mock
    private RideRequestDriverAttemptServiceImpl attemptService;

    @InjectMocks
    private RiderSearchScheduler scheduler;

    private RideRequestEntity pendingRequest;

    @BeforeEach
    void setUp() {
        pendingRequest = RideRequestEntity.builder()
                .id(99L)
                .identifier("ride-99")
                .status(StatusEnum.PENDING)
                .location(new Point(12.0, 34.0))
                .user(UserEntity.builder().identifier("requester-1").build())
                .build();
    }

    @Test
    void scheduleRidersSearch_schedulesFixedRateTask() {
        ScheduledExecutorService executorMock = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> futureMock = mock(ScheduledFuture.class);
        
        doReturn(futureMock).when(executorMock)
                .scheduleAtFixedRate(any(Runnable.class), eq(4L), eq(4L), eq(java.util.concurrent.TimeUnit.MINUTES));
        ReflectionTestUtils.setField(scheduler, "scheduler", executorMock);

        scheduler.scheduleRidersSearch(123L);

        verify(executorMock).scheduleAtFixedRate(any(Runnable.class), eq(4L), eq(4L), eq(java.util.concurrent.TimeUnit.MINUTES));
    }

    @Test
    void handleRetry_cancelsWhenRideRequestIsMissing() throws Exception {
        when(rideRequestRepository.findById(123L)).thenReturn(Optional.empty());
        ScheduledFuture<?> future = mock(ScheduledFuture.class);

        invokeHandleRetry(123L, new AtomicInteger(), future);

        verify(future).cancel(false);
        verifyNoInteractions(attemptService, notificationService, ridersSearchService);
    }

    @Test
    void handleRetry_cancelsWhenStatusNotPending() throws Exception {
        RideRequestEntity accepted = RideRequestEntity.builder()
                .id(77L)
                .identifier("ride-accepted")
                .status(StatusEnum.ACCEPTED)
                .build();
        when(rideRequestRepository.findById(accepted.getId())).thenReturn(Optional.of(accepted));
        ScheduledFuture<?> future = mock(ScheduledFuture.class);


        invokeHandleRetry(accepted.getId(), new AtomicInteger(), future);

        verify(future).cancel(false);
        verifyNoInteractions(attemptService, notificationService, ridersSearchService);
    }

    @Test
    void handleRetry_marksCanceledAfterMaxRetries() throws Exception {
        when(rideRequestRepository.findById(pendingRequest.getId())).thenReturn(Optional.of(pendingRequest));
        ScheduledFuture<?> future = mock(ScheduledFuture.class);


        invokeHandleRetry(pendingRequest.getId(), new AtomicInteger(3), future);

        verify(attemptService).markOutstandingAttemptsAsTimedOut(pendingRequest.getId());
        verify(rideRequestRepository).save(pendingRequest);
        assertThat(pendingRequest.getStatus()).isEqualTo(StatusEnum.CANCELED);
        verify(future).cancel(false);
        verifyNoInteractions(notificationService, ridersSearchService);
    }

    @Test
    void handleRetry_relaunchesSearchAndIncrementsExecutionCount() throws Exception {
        when(rideRequestRepository.findById(pendingRequest.getId())).thenReturn(Optional.of(pendingRequest));
        when(attemptService.getAttemptedRiderIdentifiers(pendingRequest.getId())).thenReturn(Set.of("old-rider"));
        List<Rider> newCandidates = List.of(Rider.builder().identifier("new-1").build());
        when(ridersSearchService.findNearestVehicles(pendingRequest.getLocation(), Set.of("old-rider")))
                .thenReturn(newCandidates);
        when(attemptService.getNextNotificationRound(pendingRequest.getId())).thenReturn(2);
        when(attemptService.createAttemptsForRound(pendingRequest, newCandidates, 2)).thenReturn(newCandidates);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        
        AtomicInteger executionCount = new AtomicInteger(0);

        invokeHandleRetry(pendingRequest.getId(), executionCount, future);

        verify(attemptService).markOutstandingAttemptsAsTimedOut(pendingRequest.getId());
        verify(attemptService).createAttemptsForRound(pendingRequest, newCandidates, 2);
        verify(notificationService).sendRabbitMqNotification(newCandidates, pendingRequest);
        assertThat(executionCount.get()).isEqualTo(1);
        verify(future, never()).cancel(anyBoolean());
    }

    private void invokeHandleRetry(Long rideRequestId, AtomicInteger executionCount, ScheduledFuture<?> future) throws Exception {
        Method method = RiderSearchScheduler.class
                .getDeclaredMethod("handleRetry", Long.class, AtomicInteger.class, ScheduledFuture.class);
        method.setAccessible(true);
        method.invoke(scheduler, rideRequestId, executionCount, future);
    }
}

