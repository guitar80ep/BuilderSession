package org.builder.session.jackson.system;

public interface SystemUtil {

    public long getFreeMemory(DigitalUnit unit);
    public long getTotalMemory(DigitalUnit unit);
    public long getUsedMemory(DigitalUnit unit);
    public double getMemoryPercentage();

    public long getTotalCpu (DigitalUnit unit);
    public long getUsedCpu (DigitalUnit unit);
    public default long getUnitsPerProcessor() {
        return 1024;
    }
    public double getCpuPercentage();

    public long getNetworkUsage(DigitalUnit unit);

    public long getStorageUsage(DigitalUnit unit);

    public default String toMemoryString() {
        long usedMemory = this.getUsedMemory(DigitalUnit.MEGABYTES);
        long totalMemory = this.getTotalMemory(DigitalUnit.MEGABYTES);
        double percentMemory = this.getMemoryPercentage();
        return "Profiled: [ MemoryUsed / MemoryTotal (%): " + usedMemory + "MB / " + totalMemory + "MB (" + percentMemory + ") ]";
    }

    public default String toCpuString() {
        long usedCpu = this.getUsedCpu(DigitalUnit.VCPU);
        long totalCpu = this.getTotalCpu(DigitalUnit.VCPU);
        double percentCpu =DigitalUnit.VCPU.toPercentage(usedCpu, totalCpu);
        return"Profiled: [ CpuUsed / CpuTotal (%): " + usedCpu + "vCPU / " + totalCpu + "vCPU (" + percentCpu + ") ]";
    }
}
