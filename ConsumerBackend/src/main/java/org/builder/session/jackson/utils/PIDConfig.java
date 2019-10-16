package org.builder.session.jackson.utils;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@ToString
@EqualsAndHashCode
@Builder
@Getter
public class PIDConfig {
    private double proportionFactor = 0.333;
    private double derivativeFactor = 0.333;
    private double integralFactor = 0.333;
}
