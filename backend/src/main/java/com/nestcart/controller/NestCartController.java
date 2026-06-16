package com.nestcart.controller;

import com.nestcart.dto.SensorDataRequest;
import com.nestcart.entity.NestCart;
import com.nestcart.entity.SensorData;
import com.nestcart.repository.NestCartRepository;
import com.nestcart.repository.SensorDataRepository;
import com.nestcart.service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/carts")
@RequiredArgsConstructor
public class NestCartController {

    private final NestCartRepository nestCartRepository;
    private final SensorDataRepository sensorDataRepository;
    private final AlertService alertService;

    @GetMapping
    public ResponseEntity<List<NestCart>> getAllCarts() {
        return ResponseEntity.ok(nestCartRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<NestCart> getCart(@PathVariable UUID id) {
        return nestCartRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<NestCart> createCart(@RequestBody NestCart cart) {
        cart.setCreatedAt(OffsetDateTime.now());
        return ResponseEntity.ok(nestCartRepository.save(cart));
    }

    @PostMapping("/{id}/sensor-data")
    public ResponseEntity<SensorData> submitSensorData(
            @PathVariable UUID id,
            @Valid @RequestBody SensorDataRequest request) {
        if (!nestCartRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        SensorData data = SensorData.builder()
                .cartId(id)
                .timestamp(OffsetDateTime.now())
                .boomStress(request.getBoomStress())
                .basketSway(request.getBasketSway())
                .height(request.getHeight())
                .observationDistance(request.getObservationDistance())
                .windSpeed(request.getWindSpeed() != null ? request.getWindSpeed() : 0.0)
                .windDirection(request.getWindDirection() != null ? request.getWindDirection() : 0.0)
                .temperature(request.getTemperature() != null ? request.getTemperature() : 20.0)
                .build();

        data = sensorDataRepository.save(data);
        alertService.checkAndAlert(data);

        return ResponseEntity.ok(data);
    }

    @GetMapping("/{id}/sensor-data")
    public ResponseEntity<List<SensorData>> getSensorData(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "60") int limit) {
        return ResponseEntity.ok(sensorDataRepository.findLatestByCartId(id, PageRequest.of(0, limit)));
    }
}
