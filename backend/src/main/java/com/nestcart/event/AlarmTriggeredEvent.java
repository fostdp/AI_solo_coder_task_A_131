package com.nestcart.event;

import com.nestcart.entity.AlertRecord;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AlarmTriggeredEvent extends ApplicationEvent {

    private final AlertRecord alertRecord;

    public AlarmTriggeredEvent(Object source, AlertRecord alertRecord) {
        super(source);
        this.alertRecord = alertRecord;
    }
}
