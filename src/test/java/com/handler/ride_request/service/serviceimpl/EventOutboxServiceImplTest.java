package com.handler.ride_request.service.serviceimpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.handler.ride_request.entity.EventOutboxEntity;
import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.UserEntity;
import com.handler.ride_request.enums.OutboxEventStatus;
import com.handler.ride_request.enums.RideRequestEventType;
import com.handler.ride_request.enums.StatusEnum;
import com.handler.ride_request.repository.EventOutboxRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageRequest;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventOutboxServiceImplTest {

    @Mock
    private EventOutboxRepository eventOutboxRepository;

    private EventOutboxServiceImpl service;
    private ObjectMapper objectMapper;
    private RideRequestEntity rideRequest;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new EventOutboxServiceImpl(eventOutboxRepository, objectMapper);
        rideRequest = RideRequestEntity.builder()
                .id(42L)
                .identifier("ride-42")
                .status(StatusEnum.PENDING)
                .user(UserEntity.builder().identifier("requester-42").build())
                .build();
    }

    @Test
    void recordRideRequestEventBuildsPendingPayloadSnapshot() throws Exception {
        when(eventOutboxRepository.save(any(EventOutboxEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<EventOutboxEntity> eventCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);

        service.recordRideRequestEvent(RideRequestEventType.REQUEST_CREATED, rideRequest);

        verify(eventOutboxRepository).save(eventCaptor.capture());
        EventOutboxEntity event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(RideRequestEventType.REQUEST_CREATED);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getRideRequestId()).isEqualTo(42L);
        assertThat(event.getRideRequestIdentifier()).isEqualTo("ride-42");
        assertThat(event.getRequesterId()).isEqualTo("requester-42");
        assertThat(event.getRiderId()).isNull();
        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getUpdatedAt()).isNotNull();

        JsonNode payload = objectMapper.readTree(event.getPayload());
        assertThat(payload.get("eventType").asText()).isEqualTo("REQUEST_CREATED");
        assertThat(payload.get("rideRequestId").asLong()).isEqualTo(42L);
        assertThat(payload.get("rideRequestIdentifier").asText()).isEqualTo("ride-42");
        assertThat(payload.get("requesterId").asText()).isEqualTo("requester-42");
        assertThat(payload.get("riderId").isNull()).isTrue();
        assertThat(payload.get("rideStatus").asText()).isEqualTo("PENDING");
    }

    @Test
    void recordRiderEventStoresRiderIdentifierInPayloadAndColumn() throws Exception {
        when(eventOutboxRepository.save(any(EventOutboxEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<EventOutboxEntity> eventCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);

        service.recordRiderEvent(RideRequestEventType.RIDER_NOTIFIED, rideRequest, "rider-1");

        verify(eventOutboxRepository).save(eventCaptor.capture());
        EventOutboxEntity event = eventCaptor.getValue();
        assertThat(event.getRiderId()).isEqualTo("rider-1");

        JsonNode payload = objectMapper.readTree(event.getPayload());
        assertThat(payload.get("riderId").asText()).isEqualTo("rider-1");
    }

    @Test
    void markProcessedUpdatesStatusAndProcessedAt() {
        EventOutboxEntity event = EventOutboxEntity.builder()
                .id(99L)
                .status(OutboxEventStatus.PENDING)
                .createdAt(OffsetDateTime.now().minusMinutes(1))
                .updatedAt(OffsetDateTime.now().minusMinutes(1))
                .lastError("previous failure")
                .build();
        when(eventOutboxRepository.findById(99L)).thenReturn(Optional.of(event));
        when(eventOutboxRepository.save(event)).thenReturn(event);

        EventOutboxEntity result = service.markProcessed(99L);

        assertThat(result.getStatus()).isEqualTo(OutboxEventStatus.PROCESSED);
        assertThat(result.getProcessedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isEqualTo(result.getProcessedAt());
        assertThat(result.getLastError()).isNull();
        verify(eventOutboxRepository).save(event);
    }

    @Test
    void recordFailureKeepsEventPendingAndIncrementsRetryCount() {
        EventOutboxEntity event = EventOutboxEntity.builder()
                .id(99L)
                .status(OutboxEventStatus.PENDING)
                .retryCount(2)
                .createdAt(OffsetDateTime.now().minusMinutes(1))
                .updatedAt(OffsetDateTime.now().minusMinutes(1))
                .build();
        when(eventOutboxRepository.findById(99L)).thenReturn(Optional.of(event));
        when(eventOutboxRepository.save(event)).thenReturn(event);

        EventOutboxEntity result = service.recordFailure(99L, "RabbitMQ unavailable");

        assertThat(result.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(result.getRetryCount()).isEqualTo(3);
        assertThat(result.getLastError()).isEqualTo("RabbitMQ unavailable");
        assertThat(result.getUpdatedAt()).isAfter(result.getCreatedAt());
        verify(eventOutboxRepository).save(event);
    }

    @Test
    void markProcessedThrowsWhenEventIsMissing() {
        when(eventOutboxRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markProcessed(404L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void findPendingEventsWithLimitUsesOldestFirstPage() {
        service.findPendingEvents(25);

        verify(eventOutboxRepository).findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING, PageRequest.of(0, 25));
    }

    @Test
    void findPendingEventsWithNonPositiveLimitReturnsEmptyList() {
        assertThat(service.findPendingEvents(0)).isEmpty();

        verifyNoMoreInteractions(eventOutboxRepository);
    }
}
