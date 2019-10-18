package org.builder.session.jackson.utils;

import lombok.RequiredArgsConstructor;

public interface SystemUtil {

    public long getFreeMemory(MemoryUnit unit);
    public long getTotalMemory(MemoryUnit unit);
    public long getUsedMemory(MemoryUnit unit);
    public double getMemoryPercentage();

    public double getCpuPercentage();
    public long getCpuUnitsTotal();
    public long getCpuUnitsUtilized ();
    public default long getUnitsPerProcessor() {
        return 1024;
    }

    public default String toMemoryString() {
        long usedMemory = this.getUsedMemory(SystemUtil.MemoryUnit.MEGABYTES);
        long totalMemory = this.getTotalMemory(SystemUtil.MemoryUnit.MEGABYTES);
        double percentMemory = this.getMemoryPercentage();
        return "Profiled: [ MemoryUsed / MemoryTotal (%): " + usedMemory + "MB / " + totalMemory + "MB (" + percentMemory + ") ]";
    }

    public default String toCpuString() {
        long usedCpu = this.getCpuUnitsUtilized();
        long totalCpu = this.getCpuUnitsTotal();
        double percentCpu = usedCpu / (double)totalCpu;
        return"Profiled: [ CpuUsed / CpuTotal (%): " + usedCpu + "vCPU / " + totalCpu + "vCPU (" + percentCpu + ") ]";
    }

    @RequiredArgsConstructor
    public enum MemoryUnit {
        BYTES(1),
        KILOBYTES(1024),
        MEGABYTES(1024 * 1024),
        GIGABYTES(1024 * 1024 * 1024);

        private final long toBytesDivisor;

        public static long convert(long value, MemoryUnit source, MemoryUnit goal) {
            return (value * source.toBytesDivisor) / goal.toBytesDivisor ;
        }
    }
}
