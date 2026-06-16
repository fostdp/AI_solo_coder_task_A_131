package com.nestcart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationResult {

    private UUID cartId;
    private Double height;
    private Double windSpeed;
    private Double windDirection;

    private Double gravityStress;
    private Double windStress;
    private Double totalStress;
    private Double stressRatio;
    private String stressStatus;

    private Double gravityDeflection;
    private Double windDeflection;
    private Double totalDeflection;
    private Double deflectionRatio;
    private String stabilityStatus;

    private List<BeamElementResult> beamElements;

    private Double safetyFactor;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BeamElementResult {
        private int elementIndex;
        private double position;
        private double axialForce;
        private double shearForce;
        private double bendingMoment;
        private double stress;
        private double deflection;
    }
}
