package com.nestcart.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "nestcart.alert")
public class AlertProperties {

    private double stressWarningRatio = 0.8;

    private double stressCriticalRatio = 0.95;

    private double swayWarningRatio = 0.7;

    private double swayCriticalRatio = 0.9;

    private String alertTopicPrefix = "/topic/alerts";
}
