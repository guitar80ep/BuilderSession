package org.builder.session.jackson.utils;

import com.sun.management.OperatingSystemMXBean;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import sun.management.ManagementFactoryHelper;

/**
 * NOTE: This doesn't work with containers because it reads
 * the value for the host they run on. Read below for some of
 * the limitations.
 *
 * https://fabiokung.com/2014/03/13/memory-inside-linux-containers/
 */
@RequiredArgsConstructor
public class OperatingSystemUtil implements SystemUtil {

    @NonNull
    private final OperatingSystemMXBean system =  (OperatingSystemMXBean) ManagementFactoryHelper.getOperatingSystemMXBean();

    @Override
    public long getFreeMemory (MemoryUnit unit) {
        return MemoryUnit.convert(system.getFreePhysicalMemorySize(), MemoryUnit.BYTES, unit);
    }

    @Override
    public long getUsedMemory (MemoryUnit unit) {
        return getTotalMemory(unit) - getFreeMemory(unit);
    }

    @Override
    public long getTotalMemory (MemoryUnit unit) {
        return MemoryUnit.convert(system.getTotalPhysicalMemorySize(), MemoryUnit.BYTES, unit);
    }

    @Override
    public double getMemoryPercentage () {
        return (double) getFreeMemory(MemoryUnit.BYTES)
                / (double) getTotalMemory(MemoryUnit.BYTES);
    }

    @Override
    public double getCpuPercentage () {
        return system.getSystemCpuLoad();
    }

    @Override
    public long getCpuUnitsTotal () {
        return system.getAvailableProcessors() * this.getUnitsPerProcessor();
    }

    @Override
    public long getCpuUnitsUtilized () {
        return (long)( (double) this.getCpuUnitsTotal() * getCpuPercentage());
    }
}
