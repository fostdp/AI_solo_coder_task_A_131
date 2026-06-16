package com.nestcart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisionResult {

    private UUID cartId;
    private Double observerHeight;
    private Double analysisRadius;
    private String regionName;

    private Integer visiblePoints;
    private Integer totalPoints;
    private Double coverageRatio;
    private Double maxVisibleDistance;
    private Double theoreticalHorizon;

    private List<GridPoint> visiblePointList;
    private List<GridPoint> blockedPointList;

    private List<SectorAnalysis> sectorAnalysis;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GridPoint {
        private int gridX;
        private int gridY;
        private double elevation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectorAnalysis {
        private double azimuth;
        private double minElevation;
        private double maxElevation;
        private double visibleDistance;
        private boolean isBlocked;
    }
}
