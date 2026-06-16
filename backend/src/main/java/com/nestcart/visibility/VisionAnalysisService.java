package com.nestcart.visibility;

import com.nestcart.config.properties.VisionAnalysisProperties;
import com.nestcart.dto.VisionResult;
import com.nestcart.entity.NestCart;
import com.nestcart.entity.SensorData;
import com.nestcart.entity.TerrainElevation;
import com.nestcart.event.SensorDataReceivedEvent;
import com.nestcart.event.VisionAnalyzedEvent;
import com.nestcart.repository.NestCartRepository;
import com.nestcart.repository.SensorDataRepository;
import com.nestcart.repository.TerrainElevationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VisionAnalysisService {

    private final TerrainElevationRepository terrainRepository;
    private final NestCartRepository nestCartRepository;
    private final SensorDataRepository sensorDataRepository;
    private final TerrainQuadtreeService quadtreeService;
    private final VisionAnalysisProperties props;
    private final ApplicationEventPublisher eventPublisher;

    public VisionResult analyzeVision(UUID cartId, double observerHeight, double analysisRadius, String regionName) {
        NestCart cart = nestCartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("巢车不存在: " + cartId));

        String region = regionName != null ? regionName : props.getDefaultRegion();

        double earthRadius = props.getEarthRadius();
        double horizonDistance = Math.sqrt(2 * earthRadius * observerHeight + observerHeight * observerHeight);
        double effectiveRadius = Math.min(analysisRadius, horizonDistance);

        int gridSizeX = getGridSizeX(region);
        int gridSizeY = getGridSizeY(region);
        double resolution = props.getDefaultGridResolution();

        int observerGridX = gridSizeX / 2;
        int observerGridY = gridSizeY / 2;

        int radiusInCells = (int) Math.ceil(effectiveRadius / resolution);
        radiusInCells = Math.min(radiusInCells, Math.min(gridSizeX / 2, gridSizeY / 2));

        boolean useQuadtree = props.isUseQuadtree();

        int visiblePoints = 0;
        int totalPoints = 0;
        double maxVisibleDistance = 0;
        List<VisionResult.GridPoint> visiblePointList = new ArrayList<>();
        List<VisionResult.GridPoint> blockedPointList = new ArrayList<>();

        if (useQuadtree) {
            quadtreeService.ensureTreeBuilt(region);
        }

        int sectorCount = props.getSectorCount();
        List<VisionResult.SectorAnalysis> sectors = new ArrayList<>();

        for (int s = 0; s < sectorCount; s++) {
            double azimuth = s * (360.0 / sectorCount);
            double minElev = Double.MAX_VALUE;
            double maxElev = Double.MIN_VALUE;
            double sectorVisibleDist = 0;

            int steps = radiusInCells;
            for (int step = 1; step <= steps; step++) {
                double dist = step * resolution;
                double angleRad = Math.toRadians(azimuth);
                int targetX = observerGridX + (int) Math.round(step * Math.cos(angleRad));
                int targetY = observerGridY + (int) Math.round(step * Math.sin(angleRad));

                if (targetX < 0 || targetX >= gridSizeX || targetY < 0 || targetY >= gridSizeY) {
                    break;
                }

                totalPoints++;
                boolean isVisible;

                if (useQuadtree) {
                    isVisible = quadtreeService.isLineOfSightClear(
                            region, observerGridX, observerGridY, observerHeight,
                            targetX, targetY);
                } else {
                    isVisible = isLineOfSightBresenham(
                            observerGridX, observerGridY, observerHeight,
                            targetX, targetY, region);
                }

                double elev = getTerrainElevation(targetX, targetY, region);
                minElev = Math.min(minElev, elev);
                maxElev = Math.max(maxElev, elev);

                if (isVisible) {
                    visiblePoints++;
                    sectorVisibleDist = dist;
                    maxVisibleDistance = Math.max(maxVisibleDistance, dist);
                    if (visiblePointList.size() < 5000) {
                        visiblePointList.add(new VisionResult.GridPoint(targetX, targetY, elev));
                    }
                } else {
                    if (blockedPointList.size() < 5000) {
                        blockedPointList.add(new VisionResult.GridPoint(targetX, targetY, elev));
                    }
                }
            }

            sectors.add(VisionResult.SectorAnalysis.builder()
                    .azimuth(azimuth)
                    .visibleDistance(sectorVisibleDist)
                    .isBlocked(sectorVisibleDist < effectiveRadius * 0.9)
                    .minElevation(minElev == Double.MAX_VALUE ? 0 : minElev)
                    .maxElevation(maxElev == Double.MIN_VALUE ? 0 : maxElev)
                    .build());
        }

        double coverageRatio = totalPoints > 0 ? (double) visiblePoints / totalPoints : 0;

        VisionResult result = VisionResult.builder()
                .cartId(cartId)
                .observerHeight(observerHeight)
                .analysisRadius(effectiveRadius)
                .theoreticalHorizon(horizonDistance)
                .maxVisibleDistance(maxVisibleDistance)
                .visiblePoints(visiblePoints)
                .totalPoints(totalPoints)
                .coverageRatio(coverageRatio)
                .visiblePointList(visiblePointList)
                .blockedPointList(blockedPointList)
                .sectorAnalysis(sectors)
                .build();

        eventPublisher.publishEvent(new VisionAnalyzedEvent(this, cartId, result));

        return result;
    }

    public VisionResult analyzeVisionWithLatest(UUID cartId, Double radius, String regionName) {
        List<SensorData> latestData = sensorDataRepository.findLatestByCartId(cartId, PageRequest.of(0, 1));
        double height = latestData.isEmpty() ? 10.0 : latestData.get(0).getHeight();
        double analysisRadius = radius != null ? radius : props.getMaxAnalysisRadius();
        return analyzeVision(cartId, height, analysisRadius, regionName);
    }

    @Async
    @EventListener
    public void onSensorDataReceived(SensorDataReceivedEvent event) {
        try {
            SensorData data = event.getSensorData();
            log.debug("收到传感器数据事件，触发视野分析: cartId={}", data.getCartId());
            analyzeVision(data.getCartId(), data.getHeight(),
                    props.getMaxAnalysisRadius(), props.getDefaultRegion());
        } catch (Exception e) {
            log.warn("事件驱动视野分析失败: {}", e.getMessage());
        }
    }

    private boolean isLineOfSightBresenham(int x0, int y0, double h0,
                                           int x1, int y1, String regionName) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        int x = x0;
        int y = y0;

        while (true) {
            if (x == x1 && y == y1) break;

            double distRatio = dx + dy == 0 ? 0 :
                    (Math.abs(x - x0) + Math.abs(y - y0)) / (double) (dx + dy);

            double lineOfSightHeight = h0 - distRatio * distRatio * 0;
            double curvatureDrop = getEarthCurvatureDrop(distRatio * Math.max(dx, dy) * props.getDefaultGridResolution());
            double terrainHeight = getTerrainElevation(x, y, regionName) - curvatureDrop;

            if (terrainHeight > lineOfSightHeight && !(x == x0 && y == y0)) {
                return false;
            }

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }

        return true;
    }

    private double getEarthCurvatureDrop(double distance) {
        return distance * distance / (2 * props.getEarthRadius());
    }

    private double getTerrainElevation(int gridX, int gridY, String regionName) {
        TerrainElevation terrain = terrainRepository.findByGridXAndGridYAndRegionName(gridX, gridY, regionName);
        return terrain != null ? terrain.getElevation() : 0.0;
    }

    private int getGridSizeX(String regionName) {
        List<TerrainElevation> maxXList = terrainRepository.findTopByRegionNameOrderByGridXDesc(regionName);
        if (maxXList.isEmpty()) return 100;
        return maxXList.get(0).getGridX() + 1;
    }

    private int getGridSizeY(String regionName) {
        List<TerrainElevation> maxYList = terrainRepository.findTopByRegionNameOrderByGridYDesc(regionName);
        if (maxYList.isEmpty()) return 100;
        return maxYList.get(0).getGridY() + 1;
    }
}
