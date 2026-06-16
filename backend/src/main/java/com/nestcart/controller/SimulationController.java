package com.nestcart.controller;

import com.nestcart.dto.SimulationResult;
import com.nestcart.dto.VisionResult;
import com.nestcart.service.StructureSimulationService;
import com.nestcart.service.VisionAnalysisService;
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
        return ResponseEntity.ok(visionAnalysisService.analyzeVision(
                cartId, height, regionName, observerGridX, observerGridY));
    }

    @GetMapping("/vision/horizon")
    public ResponseEntity<Double> calculateHorizon(@RequestParam double height) {
        return ResponseEntity.ok(visionAnalysisService.calculateTheoreticalHorizon(height));
    }
}
