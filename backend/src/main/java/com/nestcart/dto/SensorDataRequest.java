package com.nestcart.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorDataRequest {

    @NotNull
    private UUID cartId;

    @NotNull
    private Double boomStress;

    @NotNull
    private Double basketSway;

    @NotNull
    private Double height;

    @NotNull
    private Double observationDistance;

    private Double windSpeed;

    private Double windDirection;

    private Double temperature;
}
