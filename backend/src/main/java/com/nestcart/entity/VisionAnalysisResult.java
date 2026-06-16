package com.nestcart.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "vision_analysis_result")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisionAnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "cart_id", nullable = false)
    private UUID cartId;

    @Column(name = "height", nullable = false)
    private Double height;

    @Column(name = "visible_points", nullable = false)
    private Integer visiblePoints;

    @Column(name = "total_points", nullable = false)
    private Integer totalPoints;

    @Column(name = "coverage_ratio", nullable = false)
    private Double coverageRatio;

    @Column(name = "max_visible_distance", nullable = false)
    private Double maxVisibleDistance;

    @Column(name = "visible_grid", columnDefinition = "jsonb")
    private String visibleGrid;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
