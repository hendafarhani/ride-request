package com.handler.ride_request.service;

import com.handler.ride_request.model.Rider;
import org.springframework.data.geo.Point;

import java.util.List;
import java.util.Set;

public interface RidersSearchService {

    List<Rider> findNearestVehicles(Point location, Set<String> excludedIdentifiers);

}
