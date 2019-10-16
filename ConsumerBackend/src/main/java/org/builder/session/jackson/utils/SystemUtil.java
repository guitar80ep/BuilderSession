package org.builder.session.jackson.utils;

import lombok.RequiredArgsConstructor;

public interface SystemUtil {

    public long getFreeMemory(MemoryUnit unit);
    public long getTotalMemory(MemoryUnit unit);
    public default long getUsedMemory(MemoryUnit unit) {
        return getTotalMemory(unit) - getFreeMemory(unit);
    }

    public long getTotalProcessors();
    public double getCpuPerentage ();
    public default long getUnitsPerProcessor() {
        return 1024;
    }
    public default long getCpuUnitsTotal () {
        return getUnitsPerProcessor() * getTotalProcessors();
    }
    public default long getCpuUnitsUtilized () {
        return (long)(getCpuPerentage() * getCpuUnitsTotal());
    }

    public default String toMemoryString() {
        long usedMemory = this.getUsedMemory(SystemUtil.MemoryUnit.MEGABYTES);
        long totalMemory = this.getTotalMemory(SystemUtil.MemoryUnit.MEGABYTES);
        double percentMemory = usedMemory / (double)totalMemory;
        return "[ MemoryUsed / MemoryTotal (%): " + usedMemory + " / " + totalMemory + " (" + percentMemory + ") ]";
    }

    public default String toCpuString() {
        long usedCpu = this.getCpuUnitsUtilized();
        long totalCpu = this.getCpuUnitsTotal();
        double percentCpu = usedCpu / (double)totalCpu;
        return"[ CpuUSed / CpuTotal (%): " + usedCpu + " / " + totalCpu + " (" + percentCpu + ") ]";
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
