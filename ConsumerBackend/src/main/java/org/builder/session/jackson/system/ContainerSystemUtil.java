package org.builder.session.jackson.system;

import java.time.Duration;
import java.util.Optional;

import org.builder.session.jackson.client.Client;
import org.builder.session.jackson.client.TaskMetadataClient;
import org.builder.session.jackson.client.messages.ContainerMetadata;
import org.builder.session.jackson.client.messages.ContainerStats;
import org.builder.session.jackson.client.messages.TaskMetadata;
import org.builder.session.jackson.exception.ConsumerDependencyException;
import org.builder.session.jackson.exception.ConsumerInternalException;
import org.builder.session.jackson.utils.NoArgs;

import lombok.extern.slf4j.Slf4j;

/**
 * Uses the Task Metadata endpoint to pollStats usage statistics.
 *
 * Link: https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-metadata-endpoint-v3.html
 */
@Slf4j
public class ContainerSystemUtil implements SystemUtil {

    private static final String CONTAINER_NAME = "ConsumerBackend";
    private static final String MEMORY_LIMIT_KEY = "Memory";
    private static final String CPU_LIMIT_KEY = "CPU";
    private static final Duration CACHE_TIME = Duration.ofMillis(200);
    private static final Duration WAIT_TIME = Duration.ofSeconds(20);

    private final Client<NoArgs, TaskMetadata> metadataClient = TaskMetadataClient.createTaskMetadataClient(CACHE_TIME);
    private final Client<NoArgs, ContainerStats> statsClient = TaskMetadataClient.createContainerStatsClient(CACHE_TIME);

    public ContainerSystemUtil() {
        try {
            //Perform some simple validation for our system to confirm that it is properly setup.
            //TODO: Improve how this sleep time is setup to only do it on initialization...
            Thread.sleep(WAIT_TIME.toMillis());
            ContainerStats initialStats = pollStats();
            ContainerMetadata initialMetadata = pollMetadata();
            Long reservedContainerCpu = getCpuUnitsTotal();
            Long reservedContainerMemory = getTotalMemory(MemoryUnit.BYTES);
            if (reservedContainerCpu == null || reservedContainerCpu <= 0) {
                throw new IllegalArgumentException("Container monitoring cannot be performed without a hard container reservation limit on CPU.");
            }
            if (reservedContainerMemory == null || reservedContainerMemory <= 0) {
                throw new IllegalArgumentException("Container monitoring cannot be performed without a hard container reservation limit on Memory.");
            }
        } catch (Throwable t) {
            throw new ConsumerInternalException("Failed while starting up ContainerSystemUtil.", t);
        }
    }

    /**
     * Polls the latest ContainerStats from the Metadata endpoint and logs the result.
     */
    protected ContainerStats pollStats () {
        ContainerStats stats = Optional.ofNullable(statsClient.call(NoArgs.INSTANCE))
                                       .orElseThrow(() -> new ConsumerDependencyException("Couldn't gather stats from endpoint."));
        log.debug("Pulled container stats: " + stats);
        return stats;
    }

    /**
     * Polls the latest ContainerStats from the Metadata endpoint and logs the result.
     */
    protected ContainerMetadata pollMetadata () {
        TaskMetadata stats = Optional.ofNullable(metadataClient.call(NoArgs.INSTANCE))
                                     .orElseThrow(() -> new ConsumerDependencyException("Couldn't gather metadata from endpoint."));
        log.debug("Pulled task metadata: " + stats);
        return stats.getContainers()
                    .stream()
                    .filter(c -> CONTAINER_NAME.equals(c.getName()))
                    .findFirst()
                    .orElseThrow(() -> new ConsumerDependencyException("Failed to find "
                                                                               + CONTAINER_NAME
                                                                               + " container from metadata endpoint.",
                                                                       new IllegalStateException(
                                                                               "Expected container metadata, but found none.")));
    }

    @Override
    public long getFreeMemory(MemoryUnit unit) {
        ContainerStats stats = this.pollStats();
        return MemoryUnit.convert(stats.getMemoryStats().getLimit() - stats.getMemoryStats().getUsage(),
                MemoryUnit.BYTES,
                unit);
    }

    public long getTotalMemory(MemoryUnit unit) {
        return MemoryUnit.convert(this.pollMetadata().getLimits().get(MEMORY_LIMIT_KEY),
                                  MemoryUnit.MEGABYTES,
                                  unit);
    }

    public long getUsedMemory(MemoryUnit unit) {
        return MemoryUnit.convert(this.pollStats().getMemoryStats().getUsage(),
                                  MemoryUnit.BYTES,
                                  unit);
    }

    public double getMemoryPercentage() {
        ContainerStats stats = this.pollStats();
        return (double)stats.getMemoryStats().getUsage() /
                (double)stats.getMemoryStats().getLimit();
    }

    public double getCpuPercentageOfSystemUsedByThisContainer() {
        ContainerStats stats = this.pollStats();
        if(stats.hasPreviousCpuStats()) {
            long systemUsage = stats.getCpuStats().getSystemCpuUsage()
                    - stats.getPreviousCpuStats().getSystemCpuUsage();
            long containerUsage = stats.getCpuStats().getCpuUsage().getTotalUsage()
                    - stats.getPreviousCpuStats().getCpuUsage().getTotalUsage();
            // This seems odd originally, but that's because CPU isn't a "measurable" resource. It's
            // just how many cycles out of the total amount the CPU run were taken by this container.
            return systemUsage == 0 ? 0.0 : (double)containerUsage / (double)systemUsage;
        } else {
            log.warn("Previous CPU stats were still null on TaskMetadata endpoint. Resolving to 0.");
            return 0.0;
        }
    }

    public double getCpuPercentageAllocatedToThisContainer() {
        return (double)this.pollMetadata().getLimits().get(CPU_LIMIT_KEY)
                / (double)(this.pollStats().getCpuStats().getOnlineCpus() * getUnitsPerProcessor());
    }

    public double getCpuPercentage() {
        // If the we used 30% of the system, but we were allocated 60%: we have used 50% of our space.
        return getCpuPercentageOfSystemUsedByThisContainer()
                / getCpuPercentageAllocatedToThisContainer();
    }

    public long getCpuUnitsTotal() {
        // We gather the adjusted CPU shares from ECS directly.
        return this.pollMetadata().getLimits().get(CPU_LIMIT_KEY);
    }

    public long getCpuUnitsUtilized () {
        // If we have our container is using 50% of it's reserved limit and a CPU using
        return (long)(this.getCpuPercentage() * getCpuUnitsTotal());
    }


}
