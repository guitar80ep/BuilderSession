package org.builder.session.jackson.utils;

import com.google.common.util.concurrent.AtomicDouble;
import com.sun.management.OperatingSystemMXBean;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import sun.management.ManagementFactoryHelper;

@RequiredArgsConstructor
public class SystemUtilImpl implements SystemUtil {

    @NonNull
    private final OperatingSystemMXBean system =  (OperatingSystemMXBean) ManagementFactoryHelper.getOperatingSystemMXBean();
    private AtomicDouble lastCpuUsage = new AtomicDouble(0.0);

    @Override
    public long getFreeMemory (MemoryUnit unit) {
        return MemoryUnit.convert(system.getFreePhysicalMemorySize(), MemoryUnit.BYTES, unit);
    }

    @Override
    public long getTotalMemory (MemoryUnit unit) {
        return MemoryUnit.convert(system.getTotalPhysicalMemorySize(), MemoryUnit.BYTES, unit);
    }

    @Override
    public double getCpuPerentage () {
        // This is cached because sometimes it reads incorrectly.
        double usage = system.getSystemCpuLoad();
        if(usage < 0) {
            return lastCpuUsage.get();
        } else {
            lastCpuUsage.set(usage);
            return usage;
        }
    }

    @Override
    public long getTotalProcessors () {
        return system.getAvailableProcessors();
    }
}
