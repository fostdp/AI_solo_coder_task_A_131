package com.nestcart.structure;

import com.nestcart.config.properties.StructureSimulationProperties;
import com.nestcart.dto.SimulationResult;
import com.nestcart.entity.NestCart;
import com.nestcart.entity.SensorData;
import com.nestcart.event.SensorDataReceivedEvent;
import com.nestcart.event.StructureSimulatedEvent;
import com.nestcart.repository.NestCartRepository;
import com.nestcart.repository.SensorDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StructureSimulationService {

    private final NestCartRepository nestCartRepository;
    private final SensorDataRepository sensorDataRepository;
    private final DavenportWindSpectrumService davenportService;
    private final StructureSimulationProperties props;
    private final ApplicationEventPublisher eventPublisher;

    public SimulationResult simulate(UUID cartId, Double height, Double windSpeed, Double windDirection) {
        NestCart cart = nestCartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("巢车不存在: " + cartId));

        double effectiveHeight = height != null ? height : cart.getMaxHeight();
        double effectiveWindSpeed = windSpeed != null ? windSpeed : 0.0;
        double effectiveWindDirection = windDirection != null ? windDirection : 0.0;

        double boomLength = cart.getBoomLength();
        double area = cart.getBoomCrossSectionArea();
        double inertia = cart.getBoomMomentOfInertia();
        double elasticModulus = cart.getBoomElasticModulus();
        double basketWeight = cart.getBasketWeight();
        double stressLimit = cart.getStressLimit();
        double swayLimit = cart.getSwayLimit();

        double gravityLoad = basketWeight * props.getGravity();

        double turbulenceIntensity = davenportService.calculateTurbulenceIntensity(effectiveHeight);
        double gustFactor = davenportService.calculateGustLoadFactor(effectiveWindSpeed, effectiveHeight);
        double gustResponseFactor = davenportService.calculateGustFactor(effectiveWindSpeed, effectiveHeight);
        double peakGustWindSpeed = effectiveWindSpeed * gustFactor;

        double staticWindForcePerLength = calculateWindLoadPerUnitLength(effectiveWindSpeed, area, boomLength);
        double dynamicWindForcePerLength = calculateDynamicWindLoad(
                effectiveWindSpeed, area, boomLength, effectiveHeight, gustResponseFactor);

        double totalWindForcePerLength = staticWindForcePerLength + dynamicWindForcePerLength;
        double totalWindForce = totalWindForcePerLength * boomLength;

        List<SimulationResult.BeamElementResult> beamElements = new ArrayList<>();
        double maxStress = 0.0;
        double maxDeflection = 0.0;
        double elementLength = boomLength / props.getBeamElementCount();

        for (int i = 0; i < props.getBeamElementCount(); i++) {
            double x = (i + 0.5) * elementLength;
            double xi = x / boomLength;

            double gravityShear = gravityLoad;
            double gravityMoment = gravityLoad * x;
            double gravityAxial = 0.0;

            double windShear = totalWindForcePerLength * (boomLength - x);
            double windMoment = totalWindForcePerLength * (boomLength - x) * (boomLength - x) / 2.0;
            double windAxial = totalWindForce * Math.sin(Math.toRadians(effectiveWindDirection)) * xi;

            double totalShear = Math.abs(gravityShear) + Math.abs(windShear);
            double totalMoment = gravityMoment + windMoment;
            double totalAxial = gravityAxial + windAxial;

            double bendingStress = totalMoment * (Math.sqrt(area / Math.PI)) / inertia;
            double axialStress = totalAxial / area;
            double shearStress = totalShear / area;
            double vonMisesStress = Math.sqrt(
                    bendingStress * bendingStress + axialStress * axialStress
                            - bendingStress * axialStress + 3.0 * shearStress * shearStress
            );

            double gravityDeflection = (gravityLoad * x * x) / (6.0 * elasticModulus * inertia)
                    * (3.0 * boomLength - x);
            double windDeflection = (totalWindForcePerLength * x * x) / (24.0 * elasticModulus * inertia)
                    * (boomLength * boomLength * 6.0 - 4.0 * boomLength * x + x * x);
            double totalDeflection = gravityDeflection + windDeflection;

            maxStress = Math.max(maxStress, vonMisesStress);
            maxDeflection = Math.max(maxDeflection, totalDeflection);

            beamElements.add(SimulationResult.BeamElementResult.builder()
                    .elementIndex(i)
                    .position(x)
                    .axialForce(totalAxial)
                    .shearForce(totalShear)
                    .bendingMoment(totalMoment)
                    .stress(vonMisesStress)
                    .deflection(totalDeflection)
                    .build());
        }

        double gravityMaxStress = (gravityLoad * boomLength) * (Math.sqrt(area / Math.PI)) / inertia;
        double windMaxStress = (totalWindForcePerLength * boomLength * boomLength / 2.0) * (Math.sqrt(area / Math.PI)) / inertia;
        double gustStress = windMaxStress - (staticWindForcePerLength * boomLength * boomLength / 2.0) * (Math.sqrt(area / Math.PI)) / inertia;

        double gravityMaxDeflection = (gravityLoad * Math.pow(boomLength, 3)) / (3.0 * elasticModulus * inertia);
        double windMaxDeflection = (totalWindForcePerLength * Math.pow(boomLength, 4)) / (8.0 * elasticModulus * inertia);

        double stressRatio = maxStress / stressLimit;
        String stressStatus = getStressStatus(stressRatio);

        double deflectionRatio = maxDeflection / swayLimit;
        String stabilityStatus = getStabilityStatus(deflectionRatio);

        double actualSafetyFactor = stressLimit / maxStress;

        SimulationResult result = SimulationResult.builder()
                .cartId(cartId)
                .height(effectiveHeight)
                .windSpeed(effectiveWindSpeed)
                .windDirection(effectiveWindDirection)
                .gravityStress(gravityMaxStress)
                .windStress(windMaxStress)
                .totalStress(maxStress)
                .stressRatio(stressRatio)
                .stressStatus(stressStatus)
                .gravityDeflection(gravityMaxDeflection)
                .windDeflection(windMaxDeflection)
                .totalDeflection(maxDeflection)
                .deflectionRatio(deflectionRatio)
                .stabilityStatus(stabilityStatus)
                .beamElements(beamElements)
                .safetyFactor(actualSafetyFactor)
                .gustFactor(gustFactor)
                .gustStress(Math.max(0, gustStress))
                .turbulenceIntensity(turbulenceIntensity)
                .gustResponseFactor(gustResponseFactor)
                .peakGustWindSpeed(peakGustWindSpeed)
                .build();

        eventPublisher.publishEvent(new StructureSimulatedEvent(this, cartId, result));

        return result;
    }

    public SimulationResult simulateWithLatestData(UUID cartId) {
        List<SensorData> latestData = sensorDataRepository.findLatestByCartId(cartId, PageRequest.of(0, 1));
        if (latestData.isEmpty()) {
            return simulate(cartId, null, null, null);
        }
        SensorData data = latestData.get(0);
        return simulate(cartId, data.getHeight(), data.getWindSpeed(), data.getWindDirection());
    }

    @Async
    @EventListener
    public void onSensorDataReceived(SensorDataReceivedEvent event) {
        try {
            SensorData data = event.getSensorData();
            log.debug("收到传感器数据事件，触发结构仿真: cartId={}", data.getCartId());
            simulate(data.getCartId(), data.getHeight(), data.getWindSpeed(), data.getWindDirection());
        } catch (Exception e) {
            log.warn("事件驱动结构仿真失败: {}", e.getMessage());
        }
    }

    private double calculateWindLoadPerUnitLength(double windSpeed, double crossSectionArea, double boomLength) {
        if (windSpeed <= 0) return 0.0;
        double windPressure = 0.5 * props.getAirDensity() * windSpeed * windSpeed * props.getWindLoadCoefficient();
        double dragArea = Math.sqrt(crossSectionArea) * 2.0;
        return windPressure * dragArea;
    }

    private double calculateDynamicWindLoad(double windSpeed, double crossSectionArea,
                                            double boomLength, double height, double gustResponseFactor) {
        if (windSpeed <= 0) return 0.0;
        double staticLoad = calculateWindLoadPerUnitLength(windSpeed, crossSectionArea, boomLength);
        return staticLoad * (gustResponseFactor - 1.0);
    }

    private String getStressStatus(double stressRatio) {
        double critical = props.getDefaultBoom().getStressLimit() > 0 ? 0.95 : 0.95;
        double warning = 0.8;
        if (stressRatio >= critical) {
            return "CRITICAL";
        } else if (stressRatio >= warning) {
            return "WARNING";
        }
        return "NORMAL";
    }

    private String getStabilityStatus(double deflectionRatio) {
        if (deflectionRatio >= 0.95) {
            return "UNSTABLE";
        } else if (deflectionRatio >= 0.8) {
            return "MARGINAL";
        }
        return "STABLE";
    }

    public boolean isStressOverLimit(UUID cartId, double stress) {
        NestCart cart = nestCartRepository.findById(cartId).orElse(null);
        if (cart == null) return false;
        return stress >= cart.getStressLimit() * 0.8;
    }

    public boolean isSwayOverLimit(UUID cartId, double sway) {
        NestCart cart = nestCartRepository.findById(cartId).orElse(null);
        if (cart == null) return false;
        return sway >= cart.getSwayLimit() * 0.7;
    }
}
