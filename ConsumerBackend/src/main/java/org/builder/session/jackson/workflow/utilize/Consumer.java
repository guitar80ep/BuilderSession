package org.builder.session.jackson.workflow.utilize;

import java.util.Map;
import java.util.Set;

import org.build.session.jackson.proto.Resource;
import org.build.session.jackson.proto.Unit;
import org.builder.session.jackson.system.SystemUtil;

import com.google.common.collect.ImmutableMap;

import lombok.NonNull;

public interface Consumer extends AutoCloseable {
    public String getName();
    public void setTarget (double value, Unit unit);
    public double getTarget(Unit unit);
    public double getActual(Unit unit);
    public Unit getDefaultUnit();

    public default double getTarget() {
        return getTarget(getDefaultUnit());
    }

    public default double getActual() {
        return getActual(getDefaultUnit());
    }

    public void consume();
    public void close();

    /**
     * Builds a set of default consumers to use based on a set of specified resources.
     */
    public static Map<Resource, Consumer> buildDefaultConsumers(@NonNull Set<Resource> resources,
                                                                @NonNull SystemUtil systemUtil,
                                                                @NonNull PIDConfig pidConfig) {
        ImmutableMap.Builder<Resource, Consumer> builder = ImmutableMap.builder();
        for(Resource resource : resources) {
            switch (resource) {
                case CPU:
                    builder.put(Resource.CPU, new CpuConsumer(systemUtil, pidConfig));
                    break;
                case MEMORY:
                    builder.put(Resource.MEMORY, new MemoryConsumer(systemUtil, pidConfig));
                    break;
                case DISK:
                    builder.put(Resource.DISK, new DiskConsumer(systemUtil, pidConfig));
                    break;
                case NETWORK:
                    builder.put(Resource.NETWORK, new NetworkConsumer(systemUtil, pidConfig));
                    break;
                    default:
                        throw new IllegalArgumentException("Unrecognized resource type " + resource);
            }
        }
        return builder.build();
    }
}
