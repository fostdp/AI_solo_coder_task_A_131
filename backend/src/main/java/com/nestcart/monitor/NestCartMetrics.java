package com.nestcart.monitor;

import com.nestcart.event.AlarmTriggeredEvent;
import com.nestcart.event.SensorDataReceivedEvent;
import com.nestcart.event.StructureSimulatedEvent;
import com.nestcart.event.VisionAnalyzedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class NestCartMetrics {

    private final Counter sensorDataReceivedTotal;
    private final Counter alertTriggeredTotal;
    private final Counter structureSimulatedTotal;
    private final Counter visionAnalyzedTotal;
    private final Counter alertWarningCount;
    private final Counter alertCriticalCount;

    private final ConcurrentHashMap<String, Timer.Sample> simulationTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer.Sample> visionTimers = new ConcurrentHashMap<>();

    private final Timer structureSimulationTimer;
    private final Timer visionAnalysisTimer;

    public NestCartMetrics(MeterRegistry registry) {
        this.sensorDataReceivedTotal = Counter.builder("nestcart.sensor.data.received.total")
                .description("Total number of sensor data points received")
                .register(registry);

        this.alertTriggeredTotal = Counter.builder("nestcart.alert.triggered.total")
                .description("Total number of alerts triggered")
                .register(registry);

        this.alertWarningCount = Counter.builder("nestcart.alert.warning.total")
                .description("Total number of WARNING severity alerts")
                .register(registry);

        this.alertCriticalCount = Counter.builder("nestcart.alert.critical.total")
                .description("Total number of CRITICAL severity alerts")
                .register(registry);

        this.structureSimulatedTotal = Counter.builder("nestcart.structure.simulated.total")
                .description("Total number of structure simulations executed")
                .register(registry);

        this.visionAnalyzedTotal = Counter.builder("nestcart.vision.analyzed.total")
                .description("Total number of vision analyses executed")
                .register(registry);

        this.structureSimulationTimer = Timer.builder("nestcart.structure.simulation.duration")
                .description("Duration of structure simulation")
                .register(registry);

        this.visionAnalysisTimer = Timer.builder("nestcart.vision.analysis.duration")
                .description("Duration of vision analysis")
                .register(registry);

        registry.gauge("nestcart.info", 1, value -> value);

        log.info("NestCart 自定义指标已注册到 Prometheus");
    }

    @EventListener
    public void onSensorDataReceived(SensorDataReceivedEvent event) {
        sensorDataReceivedTotal.increment();
        String cartId = event.getSensorData().getCartId().toString();
        simulationTimers.put(cartId, Timer.start());
        visionTimers.put(cartId, Timer.start());
    }

    @EventListener
    public void onStructureSimulated(StructureSimulatedEvent event) {
        structureSimulatedTotal.increment();
        String cartId = event.getCartId().toString();
        Timer.Sample sample = simulationTimers.remove(cartId);
        if (sample != null) {
            sample.stop(structureSimulationTimer);
        }
    }

    @EventListener
    public void onVisionAnalyzed(VisionAnalyzedEvent event) {
        visionAnalyzedTotal.increment();
        String cartId = event.getCartId().toString();
        Timer.Sample sample = visionTimers.remove(cartId);
        if (sample != null) {
            sample.stop(visionAnalysisTimer);
        }
    }

    @EventListener
    public void onAlarmTriggered(AlarmTriggeredEvent event) {
        alertTriggeredTotal.increment();
        String severity = event.getAlertRecord().getSeverity();
        if ("CRITICAL".equalsIgnoreCase(severity)) {
            alertCriticalCount.increment();
        } else if ("WARNING".equalsIgnoreCase(severity)) {
            alertWarningCount.increment();
        }
    }
}
