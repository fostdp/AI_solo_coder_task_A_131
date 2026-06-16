package com.nestcart.visibility;

import com.nestcart.dto.VisionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/vision")
@RequiredArgsConstructor
public class VisionAnalysisController {

    private final VisionAnalysisService visionAnalysisService;

    @PostMapping("/analyze/{cartId}")
    public ResponseEntity<VisionResult> analyzeVision(
            @PathVariable UUID cartId,
            @RequestParam(required = false) Double height,
            @RequestParam(required = false) Double radius,
            @RequestParam(required = false) String regionName) {
        double h = height != null ? height : 10.0;
        double r = radius != null ? radius : 5000.0;
        return ResponseEntity.ok(visionAnalysisService.analyzeVision(cartId, h, r, regionName));
    }

    @GetMapping("/analyze/{cartId}/latest")
    public ResponseEntity<VisionResult> analyzeWithLatest(
            @PathVariable UUID cartId,
            @RequestParam(required = false) Double radius,
            @RequestParam(required = false) String regionName) {
        return ResponseEntity.ok(visionAnalysisService.analyzeVisionWithLatest(cartId, radius, regionName));
    }
}
