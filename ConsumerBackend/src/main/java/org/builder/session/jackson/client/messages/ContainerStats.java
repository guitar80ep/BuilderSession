package org.builder.session.jackson.client.messages;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContainerStats {
    private final ZonedDateTime read;
    @SerializedName("preread")
    private final ZonedDateTime preRead;
    @SerializedName("memory_stats")
    private final MemoryStats memoryStats;
    @SerializedName("precpu_stats")
    private final CpuStats previousCpuStats;
    @SerializedName("cpu_stats")
    private final CpuStats cpuStats;
    @SerializedName("networks")
    private final Map<String, NetworkInterfaceStats> networkStats;
    @SerializedName("blkio_stats")
    private final StorageStats storageStats;


    public boolean hasPreviousCpuStats() {
        return this.previousCpuStats != null
                && this.previousCpuStats.getOnlineCpus() != null
                && this.previousCpuStats.getSystemCpuUsage() != null;
    }

    @Data
    @Builder
    public static class MemoryStats {
        private final Stats stats;
        @SerializedName("max_usage")
        private final Long maxUsage;
        private final Long usage;
        @SerializedName("failcnt")
        private final Long failureCount;
        private final Long limit;
        
        @Builder
        @Data
        public static class Stats {
            @SerializedName("active_anon")
            private final Long activeAnon;
            @SerializedName("active_file")
            private final Long activeFile;
            private final Long cache;
            private final Long dirty;
            @SerializedName("hierarchical_memory_limit")
            private final Long hierarchicalMemoryLimit;
            @SerializedName("hierarchical_memsw_limit")
            private final Long hierarchicalMemSwapLimit;
            @SerializedName("inactive_anon")
            private final Long  inactiveAnon;
            @SerializedName("inactive_file")
            private final Long inactiveFile;
            @SerializedName("mapped_file")
            private final Long mappedFile;
            @SerializedName("pgfault")
            private final Long pageFault;
            @SerializedName("pgmajfault")
            private final Long pageMajFault;
            @SerializedName("pgpgin")
            private final Long pagePageIn;
            @SerializedName("pgpgout")
            private final Long pagePageOut;
            private final Long rss;
            @SerializedName("rss_huge")
            private final Long rssHuge;
            @SerializedName("swap")
            private final Long swap;
            @SerializedName("total_active_anon")
            private final Long totalActiveAnon;
            @SerializedName("total_active_file")
            private final Long totalActiveFile;
            @SerializedName("total_cache")
            private final Long totalCache;
            @SerializedName("total_dirty")
            private final Long totalDirty;
            @SerializedName("total_inactive_anon")
            private final Long totalInactiveAnon;
            @SerializedName("total_inactive_file")
            private final Long totalInactiveFile;
            @SerializedName("total_mapped_file")
            private final Long totalMappedFile;
            @SerializedName("total_pgfault")
            private final Long totalPgfault;
            @SerializedName("total_pgmajfault")
            private final Long totalPgmajfault;
            @SerializedName("total_pgpgin")
            private final Long totalPgpgin;
            @SerializedName("total_pgpgout")
            private final Long totalPgpgout;
            @SerializedName("total_rss")
            private final Long totalRss;
            @SerializedName("total_rss_huge")
            private final Long totalRssHuge;
            @SerializedName("total_swap")
            private final Long totalSwap;
            @SerializedName("total_unevictable")
            private final Long totalUnevictable;
            @SerializedName("total_writeback")
            private final Long totalWriteback;
            private final Long unevictable;
            private final Long writeback;
        }
    }

    @Data
    @Builder
    public static class CpuStats {
        @SerializedName("cpu_usage")
        private final CpuUsageStats cpuUsage;
        @SerializedName("system_cpu_usage")
        private final Long systemCpuUsage;
        @SerializedName("online_cpus")
        private final Long onlineCpus;
        @SerializedName("throttling_data")
        private final ThrottlingDataStats throttlingData;

        @Data
        @Builder
        public static class CpuUsageStats {
            @SerializedName("total_usage")
            private final Long totalUsage;
            @SerializedName("percpu_usage")
            private final List<Long> perCpuUsage;
            @SerializedName("usage_in_kernelmode")
            private final Long usageInKernelMode;
            @SerializedName("usage_in_usermode")
            private final Long usageInUserMode;
        }

        @Data
        @Builder
        public static class ThrottlingDataStats {
            private final Long periods;
            @SerializedName("throttled_periods")
            private final Long throttledPeriods;
            @SerializedName("throttled_time")
            private final Long throttledTime;
        }
    }

    @Data
    @Builder
    public static class StorageStats {

        @SerializedName("io_service_bytes_recursive")
        private final List<VolumeStats> volumes;

        @Builder
        @Data
        public static class VolumeStats {
            @SerializedName("major")
            private final Long major;
            @SerializedName("minor")
            private final Long minor;
            @SerializedName("op")
            private final String operation;
            @SerializedName("value")
            private final Long value;
        }
    }

    @Builder
    @Data
    public static class NetworkInterfaceStats {

        @SerializedName("rx_bytes")
        private final Long receivedBytes;
        @SerializedName("rx_dropped")
        private final Long receivedDropped;
        @SerializedName("rx_error")
        private final Long receivedError;
        @SerializedName("rx_packets")
        private final Long receivedPackets;

        @SerializedName("tx_bytes")
        private final Long transmittedBytes;
        @SerializedName("tx_dropped")
        private final Long transmittedDropped;
        @SerializedName("tx_error")
        private final Long transmittedError;
        @SerializedName("tx_packets")
        private final Long transmittedPackets;
    }


}
