package com.nestcart.alarm;

import com.nestcart.config.properties.AlertProperties;
import com.nestcart.entity.AlertRecord;
import com.nestcart.entity.NestCart;
import com.nestcart.entity.SensorData;
import com.nestcart.event.AlarmTriggeredEvent;
import com.nestcart.event.SensorDataReceivedEvent;
import com.nestcart.repository.AlertRecordRepository;
import com.nestcart.repository.NestCartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final NestCartRepository nestCartRepository;
    private final AlertRecordRepository alertRecordRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AlertProperties props;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AlertRecord checkAndAlert(SensorData sensorData) {
        NestCart cart = nestCartRepository.findById(sensorData.getCartId()).orElse(null);
        if (cart == null) return null;

        AlertRecord stressAlert = checkStressAlert(sensorData, cart);
        AlertRecord swayAlert = checkSwayAlert(sensorData, cart);

        AlertRecord resultAlert = stressAlert != null ? stressAlert : swayAlert;

        if (resultAlert != null) {
            pushAlert(resultAlert);
        }

        return resultAlert;
    }

    private AlertRecord checkStressAlert(SensorData sensorData, NestCart cart) {
        double stressLimit = cart.getStressLimit();
        double currentStress = sensorData.getBoomStress();
        double stressRatio = currentStress / stressLimit;

        String severity;
        String message;

        if (stressRatio >= props.getStressCriticalRatio()) {
            severity = "CRITICAL";
            message = String.format("悬臂应力临界: %.2f MPa (限值: %.2f MPa, 占比: %.1f%%)",
                    currentStress / 1e6, stressLimit / 1e6, stressRatio * 100);
        } else if (stressRatio >= props.getStressWarningRatio()) {
            severity = "WARNING";
            message = String.format("悬臂应力预警: %.2f MPa (限值: %.2f MPa, 占比: %.1f%%)",
                    currentStress / 1e6, stressLimit / 1e6, stressRatio * 100);
        } else {
            return null;
        }

        return createAlertRecord(sensorData.getCartId(), "STRESS_OVERLIMIT", severity,
                message, currentStress, stressLimit);
    }

    private AlertRecord checkSwayAlert(SensorData sensorData, NestCart cart) {
        double swayLimit = cart.getSwayLimit();
        double currentSway = sensorData.getBasketSway();
        double swayRatio = currentSway / swayLimit;

        String severity;
        String message;

        if (swayRatio >= props.getSwayCriticalRatio()) {
            severity = "CRITICAL";
            message = String.format("吊篮晃动临界: %.3f m (限值: %.3f m, 占比: %.1f%%)",
                    currentSway, swayLimit, swayRatio * 100);
        } else if (swayRatio >= props.getSwayWarningRatio()) {
            severity = "WARNING";
            message = String.format("吊篮晃动预警: %.3f m (限值: %.3f m, 占比: %.1f%%)",
                    currentSway, swayLimit, swayRatio * 100);
        } else {
            return null;
        }

        return createAlertRecord(sensorData.getCartId(), "SWAY_OVERLIMIT", severity,
                message, currentSway, swayLimit);
    }

    private AlertRecord createAlertRecord(UUID cartId, String alertType, String severity,
                                           String message, double metricValue, double threshold) {
        AlertRecord alert = AlertRecord.builder()
                .cartId(cartId)
                .alertType(alertType)
                .severity(severity)
                .message(message)
                .metricValue(metricValue)
                .threshold(threshold)
                .createdAt(OffsetDateTime.now())
                .acknowledged(false)
                .build();

        return alertRecordRepository.save(alert);
    }

    public void pushAlert(AlertRecord alert) {
        String topic = props.getAlertTopicPrefix();
        messagingTemplate.convertAndSend(topic, alert);
        messagingTemplate.convertAndSend(topic + "/" + alert.getCartId(), alert);

        log.info("告警已推送: type={}, severity={}, cartId={}", alert.getAlertType(),
                alert.getSeverity(), alert.getCartId());

        eventPublisher.publishEvent(new AlarmTriggeredEvent(this, alert));
    }

    @Async
    @EventListener
    public void onSensorDataReceived(SensorDataReceivedEvent event) {
        try {
            SensorData data = event.getSensorData();
            log.debug("收到传感器数据事件，触发告警评估: cartId={}", data.getCartId());
            checkAndAlert(data);
        } catch (Exception e) {
            log.warn("事件驱动告警评估失败: {}", e.getMessage());
        }
    }

    public List<AlertRecord> getRecentAlerts(int limit) {
        List<AlertRecord> all = alertRecordRepository.findTop10ByOrderByCreatedAtDesc();
        if (limit <= 0 || all.size() <= limit) return all;
        return all.subList(0, limit);
    }

    public AlertRecord acknowledgeAlert(UUID alertId) {
        AlertRecord alert = alertRecordRepository.findById(alertId).orElse(null);
        if (alert != null) {
            alert.setAcknowledged(true);
            return alertRecordRepository.save(alert);
        }
        return null;
    }

    public long getUnacknowledgedCount() {
        return alertRecordRepository.countByAcknowledgedFalse();
    }
}
