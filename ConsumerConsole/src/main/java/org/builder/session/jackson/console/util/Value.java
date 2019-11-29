package org.builder.session.jackson.console.util;

import org.build.session.jackson.proto.Unit;

import lombok.Data;

@Data
public class Value {
    private final double value;
    private final Unit unit;
}
