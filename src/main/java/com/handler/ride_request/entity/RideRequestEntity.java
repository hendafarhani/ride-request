package com.handler.ride_request.entity;

import com.handler.ride_request.enums.StatusEnum;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.geo.Point;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Builder
@Getter
@Setter
@Entity
@Table(name = "RIDE_REQUEST")
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
    @ElementCollection
    @CollectionTable(name = "ride_request_candidates", joinColumns = @JoinColumn(name = "ride_request_id"))
    @Column(name = "rider_identifier")
    private Set<String> candidateRiderIdentifiers = new HashSet<>();
}
