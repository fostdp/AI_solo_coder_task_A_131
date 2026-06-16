package com.nestcart.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "alert_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "cart_id", nullable = false)
    private UUID cartId;

    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "metric_value", nullable = false)
    private Double metricValue;

    @Column(name = "threshold", nullable = false)
    private Double threshold;

    @Column(name = "acknowledged")
    private Boolean acknowledged;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
