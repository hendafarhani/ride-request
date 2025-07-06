package com.handler.ride_request.repository;

import com.handler.ride_request.entity.RiderEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RiderRepository  extends CrudRepository<RiderEntity, Long> {
}
