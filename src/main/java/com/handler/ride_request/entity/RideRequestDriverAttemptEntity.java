package com.handler.ride_request.entity;

import com.handler.ride_request.enums.AttemptStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Builder
@Getter
@Setter
@Entity
@Table(name = "RIDE_REQUEST_DRIVER_ATTEMPT")
@NoArgsConstructor
@AllArgsConstructor
public class RideRequestDriverAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ride_request_id", nullable = false)
    private RideRequestEntity rideRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rider_id", nullable = false)
    private RiderEntity rider;

    @Column(name = "notification_round", nullable = false)
    private int notificationRound;

    @Column(name = "notified_at", nullable = false)
    private OffsetDateTime notifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AttemptStatus status;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;
}
