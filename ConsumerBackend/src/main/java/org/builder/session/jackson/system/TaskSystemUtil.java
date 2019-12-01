package org.builder.session.jackson.system;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.builder.session.jackson.client.SimpleClient;
import org.builder.session.jackson.client.ecs.TaskMetadataClient;
import org.builder.session.jackson.client.messages.TaskMetadata;
import org.builder.session.jackson.client.messages.TaskStats;
import org.builder.session.jackson.exception.ConsumerDependencyException;
import org.builder.session.jackson.exception.ConsumerInternalException;
import org.builder.session.jackson.utils.RateTracker;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

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
            networkRateTracker = new RateTracker("NetworkTransmitTracker",
                                                 () -> this.pollStats()
                                                           .getContainers()
                                                           .values()
                                                           .stream()
                                                           // Network metrics sometimes begin as NULL.
                                                           .flatMap(c -> Optional.ofNullable(c)
                                                                                 .map(o -> o.getNetworkStats())
                                                                                 .orElseGet(() -> Maps.newHashMap())
                                                                                 .values()
                                                                                 .stream())
                                                           // We just track written bytes since roughly Read == Write at the moment.
                                                           .mapToDouble(i -> Optional.ofNullable(i)
                                                                                     .map(o -> o.getTransmittedBytes())
                                                                                     .orElse(0L))
                                                           .sum(),
                                                 RATE_POLLING_PERIOD);
            storageRateTracker = new RateTracker("StorageWriteTracker",
                                                 () -> this.pollStats()
                                                           .getContainers()
                                                           .values()
                                                           .stream()
                                                           .flatMap(c -> Optional.ofNullable(c)
                                                                                 .map(o -> o.getStorageStats())
                                                                                 .map(o -> o.getVolumes())
                                                                                 .orElseGet(() -> Lists.newArrayList())
                                                                                 .stream())
                                                           // We just track written bytes since roughly Read == Write at the moment.
                                                           .filter(v -> OPERATION_FOR_STORAGE.equals(v.getOperation()))
                                                           .mapToDouble(v -> Optional.ofNullable(v.getValue())
                                                                                     .orElse(0L))
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
        TaskStats taskStats = this.pollStats();
        return unit.from(taskStats.getContainers()
                                  .values()
                                  .stream()
                                  .mapToLong(c -> Optional.ofNullable(c)
                                                          .map(o -> o.getMemoryStats())
                                                          .map(o -> o.getUsage())
                                                          .orElseGet(() -> {
                                                              //Default to 0, but log the issue
                                                              log.warn("Defaulting memory to 0 due to poll stats: {}",
                                                                       taskStats);
                                                              return 0L;
                                                          }))
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
            log.warn("Previous CPU stats were still null on TaskMetadata endpoint. Defaulting to 0.");
            return 0.0;
        }
    }

    public double getCpuPercentageAllocatedToThisTask() {
        TaskStats pollStats = this.pollStats();
        long processors = pollStats
                              .getContainers()
                              .values()
                              .stream()
                              .map(c -> Optional.ofNullable(c)
                                                .map(o -> o.getCpuStats())
                                                .map(o -> o.getOnlineCpus()))
                              .filter(c -> c.isPresent())
                              .map(c -> c.get())
                              .findFirst()
                              .orElseGet(() -> { //Default to 1, but log the issue
                                  log.warn("Defaulting online CPUs to 1 due to poll stats: {}",
                                           pollStats);
                                  return 1L;
                              });
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
        return unit.from(networkRateTracker.getLatestRate(unit.getTimeUnit())
                                           .map(d -> (long)Math.round(d))
                                           .orElse(0L),
                         DigitalUnit.BYTES_PER_SECOND);
    }

    @Override
    public long getNetworkUsage (DigitalUnit unit) {
        Preconditions.checkArgument(unit.isRate(), "Expected a rate based metric.");
        return unit.from(storageRateTracker.getLatestRate(unit.getTimeUnit())
                                           .map(d -> (long)Math.round(d))
                                           .orElse(0L),
                         DigitalUnit.BYTES_PER_SECOND);
    }
}
