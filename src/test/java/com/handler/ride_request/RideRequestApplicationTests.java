package com.handler.ride_request;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"kafka.enabled=false"})
class RideRequestApplicationTests {

	@Test
	void contextLoads() {
	}

}
