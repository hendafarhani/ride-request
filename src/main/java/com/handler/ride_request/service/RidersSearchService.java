package com.handler.ride_request.service;

import com.handler.ride_request.model.Rider;
import org.springframework.data.geo.Point;

import java.util.List;

public interface RidersSearchService {

    List<Rider> findNearestVehicles(Point location);

}
