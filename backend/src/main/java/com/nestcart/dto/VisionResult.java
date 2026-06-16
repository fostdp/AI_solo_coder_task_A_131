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
    private Double height;
    private String regionName;

    private Integer visiblePoints;
    private Integer totalPoints;
    private Double coverageRatio;
    private Double maxVisibleDistance;
    private Double theoreticalHorizon;

    private List<int[]> visibleGridPoints;
    private List<int[]> blockedGridPoints;

    private List<SectorAnalysis> sectorAnalyses;

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
