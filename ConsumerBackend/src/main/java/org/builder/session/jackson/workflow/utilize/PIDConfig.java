package org.builder.session.jackson.workflow.utilize;

import java.time.Duration;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@ToString
@EqualsAndHashCode
@Builder
@Getter
public class PIDConfig {
    private Duration pace = Duration.ofSeconds(1);
    private double proportionFactor = 0.333;
    private double derivativeFactor = 0.333;
    private double integralFactor = 0.333;
    private double integralDecay = 0.95;
}
