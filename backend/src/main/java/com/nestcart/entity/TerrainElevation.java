package com.nestcart.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "terrain_elevation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TerrainElevation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "grid_x", nullable = false)
    private Integer gridX;

    @Column(name = "grid_y", nullable = false)
    private Integer gridY;

    @Column(name = "elevation", nullable = false)
    private Double elevation;

    @Column(name = "resolution", nullable = false)
    private Double resolution;

    @Column(name = "region_name", length = 100)
    private String regionName;
}
