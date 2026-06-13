package com.handler.ride_request.entity;

import com.handler.ride_request.enums.OutboxEventStatus;
import com.handler.ride_request.enums.RideRequestEventType;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Builder
@Getter
@Setter
@Entity
@Table(name = "EVENT_OUTBOX", indexes = {
        @Index(name = "idx_event_outbox_status_created", columnList = "status, created_at"),
        @Index(name = "idx_event_outbox_ride_request_status_created", columnList = "ride_request_id, status, created_at"),
        @Index(name = "idx_event_outbox_requester_status_created", columnList = "requester_id, status, created_at"),
        @Index(name = "idx_event_outbox_rider_status_created", columnList = "rider_id, status, created_at")
})
@NoArgsConstructor
@AllArgsConstructor
public class EventOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private RideRequestEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxEventStatus status;

    @Column(name = "ride_request_id", nullable = false)
    private Long rideRequestId;

    @Column(name = "ride_request_identifier", nullable = false)
    private String rideRequestIdentifier;

    @Column(name = "requester_id", nullable = false)
    private String requesterId;

    @Column(name = "rider_id")
    private String riderId;

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "last_error")
    private String lastError;
}
