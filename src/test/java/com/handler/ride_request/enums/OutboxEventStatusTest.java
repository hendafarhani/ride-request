package com.handler.ride_request.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventStatusTest {

    @Test
    void supportsPublisherLifecycleStatuses() {
        assertThat(OutboxEventStatus.valueOf("PENDING")).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(OutboxEventStatus.valueOf("PROCESSING")).isEqualTo(OutboxEventStatus.PROCESSING);
        assertThat(OutboxEventStatus.valueOf("PUBLISHED")).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(OutboxEventStatus.valueOf("PROCESSED")).isEqualTo(OutboxEventStatus.PROCESSED);
    }
}
