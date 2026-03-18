package com.handler.ride_request.entity;

import com.handler.ride_request.enums.StatusEnum;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.geo.Point;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Builder
@Getter
@Setter
@Table(name = "RIDE_REQUEST")
@Entity
@AllArgsConstructor
@NoArgsConstructor
public class RideRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private UserEntity user;

    @Column(name = "identifier", unique = true)
    private String identifier;

    @Column(name = "status")
    private StatusEnum status;

    @Column(name = "location")
    private Point location;

    @Column(name = "accepted_rider_identifier")
    private String acceptedRiderIdentifier;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Builder.Default
    @OneToMany(mappedBy = "rideRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RideRequestDriverAttemptEntity> driverAttempts = new ArrayList<>();
}
