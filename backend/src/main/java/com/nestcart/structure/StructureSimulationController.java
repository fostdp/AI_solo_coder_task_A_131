package com.nestcart.structure;

import com.nestcart.dto.SimulationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/structure")
@RequiredArgsConstructor
public class StructureSimulationController {

    private final StructureSimulationService structureSimulationService;

    @PostMapping("/simulate/{cartId}")
    public ResponseEntity<SimulationResult> runSimulation(
            @PathVariable UUID cartId,
            @RequestParam(required = false) Double height,
            @RequestParam(required = false) Double windSpeed,
            @RequestParam(required = false) Double windDirection) {
        return ResponseEntity.ok(structureSimulationService.simulate(cartId, height, windSpeed, windDirection));
    }

    @GetMapping("/simulate/{cartId}/latest")
    public ResponseEntity<SimulationResult> runSimulationWithLatest(
            @PathVariable UUID cartId) {
        return ResponseEntity.ok(structureSimulationService.simulateWithLatestData(cartId));
    }
}
