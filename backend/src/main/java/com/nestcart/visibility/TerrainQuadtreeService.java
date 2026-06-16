package com.nestcart.visibility;

import com.nestcart.config.properties.VisionAnalysisProperties;
import com.nestcart.entity.TerrainElevation;
import com.nestcart.repository.TerrainElevationRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class TerrainQuadtreeService {

    private final TerrainElevationRepository terrainRepository;
    private final VisionAnalysisProperties props;

    private final ConcurrentHashMap<String, QuadtreeNode> regionTreeCache = new ConcurrentHashMap<>();

    public void buildTree(String regionName, List<TerrainElevation> terrainData) {
        if (terrainData == null || terrainData.isEmpty()) {
            log.warn("地形数据为空，无法构建四叉树: {}", regionName);
            return;
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        double minElev = Double.MAX_VALUE, maxElev = -Double.MAX_VALUE;

        for (TerrainElevation te : terrainData) {
            minX = Math.min(minX, te.getGridX());
            minY = Math.min(minY, te.getGridY());
            maxX = Math.max(maxX, te.getGridX());
            maxY = Math.max(maxY, te.getGridY());
            minElev = Math.min(minElev, te.getElevation());
            maxElev = Math.max(maxElev, te.getElevation());
        }

        QuadtreeNode root = new QuadtreeNode(minX, minY, maxX, maxY, minElev, maxElev, false);

        buildSubtree(root, terrainData, props.getQuadtreeMaxDepth());

        regionTreeCache.put(regionName, root);
        log.info("四叉树构建完成: region={}, depth={}, nodes={}",
                regionName, props.getQuadtreeMaxDepth(), countNodes(root));
    }

    private void buildSubtree(QuadtreeNode node, List<TerrainElevation> allData, int depthLeft) {
        if (depthLeft <= 0) {
            node.setLeaf(true);
            return;
        }

        int cellCount = (node.getMaxX() - node.getMinX()) * (node.getMaxY() - node.getMinY());
        if (cellCount <= props.getQuadtreeMinCells() * props.getQuadtreeMinCells()) {
            node.setLeaf(true);
            return;
        }

        int midX = (node.getMinX() + node.getMaxX()) / 2;
        int midY = (node.getMinY() + node.getMaxY()) / 2;

        double[][] quadrantRanges = {
                {node.getMinX(), node.getMinY(), midX, midY},
                {midX + 1, node.getMinY(), node.getMaxX(), midY},
                {node.getMinX(), midY + 1, midX, node.getMaxY()},
                {midX + 1, midY + 1, node.getMaxX(), node.getMaxY()}
        };

        QuadtreeNode[] children = new QuadtreeNode[4];

        for (int i = 0; i < 4; i++) {
            int qMinX = (int) quadrantRanges[i][0];
            int qMinY = (int) quadrantRanges[i][1];
            int qMaxX = (int) quadrantRanges[i][2];
            int qMaxY = (int) quadrantRanges[i][3];

            if (qMinX > qMaxX || qMinY > qMaxY) {
                children[i] = null;
                continue;
            }

            double[] elevRange = getElevationRange(allData, qMinX, qMinY, qMaxX, qMaxY);

            QuadtreeNode child = new QuadtreeNode(qMinX, qMinY, qMaxX, qMaxY,
                    elevRange[0], elevRange[1], false);
            children[i] = child;

            buildSubtree(child, allData, depthLeft - 1);
        }

        node.setChildren(children);
    }

    private double[] getElevationRange(List<TerrainElevation> allData,
                                        int minX, int minY, int maxX, int maxY) {
        double minElev = Double.MAX_VALUE;
        double maxElev = -Double.MAX_VALUE;

        for (TerrainElevation te : allData) {
            if (te.getGridX() >= minX && te.getGridX() <= maxX
                    && te.getGridY() >= minY && te.getGridY() <= maxY) {
                minElev = Math.min(minElev, te.getElevation());
                maxElev = Math.max(maxElev, te.getElevation());
            }
        }

        if (minElev == Double.MAX_VALUE) {
            minElev = 0;
            maxElev = 0;
        }

        return new double[]{minElev, maxElev};
    }

    public void ensureTreeBuilt(String regionName) {
        if (regionTreeCache.containsKey(regionName)) {
            return;
        }

        log.info("按需构建四叉树: region={}", regionName);
        List<TerrainElevation> terrainData = terrainRepository.findByRegionName(regionName);
        buildTree(regionName, terrainData);
    }

    public boolean isLineOfSightClear(String regionName, int obsX, int obsY, double obsHeight,
                                      int targetX, int targetY) {
        ensureTreeBuilt(regionName);
        QuadtreeNode root = regionTreeCache.get(regionName);
        if (root == null) return true;

        double dx = targetX - obsX;
        double dy = targetY - obsY;
        double dist = Math.sqrt(dx * dx + dy * dy);
        double resolution = props.getDefaultGridResolution();
        double distMeters = dist * resolution;

        return isLOSClearRecursive(root, obsX, obsY, obsHeight,
                targetX, targetY, distMeters, resolution);
    }

    private boolean isLOSClearRecursive(QuadtreeNode node, int obsX, int obsY, double obsHeight,
                                        int targetX, int targetY, double totalDist, double resolution) {
        if (node == null) return true;

        if (!lineIntersectsNode(node, obsX, obsY, targetX, targetY)) {
            return true;
        }

        double minLOSHeight = getMinLOSHeightToNode(node, obsX, obsY, obsHeight, targetX, targetY, totalDist, resolution);
        double maxLOSHeight = getMaxLOSHeightAtNode(node, obsX, obsY, obsHeight, targetX, targetY, totalDist, resolution);

        if (node.getMinElevation() >= maxLOSHeight) {
            return false;
        }

        if (node.getMaxElevation() <= minLOSHeight) {
            return true;
        }

        if (node.isLeaf()) {
            return checkNodeDetail(node, obsX, obsY, obsHeight, targetX, targetY, totalDist, resolution);
        }

        for (QuadtreeNode child : node.getChildren()) {
            if (child != null) {
                if (!isLOSClearRecursive(child, obsX, obsY, obsHeight,
                        targetX, targetY, totalDist, resolution)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean lineIntersectsNode(QuadtreeNode node, int x0, int y0, int x1, int y1) {
        return Math.max(x0, x1) >= node.getMinX() && Math.min(x0, x1) <= node.getMaxX()
                && Math.max(y0, y1) >= node.getMinY() && Math.min(y0, y1) <= node.getMaxY();
    }

    private double getMinLOSHeightToNode(QuadtreeNode node, int obsX, int obsY, double obsHeight,
                                          int targetX, int targetY, double totalDist, double resolution) {
        double[] distances = getDistancesToNodeCorners(node, obsX, obsY, targetX, targetY, resolution);

        double minHeight = obsHeight;
        for (double d : distances) {
            double ratio = d / totalDist;
            double curvatureDrop = d * d / (2 * props.getEarthRadius());
            double lineHeight = obsHeight * (1 - ratio) + 0 * ratio;
            minHeight = Math.min(minHeight, lineHeight - curvatureDrop);
        }

        return minHeight;
    }

    private double getMaxLOSHeightAtNode(QuadtreeNode node, int obsX, int obsY, double obsHeight,
                                          int targetX, int targetY, double totalDist, double resolution) {
        double[] distances = getDistancesToNodeCorners(node, obsX, obsY, targetX, targetY, resolution);

        double maxHeight = 0;
        for (double d : distances) {
            double ratio = d / totalDist;
            double lineHeight = obsHeight * (1 - ratio) + 0 * ratio;
            maxHeight = Math.max(maxHeight, lineHeight);
        }

        return maxHeight;
    }

    private double[] getDistancesToNodeCorners(QuadtreeNode node, int obsX, int obsY,
                                                int targetX, int targetY, double resolution) {
        double lineLength = Math.sqrt(Math.pow(targetX - obsX, 2) + Math.pow(targetY - obsY, 2));
        if (lineLength == 0) return new double[]{0};

        double t0 = projectPointToLine(node.getMinX(), node.getMinY(), obsX, obsY, targetX, targetY);
        double t1 = projectPointToLine(node.getMaxX(), node.getMinY(), obsX, obsY, targetX, targetY);
        double t2 = projectPointToLine(node.getMinX(), node.getMaxY(), obsX, obsY, targetX, targetY);
        double t3 = projectPointToLine(node.getMaxX(), node.getMaxY(), obsX, obsY, targetX, targetY);

        double tMin = Math.min(Math.min(t0, t1), Math.min(t2, t3));
        double tMax = Math.max(Math.max(t0, t1), Math.max(t2, t3));

        tMin = Math.max(0, Math.min(1, tMin));
        tMax = Math.max(0, Math.min(1, tMax));

        return new double[]{tMin * lineLength * resolution, tMax * lineLength * resolution};
    }

    private double projectPointToLine(int px, int py, int x0, int y0, int x1, int y1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double lenSq = dx * dx + dy * dy;
        if (lenSq == 0) return 0;

        double t = ((px - x0) * dx + (py - y0) * dy) / lenSq;
        return Math.max(0, Math.min(1, t));
    }

    private boolean checkNodeDetail(QuadtreeNode node, int obsX, int obsY, double obsHeight,
                                    int targetX, int targetY, double totalDist, double resolution) {
        int dx = Math.abs(targetX - obsX);
        int dy = Math.abs(targetY - obsY);
        int sx = obsX < targetX ? 1 : -1;
        int sy = obsY < targetY ? 1 : -1;
        int err = dx - dy;

        int x = obsX;
        int y = obsY;

        while (true) {
            if (x == targetX && y == targetY) break;

            if (x >= node.getMinX() && x <= node.getMaxX()
                    && y >= node.getMinY() && y <= node.getMaxY()) {
                double distFromObs = Math.sqrt(Math.pow(x - obsX, 2) + Math.pow(y - obsY, 2)) * resolution;
                double ratio = totalDist > 0 ? distFromObs / totalDist : 0;
                double curvatureDrop = distFromObs * distFromObs / (2 * props.getEarthRadius());

                double lineHeight = obsHeight * (1 - ratio);
                double terrainHeight = node.getMaxElevation() - curvatureDrop;

                if (terrainHeight > lineHeight) {
                    return false;
                }
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

    public int countVisiblePoints(String regionName, int obsX, int obsY, double obsHeight,
                                  int radius) {
        ensureTreeBuilt(regionName);
        QuadtreeNode root = regionTreeCache.get(regionName);
        if (root == null) return 0;

        return countVisibleRecursive(root, obsX, obsY, obsHeight, obsX - radius, obsY - radius,
                obsX + radius, obsY + radius);
    }

    private int countVisibleRecursive(QuadtreeNode node, int obsX, int obsY, double obsHeight,
                                       int minX, int minY, int maxX, int maxY) {
        if (node == null) return 0;

        if (node.getMaxX() < minX || node.getMinX() > maxX
                || node.getMaxY() < minY || node.getMinY() > maxY) {
            return 0;
        }

        double distToNode = getDistanceToNodeCenter(node, obsX, obsY);
        double maxDist = Math.max(maxX - obsX, obsX - minX);

        if (node.getMaxElevation() <= obsHeight - distToNode * 0.001) {
            int nodeCells = (node.getMaxX() - node.getMinX() + 1) * (node.getMaxY() - node.getMinY() + 1);
            return nodeCells;
        }

        if (node.isLeaf()) {
            int count = 0;
            for (int x = Math.max(node.getMinX(), minX); x <= Math.min(node.getMaxX(), maxX); x++) {
                for (int y = Math.max(node.getMinY(), minY); y <= Math.min(node.getMaxY(), maxY); y++) {
                    if (isLineOfSightClear(null, obsX, obsY, obsHeight, x, y)) {
                        count++;
                    }
                }
            }
            return count;
        }

        int total = 0;
        for (QuadtreeNode child : node.getChildren()) {
            total += countVisibleRecursive(child, obsX, obsY, obsHeight, minX, minY, maxX, maxY);
        }
        return total;
    }

    private double getDistanceToNodeCenter(QuadtreeNode node, int x, int y) {
        int centerX = (node.getMinX() + node.getMaxX()) / 2;
        int centerY = (node.getMinY() + node.getMaxY()) / 2;
        return Math.sqrt(Math.pow(centerX - x, 2) + Math.pow(centerY - y, 2));
    }

    private int countNodes(QuadtreeNode node) {
        if (node == null) return 0;
        int count = 1;
        if (node.getChildren() != null) {
            for (QuadtreeNode child : node.getChildren()) {
                count += countNodes(child);
            }
        }
        return count;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    static class QuadtreeNode {
        private int minX;
        private int minY;
        private int maxX;
        private int maxY;
        private double minElevation;
        private double maxElevation;
        private boolean isLeaf;
        private QuadtreeNode[] children;

        public QuadtreeNode(int minX, int minY, int maxX, int maxY,
                            double minElev, double maxElev, boolean isLeaf) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.minElevation = minElev;
            this.maxElevation = maxElev;
            this.isLeaf = isLeaf;
        }
    }
}
