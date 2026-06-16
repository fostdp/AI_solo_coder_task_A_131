package com.nestcart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertMessage {

    private UUID alertId;
    private UUID cartId;
    private String alertType;
    private String severity;
    private String message;
    private Double metricValue;
    private Double threshold;
    private String timestamp;
}
