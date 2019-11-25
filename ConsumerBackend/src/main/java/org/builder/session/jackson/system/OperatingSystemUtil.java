package org.builder.session.jackson.system;

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
    public long getFreeMemory (DigitalUnit unit) {
        return unit.from(system.getFreePhysicalMemorySize(), DigitalUnit.BYTES);
    }

    @Override
    public long getUsedMemory (DigitalUnit unit) {
        return unit.from(getTotalMemory(DigitalUnit.BYTES) - getFreeMemory(DigitalUnit.BYTES), DigitalUnit.BYTES);
    }

    @Override
    public long getTotalMemory (DigitalUnit unit) {
        return unit.from(system.getTotalPhysicalMemorySize(), DigitalUnit.BYTES);
    }

    @Override
    public double getMemoryPercentage () {
        return (double) getFreeMemory(DigitalUnit.BYTES)
                / (double) getTotalMemory(DigitalUnit.BYTES);
    }




    @Override
    public double getCpuPercentage () {
        return system.getSystemCpuLoad();
    }

    @Override
    public long getTotalCpu (DigitalUnit unit) {
        return unit.from(system.getAvailableProcessors() * this.getUnitsPerProcessor(), DigitalUnit.VCPU);
    }

    @Override
    public long getUsedCpu (DigitalUnit unit) {
        return unit.from((long)((double)this.getUsedCpu(DigitalUnit.VCPU) * getCpuPercentage()), DigitalUnit.VCPU);
    }


    @Override
    public long getNetworkUsage (DigitalUnit unit) {
        throw new UnsupportedOperationException("Unimplemented.");
    }

    @Override
    public long getStorageUsage (DigitalUnit unit) {
        throw new UnsupportedOperationException("Unimplemented.");
    }

}
