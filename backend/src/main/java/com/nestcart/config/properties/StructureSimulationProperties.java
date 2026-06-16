package com.nestcart.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "nestcart.simulation")
public class StructureSimulationProperties {

    private double windLoadCoefficient = 1.2;

    private double airDensity = 1.225;

    private double gravity = 9.81;

    private double safetyFactor = 1.5;

    private int beamElementCount = 20;

    private BoomProperties defaultBoom = new BoomProperties();

    private BasketProperties defaultBasket = new BasketProperties();

    @Data
    public static class BoomProperties {
        private double length = 8.0;
        private double crossSectionArea = 0.003;
        private double momentOfInertia = 1.5e-6;
        private double elasticModulus = 2.1e11;
        private double stressLimit = 8.0e6;
    }

    @Data
    public static class BasketProperties {
        private double weight = 200.0;
        private double swayLimit = 0.5;
    }
}
