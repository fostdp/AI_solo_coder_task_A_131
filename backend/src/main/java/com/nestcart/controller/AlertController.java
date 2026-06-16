package com.nestcart.controller;

import com.nestcart.entity.AlertRecord;
import com.nestcart.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public ResponseEntity<List<AlertRecord>> getUnacknowledgedAlerts() {
        return ResponseEntity.ok(alertService.getUnacknowledgedAlerts());
    }

    @GetMapping("/cart/{cartId}")
    public ResponseEntity<List<AlertRecord>> getAlertsByCart(@PathVariable UUID cartId) {
        return ResponseEntity.ok(alertService.getAlertsByCart(cartId));
    }

    @GetMapping("/count")
    public ResponseEntity<Long> getUnacknowledgedCount() {
        return ResponseEntity.ok(alertService.getUnacknowledgedCount());
    }

    @PutMapping("/{alertId}/acknowledge")
    public ResponseEntity<AlertRecord> acknowledgeAlert(@PathVariable UUID alertId) {
        AlertRecord record = alertService.acknowledgeAlert(alertId);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(record);
    }
}
