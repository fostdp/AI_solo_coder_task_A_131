package com.nestcart.service;

import com.nestcart.dto.VisionResult;
import com.nestcart.entity.NestCart;
import com.nestcart.entity.TerrainElevation;
import com.nestcart.entity.VisionAnalysisResult;
import com.nestcart.repository.NestCartRepository;
import com.nestcart.repository.TerrainElevationRepository;
import com.nestcart.repository.VisionAnalysisResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VisionAnalysisService {

    private final NestCartRepository nestCartRepository;
    private final TerrainElevationRepository terrainElevationRepository;
    private final VisionAnalysisResultRepository visionAnalysisResultRepository;

    @Value("${nestcart.vision.earth-radius:6371000.0}")
    private double earthRadius;

    @Value("${nestcart.vision.default-grid-resolution:10.0}")
    private double defaultGridResolution;

    @Value("${nestcart.vision.max-analysis-radius:5000.0}")
    private double maxAnalysisRadius;

    public VisionResult analyzeVision(UUID cartId, Double height, String regionName,
                                       Integer observerGridX, Integer observerGridY) {
        NestCart cart = nestCartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("巢车不存在: " + cartId));

        double effectiveHeight = height != null ? height : cart.getMaxHeight();
        String effectiveRegion = regionName != null ? regionName : "default_battlefield";

        List<TerrainElevation> terrain = terrainElevationRepository.findByRegionName(effectiveRegion);
        if (terrain.isEmpty()) {
            throw new IllegalArgumentException("地形数据不存在: " + effectiveRegion);
        }

        Map<String, TerrainElevation> terrainMap = terrain.stream()
                .collect(Collectors.toMap(
                        te -> te.getGridX() + "," + te.getGridY(),
                        te -> te,
                        (a, b) -> a
                ));

        int obsX = observerGridX != null ? observerGridX : 50;
        int obsY = observerGridY != null ? observerGridY : 50;

        double observerElevation = getElevation(terrainMap, obsX, obsY);
        double observerTotalHeight = observerElevation + effectiveHeight;

        double theoreticalHorizon = Math.sqrt(2.0 * earthRadius * effectiveHeight);

        int maxGridRadius = (int) Math.min(
                maxAnalysisRadius / defaultGridResolution,
                Math.max(100, 100)
        );

        List<int[]> visiblePoints = new ArrayList<>();
        List<int[]> blockedPoints = new ArrayList<>();
        double maxVisibleDistance = 0.0;

        for (int dx = -maxGridRadius; dx <= maxGridRadius; dx++) {
            for (int dy = -maxGridRadius; dy <= maxGridRadius; dy++) {
                if (dx == 0 && dy == 0) continue;

                int targetX = obsX + dx;
                int targetY = obsY + dy;

                String key = targetX + "," + targetY;
                if (!terrainMap.containsKey(key)) continue;

                double distance = Math.sqrt(dx * dx + dy * dy) * defaultGridResolution;
                if (distance > maxAnalysisRadius) continue;

                if (isLineOfSightClear(terrainMap, obsX, obsY, observerTotalHeight,
                        targetX, targetY, distance)) {
                    visiblePoints.add(new int[]{targetX, targetY});
                    maxVisibleDistance = Math.max(maxVisibleDistance, distance);
                } else {
                    blockedPoints.add(new int[]{targetX, targetY});
                }
            }
        }

        int totalAnalyzed = visiblePoints.size() + blockedPoints.size();
        double coverageRatio = totalAnalyzed > 0 ? (double) visiblePoints.size() / totalAnalyzed : 0.0;

        List<VisionResult.SectorAnalysis> sectorAnalyses = analyzeSectors(
                terrainMap, obsX, obsY, observerTotalHeight, maxGridRadius);

        VisionResult result = VisionResult.builder()
                .cartId(cartId)
                .height(effectiveHeight)
                .regionName(effectiveRegion)
                .visiblePoints(visiblePoints.size())
                .totalPoints(totalAnalyzed)
                .coverageRatio(coverageRatio)
                .maxVisibleDistance(maxVisibleDistance)
                .theoreticalHorizon(theoreticalHorizon)
                .visibleGridPoints(visiblePoints)
                .blockedGridPoints(blockedPoints)
                .sectorAnalyses(sectorAnalyses)
                .build();

        saveAnalysisResult(cartId, effectiveHeight, result);

        return result;
    }

    private boolean isLineOfSightClear(Map<String, TerrainElevation> terrainMap,
                                        int x0, int y0, double observerHeight,
                                        int x1, int y1, double totalDistance) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        double targetElevation = getElevation(terrainMap, x1, y1);

        int currentX = x0;
        int currentY = y0;

        while (true) {
            if (currentX == x1 && currentY == y1) break;

            int nextX = currentX;
            int nextY = currentY;

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                nextX += sx;
            }
            if (e2 < dx) {
                err += dx;
                nextY += sy;
            }

            if (nextX == x1 && nextY == y1) break;

            double intermediateElevation = getElevation(terrainMap, nextX, nextY);

            double distanceToCurrent = Math.sqrt(
                    Math.pow(nextX - x0, 2) + Math.pow(nextY - y0, 2)
            ) * defaultGridResolution;
            double distanceToTarget = Math.sqrt(
                    Math.pow(x1 - nextX, 2) + Math.pow(y1 - nextY, 2)
            ) * defaultGridResolution;

            if (totalDistance <= 0) continue;
            double t = distanceToCurrent / totalDistance;

            double losHeight = observerHeight * (1.0 - t) + targetElevation * t;

            if (intermediateElevation > losHeight) {
                return false;
            }

            currentX = nextX;
            currentY = nextY;
        }

        return true;
    }

    private List<VisionResult.SectorAnalysis> analyzeSectors(Map<String, TerrainElevation> terrainMap,
                                                              int obsX, int obsY,
                                                              double observerTotalHeight,
                                                              int maxGridRadius) {
        List<VisionResult.SectorAnalysis> sectors = new ArrayList<>();
        int sectorCount = 36;
        double sectorAngle = 360.0 / sectorCount;

        for (int i = 0; i < sectorCount; i++) {
            double azimuth = i * sectorAngle;
            double azimuthRad = Math.toRadians(azimuth);

            double maxVisibleDist = 0.0;
            boolean blocked = false;
            double minElev = Double.MAX_VALUE;
            double maxElev = Double.MIN_VALUE;

            for (int r = 1; r <= maxGridRadius; r++) {
                int checkX = obsX + (int) Math.round(r * Math.cos(azimuthRad));
                int checkY = obsY + (int) Math.round(r * Math.sin(azimuthRad));

                double elev = getElevation(terrainMap, checkX, checkY);
                minElev = Math.min(minElev, elev);
                maxElev = Math.max(maxElev, elev);

                double distance = r * defaultGridResolution;

                if (isLineOfSightClear(terrainMap, obsX, obsY, observerTotalHeight,
                        checkX, checkY, distance)) {
                    maxVisibleDist = distance;
                } else {
                    blocked = true;
                    break;
                }
            }

            sectors.add(VisionResult.SectorAnalysis.builder()
                    .azimuth(azimuth)
                    .minElevation(minElev == Double.MAX_VALUE ? 0 : minElev)
                    .maxElevation(maxElev == Double.MIN_VALUE ? 0 : maxElev)
                    .visibleDistance(maxVisibleDist)
                    .isBlocked(blocked)
                    .build());
        }

        return sectors;
    }

    private double getElevation(Map<String, TerrainElevation> terrainMap, int x, int y) {
        String key = x + "," + y;
        TerrainElevation te = terrainMap.get(key);
        if (te != null) {
            return te.getElevation();
        }
        return 0.0;
    }

    private void saveAnalysisResult(UUID cartId, double height, VisionResult result) {
        try {
            String visibleGridJson = convertGridToJson(result.getVisibleGridPoints());
            visionAnalysisResultRepository.save(VisionAnalysisResult.builder()
                    .cartId(cartId)
                    .height(height)
                    .visiblePoints(result.getVisiblePoints())
                    .totalPoints(result.getTotalPoints())
                    .coverageRatio(result.getCoverageRatio())
                    .maxVisibleDistance(result.getMaxVisibleDistance())
                    .visibleGrid(visibleGridJson)
                    .build());
        } catch (Exception e) {
            log.warn("保存视野分析结果失败", e);
        }
    }

    private String convertGridToJson(List<int[]> points) {
        if (points == null || points.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("[").append(points.get(i)[0]).append(",").append(points.get(i)[1]).append("]");
        }
        sb.append("]");
        return sb.toString();
    }

    public double calculateTheoreticalHorizon(double height) {
        return Math.sqrt(2.0 * earthRadius * height);
    }
}
