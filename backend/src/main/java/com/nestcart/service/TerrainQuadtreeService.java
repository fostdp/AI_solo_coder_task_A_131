package com.nestcart.service;

import com.nestcart.entity.TerrainElevation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地形四叉树空间索引服务
 * 用于加速通视分析（Line-of-Sight）中的地形查询
 *
 * 原理：
 * - 将地形网格递归四叉划分，每个节点存储该区域的最大/最小高程
 * - 通视检查时，利用节点的极值快速排除可见或遮挡的大片区域
 * - 时间复杂度从 O(n) 降至 O(log n)
 */
@Service
@Slf4j
public class TerrainQuadtreeService {

    private final Map<String, QuadTreeNode> regionTreeCache = new ConcurrentHashMap<>();

    private static final int DEFAULT_MAX_DEPTH = 6;
    private static final int DEFAULT_MIN_CELLS = 4;

    public void buildTree(String regionName, List<TerrainElevation> terrainData) {
        if (terrainData == null || terrainData.isEmpty()) return;

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

        for (TerrainElevation te : terrainData) {
            minX = Math.min(minX, te.getGridX());
            maxX = Math.max(maxX, te.getGridX());
            minY = Math.min(minY, te.getGridY());
            maxY = Math.max(maxY, te.getGridY());
        }

        double[][] elevationGrid = new double[maxX - minX + 1][maxY - minY + 1];
        for (TerrainElevation te : terrainData) {
            elevationGrid[te.getGridX() - minX][te.getGridY() - minY] = te.getElevation();
        }

        QuadTreeNode root = buildNode(elevationGrid, 0, 0,
                maxX - minX, maxY - minY, 0, minX, minY);

        regionTreeCache.put(regionName, root);
        log.info("地形四叉树构建完成: region={}, 范围=[{},{}]-[{},{}], 深度={}",
                regionName, minX, minY, maxX, maxY, getTreeDepth(root));
    }

    private QuadTreeNode buildNode(double[][] grid, int x0, int y0, int x1, int y1,
                                    int depth, int gridOffsetX, int gridOffsetY) {
        double minElev = Double.MAX_VALUE;
        double maxElev = -Double.MAX_VALUE;
        boolean hasData = false;

        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                if (x >= 0 && x < grid.length && y >= 0 && y < grid[0].length) {
                    double elev = grid[x][y];
                    minElev = Math.min(minElev, elev);
                    maxElev = Math.max(maxElev, elev);
                    hasData = true;
                }
            }
        }

        QuadTreeNode node = new QuadTreeNode();
        node.minX = x0 + gridOffsetX;
        node.minY = y0 + gridOffsetY;
        node.maxX = x1 + gridOffsetX;
        node.maxY = y1 + gridOffsetY;
        node.minElevation = hasData ? minElev : 0.0;
        node.maxElevation = hasData ? maxElev : 0.0;
        node.depth = depth;
        node.isLeaf = (x1 - x0 <= DEFAULT_MIN_CELLS) || (depth >= DEFAULT_MAX_DEPTH);

        if (!node.isLeaf) {
            int midX = (x0 + x1) / 2;
            int midY = (y0 + y1) / 2;

            node.children = new QuadTreeNode[4];
            node.children[0] = buildNode(grid, x0, y0, midX, midY, depth + 1, gridOffsetX, gridOffsetY);
            node.children[1] = buildNode(grid, midX + 1, y0, x1, midY, depth + 1, gridOffsetX, gridOffsetY);
            node.children[2] = buildNode(grid, x0, midY + 1, midX, y1, depth + 1, gridOffsetX, gridOffsetY);
            node.children[3] = buildNode(grid, midX + 1, midY + 1, x1, y1, depth + 1, gridOffsetX, gridOffsetY);
        }

        return node;
    }

    private int getTreeDepth(QuadTreeNode node) {
        if (node.isLeaf) return node.depth + 1;
        int maxDepth = 0;
        for (QuadTreeNode child : node.children) {
            if (child != null) {
                maxDepth = Math.max(maxDepth, getTreeDepth(child));
            }
        }
        return maxDepth;
    }

    public boolean isLineOfSightClear(String regionName,
                                      int obsX, int obsY, double obsHeight,
                                      int targetX, int targetY, double targetHeight,
                                      double gridResolution) {
        QuadTreeNode root = regionTreeCache.get(regionName);
        if (root == null) {
            throw new IllegalStateException("区域四叉树未构建: " + regionName);
        }

        return lineOfSightTraversal(root, obsX, obsY, obsHeight,
                targetX, targetY, targetHeight, gridResolution);
    }

    public boolean isLineOfSightClear(QuadTreeNode node,
                                      int obsX, int obsY, double obsHeight,
                                      int targetX, int targetY, double gridResolution) {
        return checkLeafNodeLOS(node, obsX, obsY, obsHeight,
                targetX, targetY, 0.0, gridResolution);
    }

    private boolean lineOfSightTraversal(QuadTreeNode node,
                                          int obsX, int obsY, double obsHeight,
                                          int targetX, int targetY, double targetHeight,
                                          double resolution) {
        if (!nodeIntersectsLine(node, obsX, obsY, targetX, targetY)) {
            return true;
        }

        double minLOSHeight = getMinLineHeight(node, obsX, obsY, obsHeight,
                targetX, targetY, targetHeight);
        double maxLOSHeight = getMaxLineHeight(node, obsX, obsY, obsHeight,
                targetX, targetY, targetHeight);

        if (node.maxElevation <= minLOSHeight) {
            return true;
        }

        if (node.minElevation > maxLOSHeight) {
            return false;
        }

        if (node.isLeaf) {
            return checkLeafNodeLOS(node, obsX, obsY, obsHeight,
                    targetX, targetY, targetHeight, resolution);
        }

        for (QuadTreeNode child : node.children) {
            if (child == null) continue;
            if (!nodeIntersectsLine(child, obsX, obsY, targetX, targetY)) continue;

            if (!lineOfSightTraversal(child, obsX, obsY, obsHeight,
                    targetX, targetY, targetHeight, resolution)) {
                return false;
            }
        }

        return true;
    }

    private boolean nodeIntersectsLine(QuadTreeNode node, int x0, int y0, int x1, int y1) {
        int minX = Math.min(x0, x1);
        int maxX = Math.max(x0, x1);
        int minY = Math.min(y0, y1);
        int maxY = Math.max(y0, y1);

        return !(node.maxX < minX || node.minX > maxX ||
                node.maxY < minY || node.minY > maxY);
    }

    private double getMinLineHeight(QuadTreeNode node,
                                     int obsX, int obsY, double obsHeight,
                                     int targetX, int targetY, double targetHeight) {
        double h1 = getLineHeightAtPoint(obsX, obsY, obsHeight, targetX, targetY, targetHeight,
                node.minX, node.minY);
        double h2 = getLineHeightAtPoint(obsX, obsY, obsHeight, targetX, targetY, targetHeight,
                node.maxX, node.minY);
        double h3 = getLineHeightAtPoint(obsX, obsY, obsHeight, targetX, targetY, targetHeight,
                node.minX, node.maxY);
        double h4 = getLineHeightAtPoint(obsX, obsY, obsHeight, targetX, targetY, targetHeight,
                node.maxX, node.maxY);

        return Math.min(Math.min(h1, h2), Math.min(h3, h4));
    }

    private double getMaxLineHeight(QuadTreeNode node,
                                     int obsX, int obsY, double obsHeight,
                                     int targetX, int targetY, double targetHeight) {
        double h1 = getLineHeightAtPoint(obsX, obsY, obsHeight, targetX, targetY, targetHeight,
                node.minX, node.minY);
        double h2 = getLineHeightAtPoint(obsX, obsY, obsHeight, targetX, targetY, targetHeight,
                node.maxX, node.minY);
        double h3 = getLineHeightAtPoint(obsX, obsY, obsHeight, targetX, targetY, targetHeight,
                node.minX, node.maxY);
        double h4 = getLineHeightAtPoint(obsX, obsY, obsHeight, targetX, targetY, targetHeight,
                node.maxX, node.maxY);

        return Math.max(Math.max(h1, h2), Math.max(h3, h4));
    }

    private double getLineHeightAtPoint(int x0, int y0, double h0,
                                         int x1, int y1, double h1,
                                         int px, int py) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double totalDist = Math.sqrt(dx * dx + dy * dy);
        if (totalDist <= 0) return h0;

        double t = ((px - x0) * dx + (py - y0) * dy) / (totalDist * totalDist);
        t = Math.max(0, Math.min(1, t));

        return h0 + (h1 - h0) * t;
    }

    private boolean checkLeafNodeLOS(QuadTreeNode node,
                                      int obsX, int obsY, double obsHeight,
                                      int targetX, int targetY, double targetHeight,
                                      double resolution) {
        int dx = targetX - obsX;
        int dy = targetY - obsY;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist <= 1) return true;

        int steps = Math.max(1, (int) Math.ceil(dist));
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            int px = (int) Math.round(obsX + dx * t);
            int py = (int) Math.round(obsY + dy * t);

            if (px < node.minX || px > node.maxX || py < node.minY || py > node.maxY) {
                continue;
            }

            double lineHeight = obsHeight + (targetHeight - obsHeight) * t;
            double terrainElev = getElevationAt(node, px, py);

            if (terrainElev > lineHeight) {
                return false;
            }
        }

        return true;
    }

    private double getElevationAt(QuadTreeNode node, int x, int y) {
        if (!node.isLeaf) {
            for (QuadTreeNode child : node.children) {
                if (child != null && x >= child.minX && x <= child.maxX
                        && y >= child.minY && y <= child.maxY) {
                    return getElevationAt(child, x, y);
                }
            }
        }
        return (node.minElevation + node.maxElevation) / 2.0;
    }

    public int countVisiblePoints(String regionName,
                                   int obsX, int obsY, double obsHeight,
                                   int radius, double gridResolution) {
        QuadTreeNode root = regionTreeCache.get(regionName);
        if (root == null) {
            throw new IllegalStateException("区域四叉树未构建: " + regionName);
        }

        int[] count = new int[1];
        int targetHeight = 0;

        countVisibleTraversal(root, obsX, obsY, obsHeight, targetHeight,
                radius, gridResolution, count);

        return count[0];
    }

    private void countVisibleTraversal(QuadTreeNode node,
                                        int obsX, int obsY, double obsHeight,
                                        double targetHeight, int radius,
                                        double resolution, int[] count) {
        double dist = getMaxDistanceFromObserver(node, obsX, obsY);
        if (dist > radius) return;

        if (node.isLeaf) {
            for (int x = node.minX; x <= node.maxX; x++) {
                for (int y = node.minY; y <= node.maxY; y++) {
                    if (isLineOfSightQuick(node, obsX, obsY, obsHeight, x, y, targetHeight)) {
                        count[0]++;
                    }
                }
            }
            return;
        }

        double minLOS = getMinLineHeightToNode(node, obsX, obsY, obsHeight, targetHeight);
        if (node.maxElevation <= minLOS) {
            int points = (node.maxX - node.minX + 1) * (node.maxY - node.minY + 1);
            count[0] += points;
            return;
        }

        for (QuadTreeNode child : node.children) {
            if (child != null) {
                countVisibleTraversal(child, obsX, obsY, obsHeight,
                        targetHeight, radius, resolution, count);
            }
        }
    }

    private double getMaxDistanceFromObserver(QuadTreeNode node, int obsX, int obsY) {
        double d1 = Math.sqrt((node.minX - obsX) * (node.minX - obsX)
                + (node.minY - obsY) * (node.minY - obsY));
        double d2 = Math.sqrt((node.maxX - obsX) * (node.maxX - obsX)
                + (node.minY - obsY) * (node.minY - obsY));
        double d3 = Math.sqrt((node.minX - obsX) * (node.minX - obsX)
                + (node.maxY - obsY) * (node.maxY - obsY));
        double d4 = Math.sqrt((node.maxX - obsX) * (node.maxX - obsX)
                + (node.maxY - obsY) * (node.maxY - obsY));
        return Math.max(Math.max(d1, d2), Math.max(d3, d4));
    }

    private double getMinLineHeightToNode(QuadTreeNode node, int obsX, int obsY,
                                           double obsHeight, double targetHeight) {
        double minH = Double.MAX_VALUE;
        int[] xs = {node.minX, node.maxX};
        int[] ys = {node.minY, node.maxY};
        for (int x : xs) {
            for (int y : ys) {
                double dist = Math.sqrt((x - obsX) * (x - obsX) + (y - obsY) * (y - obsY));
                if (dist > 0) {
                    double h = obsHeight + (targetHeight - obsHeight) * (dist / (dist + 1));
                    minH = Math.min(minH, h);
                }
            }
        }
        return minH;
    }

    private boolean isLineOfSightQuick(QuadTreeNode node, int obsX, int obsY,
                                        double obsHeight, int targetX, int targetY,
                                        double targetHeight) {
        double dist = Math.sqrt((targetX - obsX) * (targetX - obsX)
                + (targetY - obsY) * (targetY - obsY));
        if (dist <= 1) return true;

        double midX = (obsX + targetX) / 2.0;
        double midY = (obsY + targetY) / 2.0;
        double midH = (obsHeight + targetHeight) / 2.0;

        int mx = (int) Math.round(midX);
        int my = (int) Math.round(midY);

        double midElev = (node.minElevation + node.maxElevation) / 2.0;
        if (midElev > midH) return false;

        if (dist < 2) return true;

        return isLineOfSightQuick(node, obsX, obsY, obsHeight, mx, my, midH)
                && isLineOfSightQuick(node, mx, my, midH, targetX, targetY, targetHeight);
    }

    public QuadTreeNode getRootNode(String regionName) {
        return regionTreeCache.get(regionName);
    }

    public boolean isTreeBuilt(String regionName) {
        return regionTreeCache.containsKey(regionName);
    }

    public void invalidateCache(String regionName) {
        regionTreeCache.remove(regionName);
    }

    public static class QuadTreeNode {
        public int minX, minY, maxX, maxY;
        public double minElevation;
        public double maxElevation;
        public int depth;
        public boolean isLeaf;
        public QuadTreeNode[] children;
    }
}
