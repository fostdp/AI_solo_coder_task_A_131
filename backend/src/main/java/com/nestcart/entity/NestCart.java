package com.nestcart.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nest_cart")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NestCart {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "boom_length", nullable = false)
    private Double boomLength;

    @Column(name = "boom_cross_section_area", nullable = false)
    private Double boomCrossSectionArea;

    @Column(name = "boom_moment_of_inertia", nullable = false)
    private Double boomMomentOfInertia;

    @Column(name = "boom_elastic_modulus", nullable = false)
    private Double boomElasticModulus;

    @Column(name = "basket_weight", nullable = false)
    private Double basketWeight;

    @Column(name = "base_height", nullable = false)
    private Double baseHeight;

    @Column(name = "max_height", nullable = false)
    private Double maxHeight;

    @Column(name = "stress_limit", nullable = false)
    private Double stressLimit;

    @Column(name = "sway_limit", nullable = false)
    private Double swayLimit;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
