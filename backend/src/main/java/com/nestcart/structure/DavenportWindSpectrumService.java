package com.nestcart.structure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class DavenportWindSpectrumService {

    private static final double K_V = 0.008;
    private static final double V_10_REF = 30.0;
    private static final double GUST_FACTOR_PEAK = 2.5;
    private static final double TURBULENCE_INTENSITY_GROUND = 0.14;
    private static final double ALEMBDA_TURBULENCE = 1200.0;

    public double calculateGustFactor(double windSpeed, double height) {
        double turbulenceIntensity = calculateTurbulenceIntensity(height);
        double integralLengthScale = calculateIntegralLengthScale(height);

        double reducedFrequency = calculateReducedFrequency(windSpeed, integralLengthScale, height);

        double backgroundFactor = calculateBackgroundFactor(reducedFrequency, turbulenceIntensity);
        double resonanceFactor = calculateResonanceFactor(reducedFrequency, turbulenceIntensity);

        double peakFactor = GUST_FACTOR_PEAK;

        double gustResponseFactor = 1.0 + peakFactor * turbulenceIntensity
                * Math.sqrt(backgroundFactor * backgroundFactor
                        + resonanceFactor * resonanceFactor);

        return Math.max(1.0, gustResponseFactor);
    }

    public double calculateTurbulenceIntensity(double height) {
        if (height <= 0) return TURBULENCE_INTENSITY_GROUND;
        double powerLawIndex = 0.16;
        return TURBULENCE_INTENSITY_GROUND * Math.pow(10.0 / height, powerLawIndex);
    }

    public double calculateIntegralLengthScale(double height) {
        return 0.4 * height / Math.log(height / 0.01);
    }

    public double calculateReducedFrequency(double windSpeed, double integralLengthScale, double height) {
        if (windSpeed <= 0) return 0.0;
        double naturalFrequency = estimateNaturalFrequency(height);
        return naturalFrequency * integralLengthScale / windSpeed;
    }

    public double estimateNaturalFrequency(double height) {
        if (height <= 0) return 1.0;
        double baseFreq = 1.0;
        return baseFreq * Math.pow(10.0 / height, 0.75);
    }

    public double calculateBackgroundFactor(double reducedFrequency, double turbulenceIntensity) {
        if (reducedFrequency <= 0) return 1.0;
        return 1.0 / Math.sqrt(1.0 + 70.0 * Math.pow(reducedFrequency, 0.6));
    }

    public double calculateResonanceFactor(double reducedFrequency, double turbulenceIntensity) {
        if (reducedFrequency <= 0) return 0.0;

        double dampingRatio = 0.02;
        double spectralValue = davenportSpectralValue(reducedFrequency, turbulenceIntensity);

        if (spectralValue <= 0 || dampingRatio <= 0) return 0.0;

        return Math.sqrt(Math.PI / (4.0 * dampingRatio) * spectralValue);
    }

    public double davenportSpectralValue(double reducedFrequency, double turbulenceIntensity) {
        if (reducedFrequency <= 0) return 0.0;

        double x = 1200.0 * reducedFrequency / V_10_REF;
        if (x <= 0) return 0.0;

        double numerator = 4.0 * K_V * V_10_REF * V_10_REF * x * x;
        double denominator = reducedFrequency * Math.pow(1.0 + x * x, 4.0 / 3.0);

        if (denominator <= 0) return 0.0;

        return numerator / denominator;
    }

    public double calculateDynamicWindLoad(double staticWindLoad, double windSpeed, double height) {
        if (windSpeed <= 0 || height <= 0) return staticWindLoad;

        double gustFactor = calculateGustFactor(windSpeed, height);

        double dynamicAmplification = 1.0 + 0.5 * (gustFactor - 1.0)
                * (1.0 + Math.cos(2 * Math.PI * windSpeed * 0.1));

        return staticWindLoad * gustFactor * dynamicAmplification;
    }

    public List<Double> generateGustTimeSeries(double meanWindSpeed, double height,
                                                int numPoints, double timeStep) {
        List<Double> timeSeries = new ArrayList<>();
        double turbulenceIntensity = calculateTurbulenceIntensity(height);
        double stdDev = meanWindSpeed * turbulenceIntensity;

        int numFrequencies = numPoints / 2;
        double[] amplitudes = new double[numFrequencies];
        double[] phases = new double[numFrequencies];

        for (int k = 0; k < numFrequencies; k++) {
            double frequency = (k + 1) / (numPoints * timeStep);
            double spectralValue = davenportSpectralValue(frequency, turbulenceIntensity);
            amplitudes[k] = Math.sqrt(spectralValue / (numPoints * timeStep));
            phases[k] = 2 * Math.PI * Math.random();
        }

        for (int i = 0; i < numPoints; i++) {
            double t = i * timeStep;
            double fluctuation = 0.0;
            for (int k = 0; k < numFrequencies; k++) {
                double frequency = (k + 1) / (numPoints * timeStep);
                fluctuation += amplitudes[k] * Math.cos(2 * Math.PI * frequency * t + phases[k]);
            }
            double windSpeed = meanWindSpeed + fluctuation * stdDev;
            timeSeries.add(Math.max(0, windSpeed));
        }

        return timeSeries;
    }

    public double calculateGustLoadFactor(double windSpeed, double height) {
        if (windSpeed <= 0) return 1.0;

        double turbulenceIntensity = calculateTurbulenceIntensity(height);

        double gustFactor = 1.0 + GUST_FACTOR_PEAK * turbulenceIntensity
                * Math.sqrt(1.0 + 1.0 / (1.0 + 0.5 * height / ALEMBDA_TURBULENCE));

        return Math.max(1.0, Math.min(gustFactor, 3.0));
    }

    public double calculateWindPressure(double windSpeed, double height) {
        double airDensity = 1.225;
        double dragCoefficient = 1.2;
        double gustFactor = calculateGustLoadFactor(windSpeed, height);
        return 0.5 * airDensity * windSpeed * windSpeed * dragCoefficient * gustFactor;
    }
}
