package com.nestcart.event;

import com.nestcart.dto.SimulationResult;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class StructureSimulatedEvent extends ApplicationEvent {

    private final UUID cartId;
    private final SimulationResult simulationResult;

    public StructureSimulatedEvent(Object source, UUID cartId, SimulationResult simulationResult) {
        super(source);
        this.cartId = cartId;
        this.simulationResult = simulationResult;
    }
}
