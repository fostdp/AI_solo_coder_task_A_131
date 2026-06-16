package com.nestcart.event;

import com.nestcart.dto.VisionResult;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class VisionAnalyzedEvent extends ApplicationEvent {

    private final UUID cartId;
    private final VisionResult visionResult;

    public VisionAnalyzedEvent(Object source, UUID cartId, VisionResult visionResult) {
        super(source);
        this.cartId = cartId;
        this.visionResult = visionResult;
    }
}
