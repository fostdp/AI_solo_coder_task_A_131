package com.nestcart.controller;

import com.nestcart.entity.TerrainElevation;
import com.nestcart.repository.TerrainElevationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/terrain")
@RequiredArgsConstructor
public class TerrainController {

    private final TerrainElevationRepository terrainElevationRepository;

    @GetMapping("/{regionName}")
    public ResponseEntity<List<TerrainElevation>> getTerrainByRegion(@PathVariable String regionName) {
        return ResponseEntity.ok(terrainElevationRepository.findByRegionName(regionName));
    }

    @GetMapping("/{regionName}/range")
    public ResponseEntity<List<TerrainElevation>> getTerrainByRange(
            @PathVariable String regionName,
            @RequestParam int xMin, @RequestParam int xMax,
            @RequestParam int yMin, @RequestParam int yMax) {
        return ResponseEntity.ok(terrainElevationRepository.findByRegionAndGridRange(
                regionName, xMin, xMax, yMin, yMax));
    }
}
