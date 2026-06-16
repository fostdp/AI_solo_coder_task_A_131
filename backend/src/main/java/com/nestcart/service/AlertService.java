package com.nestcart.service;

import com.nestcart.dto.AlertMessage;
import com.nestcart.entity.AlertRecord;
import com.nestcart.entity.NestCart;
import com.nestcart.entity.SensorData;
import com.nestcart.repository.AlertRecordRepository;
import com.nestcart.repository.NestCartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final AlertRecordRepository alertRecordRepository;
    private final NestCartRepository nestCartRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String ALERT_TYPE_STRESS = "STRESS_OVERLIMIT";
    private static final String ALERT_TYPE_SWAY = "SWAY_OVERLIMIT";
    private static final String SEVERITY_WARNING = "WARNING";
    private static final String SEVERITY_CRITICAL = "CRITICAL";

    public void checkAndAlert(SensorData sensorData) {
        NestCart cart = nestCartRepository.findById(sensorData.getCartId()).orElse(null);
        if (cart == null) return;

        checkStressAlert(sensorData, cart);
        checkSwayAlert(sensorData, cart);
    }

    private void checkStressAlert(SensorData sensorData, NestCart cart) {
        double stressRatio = sensorData.getBoomStress() / cart.getStressLimit();

        if (stressRatio >= 0.95) {
            createAndPushAlert(sensorData.getCartId(), ALERT_TYPE_STRESS, SEVERITY_CRITICAL,
                    "悬臂应力严重超限！当前应力比: " + String.format("%.2f", stressRatio * 100) + "%",
                    sensorData.getBoomStress(), cart.getStressLimit());
        } else if (stressRatio >= 0.80) {
            createAndPushAlert(sensorData.getCartId(), ALERT_TYPE_STRESS, SEVERITY_WARNING,
                    "悬臂应力接近限值！当前应力比: " + String.format("%.2f", stressRatio * 100) + "%",
                    sensorData.getBoomStress(), cart.getStressLimit());
        }
    }

    private void checkSwayAlert(SensorData sensorData, NestCart cart) {
        double swayRatio = sensorData.getBasketSway() / cart.getSwayLimit();

        if (swayRatio >= 0.90) {
            createAndPushAlert(sensorData.getCartId(), ALERT_TYPE_SWAY, SEVERITY_CRITICAL,
                    "吊篮晃动严重超限！当前晃动比: " + String.format("%.2f", swayRatio * 100) + "%",
                    sensorData.getBasketSway(), cart.getSwayLimit());
        } else if (swayRatio >= 0.70) {
            createAndPushAlert(sensorData.getCartId(), ALERT_TYPE_SWAY, SEVERITY_WARNING,
                    "吊篮晃动接近限值！当前晃动比: " + String.format("%.2f", swayRatio * 100) + "%",
                    sensorData.getBasketSway(), cart.getSwayLimit());
        }
    }

    private void createAndPushAlert(UUID cartId, String alertType, String severity,
                                     String message, double metricValue, double threshold) {
        AlertRecord record = AlertRecord.builder()
                .cartId(cartId)
                .alertType(alertType)
                .severity(severity)
                .message(message)
                .metricValue(metricValue)
                .threshold(threshold)
                .acknowledged(false)
                .createdAt(OffsetDateTime.now())
                .build();

        record = alertRecordRepository.save(record);

        AlertMessage alertMessage = AlertMessage.builder()
                .alertId(record.getId())
                .cartId(cartId)
                .alertType(alertType)
                .severity(severity)
                .message(message)
                .metricValue(metricValue)
                .threshold(threshold)
                .timestamp(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .build();

        messagingTemplate.convertAndSend("/topic/alerts", alertMessage);
        messagingTemplate.convertAndSend("/topic/alerts/" + cartId, alertMessage);

        log.warn("告警推送: [{}] {} - {}", severity, alertType, message);
    }

    public List<AlertRecord> getUnacknowledgedAlerts() {
        return alertRecordRepository.findByAcknowledgedFalseOrderByCreatedAtDesc();
    }

    public List<AlertRecord> getAlertsByCart(UUID cartId) {
        return alertRecordRepository.findByCartIdOrderByCreatedAtDesc(cartId);
    }

    public AlertRecord acknowledgeAlert(UUID alertId) {
        AlertRecord record = alertRecordRepository.findById(alertId).orElse(null);
        if (record != null) {
            record.setAcknowledged(true);
            return alertRecordRepository.save(record);
        }
        return null;
    }

    public long getUnacknowledgedCount() {
        return alertRecordRepository.countByAcknowledgedFalse();
    }
}
