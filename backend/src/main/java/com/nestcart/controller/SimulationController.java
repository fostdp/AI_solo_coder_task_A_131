package com.nestcart.controller;

import com.nestcart.dto.SimulationResult;
import com.nestcart.dto.VisionResult;
import com.nestcart.structure.StructureSimulationService;
import com.nestcart.visibility.VisionAnalysisService;
import com.nestcart.config.properties.VisionAnalysisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
public class SimulationController {

    private final StructureSimulationService structureSimulationService;
    private final VisionAnalysisService visionAnalysisService;
    private final VisionAnalysisProperties visionProps;

    @PostMapping("/structure/{cartId}")
    public ResponseEntity<SimulationResult> runStructureSimulation(
            @PathVariable UUID cartId,
            @RequestParam(required = false) Double height,
            @RequestParam(required = false) Double windSpeed,
            @RequestParam(required = false) Double windDirection) {
        return ResponseEntity.ok(structureSimulationService.simulate(cartId, height, windSpeed, windDirection));
    }

    @GetMapping("/structure/{cartId}/latest")
    public ResponseEntity<SimulationResult> runStructureSimulationWithLatest(
            @PathVariable UUID cartId) {
        return ResponseEntity.ok(structureSimulationService.simulateWithLatestData(cartId));
    }

    @PostMapping("/vision/{cartId}")
    public ResponseEntity<VisionResult> runVisionAnalysis(
            @PathVariable UUID cartId,
            @RequestParam(required = false) Double height,
            @RequestParam(required = false) String regionName,
            @RequestParam(required = false) Integer observerGridX,
            @RequestParam(required = false) Integer observerGridY) {
        double h = height != null ? height : 10.0;
        double r = visionProps.getMaxAnalysisRadius();
        String region = regionName != null ? regionName : visionProps.getDefaultRegion();
        return ResponseEntity.ok(visionAnalysisService.analyzeVision(cartId, h, r, region));
    }

    @GetMapping("/vision/horizon")
    public ResponseEntity<Double> calculateHorizon(@RequestParam double height) {
        double horizon = Math.sqrt(2 * visionProps.getEarthRadius() * height + height * height);
        return ResponseEntity.ok(horizon);
    }
}
