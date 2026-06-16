package com.nestcart.repository;

import com.nestcart.entity.TerrainElevation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TerrainElevationRepository extends JpaRepository<TerrainElevation, UUID> {

    List<TerrainElevation> findByRegionName(String regionName);

    @Query("SELECT te FROM TerrainElevation te WHERE te.regionName = :regionName " +
            "AND te.gridX BETWEEN :xMin AND :xMax AND te.gridY BETWEEN :yMin AND :yMax")
    List<TerrainElevation> findByRegionAndGridRange(
            @Param("regionName") String regionName,
            @Param("xMin") int xMin, @Param("xMax") int xMax,
            @Param("yMin") int yMin, @Param("yMax") int yMax);

    @Query("SELECT te FROM TerrainElevation te WHERE te.regionName = :regionName " +
            "AND te.gridX = :x AND te.gridY = :y")
    List<TerrainElevation> findByRegionAndGridPoint(
            @Param("regionName") String regionName,
            @Param("x") int x, @Param("y") int y);

    TerrainElevation findByGridXAndGridYAndRegionName(int gridX, int gridY, String regionName);

    List<TerrainElevation> findTopByRegionNameOrderByGridXDesc(String regionName);

    List<TerrainElevation> findTopByRegionNameOrderByGridYDesc(String regionName);
}
