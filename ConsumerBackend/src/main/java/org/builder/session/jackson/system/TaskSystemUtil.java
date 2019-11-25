package org.builder.session.jackson.system;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.builder.session.jackson.client.SimpleClient;
import org.builder.session.jackson.client.ecs.TaskMetadataClient;
import org.builder.session.jackson.client.messages.TaskMetadata;
import org.builder.session.jackson.client.messages.TaskStats;
import org.builder.session.jackson.exception.ConsumerDependencyException;
import org.builder.session.jackson.exception.ConsumerInternalException;
import org.builder.session.jackson.utils.RateTracker;

import com.google.common.base.Preconditions;

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
    private static final Duration CACHE_TIME = Duration.ofMillis(100);
    private static final Duration WAIT_TIME = Duration.ofSeconds(30);
    private static final Duration RATE_POLLING_PERIOD = Duration.ofSeconds(20);
    public static final String OPERATION_FOR_STORAGE = "Write";

    private final SimpleClient<TaskMetadata> metadataClient;
    private final SimpleClient<TaskStats> statsClient;
    private final RateTracker networkRateTracker;
    private final RateTracker storageRateTracker;

    public TaskSystemUtil () {
        try {
            metadataClient = TaskMetadataClient.createTaskMetadataClient(CACHE_TIME);
            statsClient = TaskMetadataClient.createTaskStatsClient(CACHE_TIME);

            //Perform some simple validation for our system to confirm that it is properly setup.
            //TODO: Improve how this sleep time is setup to only do it on initialization...
            TaskStats initialStats = pollStats();
            TaskMetadata initialMetadata = pollMetadata();
            Thread.sleep(WAIT_TIME.toMillis());
            long reservedContainerCpu = getTotalCpu(DigitalUnit.VCPU);
            long reservedContainerMemory = getTotalMemory(DigitalUnit.BYTES);

            //Setup rate trackers.
            networkRateTracker = new RateTracker(() -> this.pollStats()
                                                           .getContainers()
                                                           .values()
                                                           .stream()
                                                           .flatMap(c -> c.getNetworkStats().values().stream())
                                                           .mapToDouble(i -> i.getTransmittedBytes())
                                                           .sum(),
                                                 RATE_POLLING_PERIOD);
            storageRateTracker = new RateTracker(() -> this.pollStats()
                                                           .getContainers()
                                                           .values()
                                                           .stream()
                                                           .flatMap(c -> c.getStorageStats().getVolumes().stream())
                                                           .filter(v -> OPERATION_FOR_STORAGE.equals(v.getOperation()))
                                                           .mapToDouble(v -> v.getValue())
                                                           .sum(),
                                                 RATE_POLLING_PERIOD);
        } catch (Throwable t) {
            throw new ConsumerInternalException("Failed while starting up TaskSystemUtil.", t);
        }
    }

    protected long getTaskLimit(String limitKey) {
        //NOTE: We don't use Task limits because of the complication it can add.
        //      For example, the CPU is listed in processors instead of VCPUs at
        //      the moment. Instead, sum containers.
        TaskMetadata taskMetadata = this.pollMetadata();
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

    /**
     * Polls the latest ContainerStats from the Metadata endpoint and logs the result.
     */
    protected TaskStats pollStats () {
        TaskStats stats = Optional.ofNullable(statsClient.call())
                                  .orElseThrow(() -> new ConsumerDependencyException("Couldn't gather stats from endpoint."));
        log.debug("Pulled task stats: " + stats);
        return stats;
    }

    /**
     * Polls the latest ContainerStats from the Metadata endpoint and logs the result.
     */
    protected TaskMetadata pollMetadata () {
        TaskMetadata stats = Optional.ofNullable(metadataClient.call())
                                     .orElseThrow(() -> new ConsumerDependencyException("Couldn't gather metadata from endpoint."));
        log.debug("Pulled task metadata: " + stats);
        return stats;
    }

    @Override
    public long getFreeMemory(DigitalUnit unit) {
        return unit.from(getTotalMemory(DigitalUnit.BYTES) - getUsedMemory(DigitalUnit.BYTES),
                         DigitalUnit.BYTES);
    }

    public long getTotalMemory(DigitalUnit unit) {
        return unit.from(getTaskLimit(MEMORY_LIMIT_KEY), DigitalUnit.MEGABYTES);
    }

    public long getUsedMemory(DigitalUnit unit) {
        return unit.from(this.pollStats()
                                       .getContainers()
                                       .values()
                                       .stream()
                                       .mapToLong(c -> c.getMemoryStats().getUsage())
                                       .sum(),
                                   DigitalUnit.BYTES);
    }

    public double getMemoryPercentage() {
        return (double)getUsedMemory(DigitalUnit.BYTES) /
                (double)getTotalMemory(DigitalUnit.BYTES);
    }

    public double getCpuPercentageOfSystemUsedByThisTask() {
        TaskStats stats = this.pollStats();
        if(stats.getContainers().values().stream().allMatch(t -> t != null && t.hasPreviousCpuStats())) {
            long systemUsage = stats.getContainers().values().stream().findFirst().map(c -> c.getCpuStats().getSystemCpuUsage()).orElse(0L)
                    - stats.getContainers().values().stream().findFirst().map(c -> c.getPreviousCpuStats().getSystemCpuUsage()).orElse(0L);
            long containerUsage = stats.getContainers().values().stream().mapToLong( c -> c.getCpuStats().getCpuUsage().getTotalUsage()).sum()
                    - stats.getContainers().values().stream().mapToLong( c -> c.getPreviousCpuStats().getCpuUsage().getTotalUsage()).sum();
            // This seems odd originally, but that's because CPU isn't a "measurable" resource. It's
            // just how many cycles out of the total amount the CPU run were taken by this container.
            return systemUsage == 0 ? 0.0 : (double)containerUsage / (double)systemUsage;
        } else {
            log.warn("Previous CPU stats were still null on TaskMetadata endpoint. Resolving to 0.");
            return 0.0;
        }
    }

    public double getCpuPercentageAllocatedToThisTask() {
        long processors = this.pollStats()
                              .getContainers()
                              .values()
                              .stream()
                              .findFirst().get()
                              .getCpuStats()
                              .getOnlineCpus();
        return (double)getTotalCpu(DigitalUnit.VCPU)
                / (double)(processors * getUnitsPerProcessor());
    }

    public double getCpuPercentage() {
        // If the we used 30% of the system, but we were allocated 60%: we have used 50% of our space.
        return getCpuPercentageOfSystemUsedByThisTask()
                / getCpuPercentageAllocatedToThisTask();
    }

    @Override
    public long getTotalCpu(DigitalUnit unit) {
        return unit.from(this.getTaskLimit(CPU_LIMIT_KEY), DigitalUnit.VCPU);
    }

    @Override
    public long getUsedCpu (DigitalUnit unit) {
        // If we have our container is using 50% of it's reserved limit and a CPU using
        return unit.from((long)(this.getCpuPercentage() * getTotalCpu(DigitalUnit.VCPU)), DigitalUnit.VCPU);
    }


    @Override
    public long getStorageUsage (DigitalUnit unit) {
        Preconditions.checkArgument(unit.isRate(), "Expected a rate based metric.");
        return unit.from(networkRateTracker.getLatestRate(TimeUnit.SECONDS)
                                           .map(d -> (long)Math.round(d))
                                           .orElse(0L),
                         DigitalUnit.BYTES_PER_SECOND);
    }

    @Override
    public long getNetworkUsage (DigitalUnit unit) {
        Preconditions.checkArgument(unit.isRate(), "Expected a rate based metric.");
        return unit.from(storageRateTracker.getLatestRate(TimeUnit.SECONDS)
                                           .map(d -> (long)Math.round(d))
                                           .orElse(0L),
                         DigitalUnit.BYTES_PER_SECOND);
    }
}
