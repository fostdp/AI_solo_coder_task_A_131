package com.nestcart.dtu;

import com.nestcart.dto.SensorDataRequest;
import com.nestcart.entity.SensorData;
import com.nestcart.repository.SensorDataRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dtu")
@RequiredArgsConstructor
public class DtuSensorController {

    private final DtuSensorService dtuSensorService;
    private final SensorDataRepository sensorDataRepository;

    @PostMapping("/{cartId}/sensor-data")
    public ResponseEntity<SensorData> submitSensorData(
            @PathVariable UUID cartId,
            @Valid @RequestBody SensorDataRequest request) {
        SensorData data = dtuSensorService.receiveAndValidate(cartId, request);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/{cartId}/sensor-data")
    public ResponseEntity<List<SensorData>> getSensorData(
            @PathVariable UUID cartId,
            @RequestParam(defaultValue = "60") int limit) {
        return ResponseEntity.ok(sensorDataRepository.findLatestByCartId(cartId, PageRequest.of(0, limit)));
    }
}
