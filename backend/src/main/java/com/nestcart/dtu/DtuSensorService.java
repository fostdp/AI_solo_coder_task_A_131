package com.nestcart.dtu;

import com.nestcart.dto.SensorDataRequest;
import com.nestcart.entity.NestCart;
import com.nestcart.entity.SensorData;
import com.nestcart.event.SensorDataReceivedEvent;
import com.nestcart.repository.NestCartRepository;
import com.nestcart.repository.SensorDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DtuSensorService {

    private final NestCartRepository nestCartRepository;
    private final SensorDataRepository sensorDataRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SensorData receiveAndValidate(UUID cartId, SensorDataRequest request) {
        NestCart cart = nestCartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("巢车不存在: " + cartId));

        validateSensorData(request, cart);

        SensorData data = SensorData.builder()
                .cartId(cartId)
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

        log.debug("DTU 传感器数据已接收: cartId={}, stress={}, sway={}, height={}",
                cartId, data.getBoomStress(), data.getBasketSway(), data.getHeight());

        eventPublisher.publishEvent(new SensorDataReceivedEvent(this, data));

        return data;
    }

    private void validateSensorData(SensorDataRequest request, NestCart cart) {
        if (request.getBoomStress() == null || request.getBoomStress() < 0) {
            throw new IllegalArgumentException("悬臂应力无效: 不能为负值");
        }
        if (request.getBasketSway() == null || request.getBasketSway() < 0) {
            throw new IllegalArgumentException("吊篮晃动值无效: 不能为负值");
        }
        if (request.getHeight() == null || request.getHeight() <= 0) {
            throw new IllegalArgumentException("高度无效: 必须大于0");
        }
        if (request.getHeight() > cart.getMaxHeight()) {
            throw new IllegalArgumentException("高度超过最大限值: " + cart.getMaxHeight());
        }
        if (request.getObservationDistance() == null || request.getObservationDistance() < 0) {
            throw new IllegalArgumentException("观察距离无效: 不能为负值");
        }
        if (request.getWindSpeed() != null && request.getWindSpeed() < 0) {
            throw new IllegalArgumentException("风速无效: 不能为负值");
        }
        if (request.getWindDirection() != null
                && (request.getWindDirection() < 0 || request.getWindDirection() > 360)) {
            throw new IllegalArgumentException("风向无效: 应在 0-360 度之间");
        }
    }
}
