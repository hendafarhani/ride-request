package com.handler.ride_request.entity;

import com.handler.ride_request.enums.OfferStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Persists one rider notification for a ride request together with that rider's response.
 * The existing "offer" name is kept for schema compatibility.
 */
@Builder
@Getter
@Setter
@Entity
@Table(name = "RIDE_REQUEST_DRIVER_OFFER")
@NoArgsConstructor
@AllArgsConstructor
public class RideRequestDriverOfferEntity {

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
    private OfferStatus status;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;
}
