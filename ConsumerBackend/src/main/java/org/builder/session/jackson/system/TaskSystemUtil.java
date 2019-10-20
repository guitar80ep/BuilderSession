package org.builder.session.jackson.system;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.builder.session.jackson.client.Client;
import org.builder.session.jackson.client.TaskMetadataClient;
import org.builder.session.jackson.client.messages.TaskMetadata;
import org.builder.session.jackson.client.messages.TaskStats;
import org.builder.session.jackson.exception.ConsumerDependencyException;
import org.builder.session.jackson.utils.NoArgs;

import lombok.extern.slf4j.Slf4j;

/**
 * Uses the Task Metadata endpoint to pollStats usage statistics.
 *
 * Link: https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-metadata-endpoint-v3.html
 */
@Slf4j
public class TaskSystemUtil implements SystemUtil {

    private static final String MEMORY_LIMIT_KEY = "Memory";
    private static final String CPU_LIMIT_KEY = "CPU";
    private static final Duration CACHE_TIME = Duration.ofMillis(200);

    private final Client<NoArgs, TaskMetadata> metadataClient = TaskMetadataClient.createTaskMetadataClient(CACHE_TIME);
    private final Client<NoArgs, TaskStats> statsClient = TaskMetadataClient.createTaskStatsClient(CACHE_TIME);

    public TaskSystemUtil () {
        //Perform some simple validation for our system to confirm that it is properly setup.
        TaskStats initialStats = pollStats();
        TaskMetadata initialMetadata = pollMetadata();
        long reservedContainerCpu = getTaskLimit(CPU_LIMIT_KEY);
        long reservedContainerMemory = getTaskLimit(MEMORY_LIMIT_KEY);
    }

    public long getTaskLimit(String limitKey) {
        TaskMetadata taskMetadata = this.pollMetadata();
        if(taskMetadata.getLimits().containsKey(limitKey)) {
            return taskMetadata.getLimits().get(limitKey);
        } else {
            List<Long> limits = taskMetadata.getContainers()
                                            .stream()
                                            .map(c -> c.getLimits())
                                            .map(map -> map.get(limitKey))
                                            .collect(Collectors.toList());
            if(limits.stream().anyMatch(l -> l == null)) {
                String error = "Container monitoring cannot be performed without a hard container reservation limit on " + limitKey + ".";
                throw new IllegalArgumentException(error);
            }
            return limits.stream().mapToLong(l -> l).sum();
        }
    }

    /**
     * Polls the latest ContainerStats from the Metadata endpoint and logs the result.
     */
    protected TaskStats pollStats () {
        TaskStats stats = Optional.ofNullable(statsClient.call(NoArgs.INSTANCE))
                                  .orElseThrow(() -> new ConsumerDependencyException("Couldn't gather stats from endpoint."));
        log.debug("Pulled task stats: " + stats);
        return stats;
    }

    /**
     * Polls the latest ContainerStats from the Metadata endpoint and logs the result.
     */
    protected TaskMetadata pollMetadata () {
        TaskMetadata stats = Optional.ofNullable(metadataClient.call(NoArgs.INSTANCE))
                                     .orElseThrow(() -> new ConsumerDependencyException("Couldn't gather metadata from endpoint."));
        log.debug("Pulled task metadata: " + stats);
        return stats;
    }

    @Override
    public long getFreeMemory(MemoryUnit unit) {
        TaskStats stats = this.pollStats();
        return MemoryUnit.convert(getTotalMemory(MemoryUnit.BYTES) - getUsedMemory(MemoryUnit.BYTES),
                MemoryUnit.BYTES,
                unit);
    }

    public long getTotalMemory(MemoryUnit unit) {
        return MemoryUnit.convert(getTaskLimit(MEMORY_LIMIT_KEY), MemoryUnit.BYTES, unit);
    }

    public long getUsedMemory(MemoryUnit unit) {
        return MemoryUnit.convert(this.pollStats()
                                      .getContainers()
                                      .stream()
                                      .mapToLong(c -> c.getMemoryStats().getUsage())
                                      .sum(),
                                  MemoryUnit.BYTES,
                                  unit);
    }

    public double getMemoryPercentage() {
        return (double)getUsedMemory(MemoryUnit.BYTES) /
                (double)getTotalMemory(MemoryUnit.BYTES);
    }

    public double getCpuPercentageOfSystemUsedByThisContainer() {
        TaskStats stats = this.pollStats();
        if(stats.getContainers().stream().allMatch(t -> t.hasPreviousCpuStats())) {
            long systemUsage = stats.getContainers().stream().mapToLong( c -> c.getCpuStats().getSystemCpuUsage()).sum()
                    - stats.getContainers().stream().mapToLong( c -> c.getPreviousCpuStats().getSystemCpuUsage()).sum();
            long containerUsage = stats.getContainers().stream().mapToLong( c -> c.getCpuStats().getCpuUsage().getTotalUsage()).sum()
                    - stats.getContainers().stream().mapToLong( c -> c.getPreviousCpuStats().getCpuUsage().getTotalUsage()).sum();
            // This seems odd originally, but that's because CPU isn't a "measurable" resource. It's
            // just how many cycles out of the total amount the CPU run were taken by this container.
            return systemUsage == 0 ? 0.0 : (double)containerUsage / (double)systemUsage;
        } else {
            log.warn("Previous CPU stats were still null on TaskMetadata endpoint. Resolving to 0.");
            return 0.0;
        }
    }

    public double getCpuPercentageAllocatedToThisContainer() {
        long processors = this.pollStats()
                              .getContainers()
                              .stream()
                              .findFirst().get()
                              .getCpuStats()
                              .getOnlineCpus();
        return (double)this.pollMetadata().getLimits().get(CPU_LIMIT_KEY)
                / (double)(processors * getUnitsPerProcessor());
    }

    public double getCpuPercentage() {
        // If the we used 30% of the system, but we were allocated 60%: we have used 50% of our space.
        return getCpuPercentageOfSystemUsedByThisContainer()
                / getCpuPercentageAllocatedToThisContainer();
    }

    public long getCpuUnitsTotal() {
        return this.getTaskLimit(CPU_LIMIT_KEY);
    }

    public long getCpuUnitsUtilized () {
        // If we have our container is using 50% of it's reserved limit and a CPU using
        return (long)(this.getCpuPercentage() * getCpuUnitsTotal());
    }


}