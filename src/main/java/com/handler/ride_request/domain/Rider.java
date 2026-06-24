package com.handler.ride_request.domain;

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
    private String driverIdentifier;
    private String driverDisplayId;
    private String userName;
    private String averageDistance;
    private Point point;
    private String hash;

    public String effectiveDriverIdentifier() {
        return driverIdentifier != null ? driverIdentifier : identifier;
    }
}
