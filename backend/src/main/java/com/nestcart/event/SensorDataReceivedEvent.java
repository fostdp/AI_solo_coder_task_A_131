package com.nestcart.event;

import com.nestcart.entity.SensorData;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class SensorDataReceivedEvent extends ApplicationEvent {

    private final SensorData sensorData;

    public SensorDataReceivedEvent(Object source, SensorData sensorData) {
        super(source);
        this.sensorData = sensorData;
    }
}
