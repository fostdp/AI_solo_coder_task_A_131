package com.nestcart.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "nestcart.vision")
public class VisionAnalysisProperties {

    private double earthRadius = 6371000.0;

    private double defaultGridResolution = 10.0;

    private double maxAnalysisRadius = 5000.0;

    private boolean useQuadtree = true;

    private int quadtreeMaxDepth = 6;

    private int quadtreeMinCells = 4;

    private int sectorCount = 36;

    private String defaultRegion = "default_battlefield";
}
