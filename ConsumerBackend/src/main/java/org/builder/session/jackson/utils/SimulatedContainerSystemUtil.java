package org.builder.session.jackson.utils;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.time.Duration;
import java.util.List;

import com.sun.management.OperatingSystemMXBean;

import lombok.NonNull;
import sun.management.ManagementFactoryHelper;

/**
 * Due to some of the issues with measuring container limits from inside a container, instead
 * we use some knowledge of CPU shares and Java memory limits to simulate it.
 */
public class SimulatedContainerSystemUtil implements SystemUtil {

    private static final Duration STARTUP_TIME = Duration.ofSeconds(3);

    @NonNull
    private final OperatingSystemMXBean system = (OperatingSystemMXBean)ManagementFactoryHelper.getOperatingSystemMXBean();
    @NonNull
    private final List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
    @NonNull
    private final double percentageOfSystemCpu;
    @NonNull
    private final double percentageOfSystemMemory;

    public SimulatedContainerSystemUtil(long memoryLimitInMB, long cpuShares) {
        try {
            //Need some time to initialize since we need the very first operation to be accurate...
            system.getTotalPhysicalMemorySize();
            system.getSystemCpuLoad();
            Thread.sleep(3);

            //We need to store the ratio for this containers limits.
            long totalSystemUnits = (this.getUnitsPerProcessor() * system.getAvailableProcessors());
            this.percentageOfSystemCpu = (double) cpuShares / totalSystemUnits;
            this.percentageOfSystemMemory = (double) MemoryUnit.convert(memoryLimitInMB, MemoryUnit.MEGABYTES, MemoryUnit.BYTES)
                    / (double)system.getTotalPhysicalMemorySize();
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to initialize SimulatedContainerSystemUtil.", t);
        }
    }

    @Override
    public long getFreeMemory(MemoryUnit unit) {
        return this.getTotalMemory(unit) - this.getUsedMemory(unit);
    }

    public long getTotalMemory(MemoryUnit unit) {
        return MemoryUnit.convert((long)((double)system.getTotalPhysicalMemorySize() * this.percentageOfSystemMemory),
                                  MemoryUnit.BYTES,
                                  unit);
    }

    public long getUsedMemory(MemoryUnit unit) {
        //TODO: Need to implement this more accurately. Currently uses the Java process to indicate memory usage for the container which is inaccurate.
        long totalUsageInBytes = memoryPoolMXBeans.stream().mapToLong(pool -> pool.getUsage().getUsed()).sum();
        return MemoryUnit.convert(totalUsageInBytes, MemoryUnit.BYTES, unit);
    }

    public double getMemoryPercentage() {
        return (double)this.getUsedMemory(MemoryUnit.BYTES) /
                (double)this.getTotalMemory(MemoryUnit.BYTES);
    }



    public double getCpuPercentage() {
        //TODO: Need to implement this more accurately. Currently uses the Java process to indicate CPU usage for the container which is inaccurate.
        return system.getProcessCpuLoad() / this.percentageOfSystemCpu;
    }

    public long getCpuUnitsTotal() {
        return (long)((double)this.system.getAvailableProcessors() * 1024.0 * this.percentageOfSystemCpu);
    }

    public long getCpuUnitsUtilized () {
        return (long)(this.getCpuPercentage() * getCpuUnitsTotal());
    }


}
