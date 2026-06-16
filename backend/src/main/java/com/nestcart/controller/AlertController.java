package com.nestcart.controller;

import com.nestcart.alarm.AlertService;
import com.nestcart.entity.AlertRecord;
import com.nestcart.repository.AlertRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final AlertRecordRepository alertRecordRepository;

    @GetMapping
    public ResponseEntity<List<AlertRecord>> getUnacknowledgedAlerts() {
        return ResponseEntity.ok(alertRecordRepository
                .findByAcknowledgedFalseOrderByCreatedAtDesc());
    }

    @GetMapping("/cart/{cartId}")
    public ResponseEntity<List<AlertRecord>> getAlertsByCart(@PathVariable UUID cartId) {
        return ResponseEntity.ok(alertRecordRepository
                .findByCartIdOrderByCreatedAtDesc(cartId));
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getAlertCount() {
        return ResponseEntity.ok(Map.of("unacknowledged", alertService.getUnacknowledgedCount()));
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
