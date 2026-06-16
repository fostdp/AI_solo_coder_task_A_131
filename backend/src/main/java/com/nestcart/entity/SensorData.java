package com.nestcart.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sensor_data")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorData {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "cart_id", nullable = false)
    private UUID cartId;

    @Column(name = "timestamp", nullable = false)
    private OffsetDateTime timestamp;

    @Column(name = "boom_stress", nullable = false)
    private Double boomStress;

    @Column(name = "basket_sway", nullable = false)
    private Double basketSway;

    @Column(name = "height", nullable = false)
    private Double height;

    @Column(name = "observation_distance", nullable = false)
    private Double observationDistance;

    @Column(name = "wind_speed")
    private Double windSpeed;

    @Column(name = "wind_direction")
    private Double windDirection;

    @Column(name = "temperature")
    private Double temperature;
}
