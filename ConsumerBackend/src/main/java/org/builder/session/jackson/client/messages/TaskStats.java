package org.builder.session.jackson.client.messages;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskStats {
    private final List<ContainerStats> containers;
}
