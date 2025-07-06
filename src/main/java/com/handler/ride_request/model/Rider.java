package com.handler.ride_request.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.geo.Point;
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Rider {
    private String identifier;
    private String userName;
    private String averageDistance;
    private Point point;
    private String hash;
}
