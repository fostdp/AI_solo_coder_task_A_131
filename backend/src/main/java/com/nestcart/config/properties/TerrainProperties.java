package com.nestcart.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "nestcart.terrain")
public class TerrainProperties {

    private Map<String, RegionConfig> regions = new HashMap<>();

    @Data
    public static class RegionConfig {
        private String name;
        private int gridSizeX = 100;
        private int gridSizeY = 100;
        private double resolution = 10.0;
        private double baseElevation = 0.0;
        private boolean generatePerlin = false;
        private long seed = 42L;
    }
}
