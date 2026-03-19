package com.handler.ride_request.kafka.configuration;

import com.handler.ride_request.kafka.serialization.RideRequestJsonDeserializer;
import com.handler.ride_request.model.RideRequest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaListenerConfiguration {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RideRequest> kafkaListenerContainerFactory(
            ConsumerFactory<String, RideRequest> rideRequestConsumerFactory){
        ConcurrentKafkaListenerContainerFactory<String, RideRequest> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(rideRequestConsumerFactory);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, RideRequest> rideRequestConsumerFactory(
            @Value("${kafka.bootstrap-servers}") String bootstrapServers){
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        ErrorHandlingDeserializer<RideRequest> errorHandlingDeserializer =
                new ErrorHandlingDeserializer<>(new RideRequestJsonDeserializer());
        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), errorHandlingDeserializer);
    }





}
