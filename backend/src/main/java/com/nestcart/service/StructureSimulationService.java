package com.nestcart.service;

import com.nestcart.dto.SimulationResult;
import com.nestcart.entity.NestCart;
import com.nestcart.entity.SensorData;
import com.nestcart.repository.NestCartRepository;
import com.nestcart.repository.SensorDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
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

    @Value("${nestcart.simulation.wind-load-coefficient:1.2}")
    private double windLoadCoefficient;

    @Value("${nestcart.simulation.air-density:1.225}")
    private double airDensity;

    @Value("${nestcart.simulation.gravity:9.81}")
    private double gravity;

    @Value("${nestcart.simulation.safety-factor:1.5}")
    private double safetyFactor;

    @Value("${nestcart.simulation.beam-element-count:20}")
    private int beamElementCount;

    @Value("${nestcart.alert.stress-warning-ratio:0.8}")
    private double stressWarningRatio;

    @Value("${nestcart.alert.stress-critical-ratio:0.95}")
    private double stressCriticalRatio;

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

        double gravityLoad = basketWeight * gravity;

        double windForcePerLength = calculateWindLoadPerUnitLength(effectiveWindSpeed, area, boomLength);
        double totalWindForce = windForcePerLength * boomLength;

        List<SimulationResult.BeamElementResult> beamElements = new ArrayList<>();
        double maxStress = 0.0;
        double maxDeflection = 0.0;
        double elementLength = boomLength / beamElementCount;

        for (int i = 0; i < beamElementCount; i++) {
            double x = (i + 0.5) * elementLength;
            double xi = x / boomLength;

            double gravityShear = gravityLoad;
            double gravityMoment = gravityLoad * x;
            double gravityAxial = 0.0;

            double windShear = windForcePerLength * (boomLength - x);
            double windMoment = windForcePerLength * (boomLength - x) * (boomLength - x) / 2.0;
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
            double windDeflection = (windForcePerLength * x * x) / (24.0 * elasticModulus * inertia)
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
        double windMaxStress = (windForcePerLength * boomLength * boomLength / 2.0) * (Math.sqrt(area / Math.PI)) / inertia;

        double gravityMaxDeflection = (gravityLoad * Math.pow(boomLength, 3)) / (3.0 * elasticModulus * inertia);
        double windMaxDeflection = (windForcePerLength * Math.pow(boomLength, 4)) / (8.0 * elasticModulus * inertia);

        double stressRatio = maxStress / stressLimit;
        String stressStatus = getStressStatus(stressRatio);

        double deflectionRatio = maxDeflection / swayLimit;
        String stabilityStatus = getStabilityStatus(deflectionRatio);

        double actualSafetyFactor = stressLimit / maxStress;

        return SimulationResult.builder()
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
                .build();
    }

    public SimulationResult simulateWithLatestData(UUID cartId) {
        List<SensorData> latestData = sensorDataRepository.findLatestByCartId(cartId, PageRequest.of(0, 1));
        if (latestData.isEmpty()) {
            return simulate(cartId, null, null, null);
        }
        SensorData data = latestData.get(0);
        return simulate(cartId, data.getHeight(), data.getWindSpeed(), data.getWindDirection());
    }

    private double calculateWindLoadPerUnitLength(double windSpeed, double crossSectionArea, double boomLength) {
        double windPressure = 0.5 * airDensity * windSpeed * windSpeed * windLoadCoefficient;
        double dragArea = Math.sqrt(crossSectionArea) * 2.0;
        return windPressure * dragArea;
    }

    private String getStressStatus(double stressRatio) {
        if (stressRatio >= stressCriticalRatio) {
            return "CRITICAL";
        } else if (stressRatio >= stressWarningRatio) {
            return "WARNING";
        }
        return "NORMAL";
    }

    private String getStabilityStatus(double deflectionRatio) {
        if (deflectionRatio >= stressCriticalRatio) {
            return "UNSTABLE";
        } else if (deflectionRatio >= stressWarningRatio) {
            return "MARGINAL";
        }
        return "STABLE";
    }

    public boolean isStressOverLimit(UUID cartId, double stress) {
        NestCart cart = nestCartRepository.findById(cartId).orElse(null);
        if (cart == null) return false;
        return stress >= cart.getStressLimit() * stressWarningRatio;
    }

    public boolean isSwayOverLimit(UUID cartId, double sway) {
        NestCart cart = nestCartRepository.findById(cartId).orElse(null);
        if (cart == null) return false;
        return sway >= cart.getSwayLimit() * stressWarningRatio;
    }
}
