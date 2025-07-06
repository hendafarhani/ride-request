package com.handler.ride_request.entity;

import com.handler.ride_request.tools.StatusEnum;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.geo.Point;

@Builder
@Getter
@Setter
@Entity
@Table(name = "RIDE_REQUEST")
public class RideRequestEntity {

    @Id
    private Long id;

    @ManyToOne
    private UserEntity user;

    @Column(name = "identifier", unique = true)
    private String identifier;

    @Column(name = "status")
    private StatusEnum status;

    @Column(name = "location")
    private Point location;
}
