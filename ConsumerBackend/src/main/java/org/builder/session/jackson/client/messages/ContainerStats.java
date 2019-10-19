package org.builder.session.jackson.client.messages;

import java.sql.Timestamp;
import java.util.List;

import com.google.gson.annotations.SerializedName;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContainerStats {
    private final Timestamp read;
    @SerializedName("memory_stats")
    private final MemoryStats memoryStats;
    @SerializedName("precpu_stats")
    private final CpuStats preCpuStats;
    @SerializedName("cpu_stats")
    private final CpuStats cpuStats;

    @Data
    @Builder
    public class MemoryStats {
        private final Stats stats;
        @SerializedName("max_usage")
        private final Long maxUsage;
        private final Long usage;
        @SerializedName("failcnt")
        private final Long failureCount;
        private final Long limit;
        
        @Builder
        @Data
        public class Stats {
            @SerializedName("active_anon")
            private Long activeAnon;
            @SerializedName("active_file")
            private Long activeFile;
            private Long cache;
            private Long dirty;
            @SerializedName("hierarchical_memory_limit")
            private Long hierarchicalMemoryLimit;
            @SerializedName("hierarchical_memsw_limit")
            private Long hierarchicalMemSwapLimit;
            @SerializedName("inactive_anon")
            private Long  inactiveAnon;
            @SerializedName("inactive_file")
            private Long inactiveFile;
            @SerializedName("mapped_file")
            private Long mappedFile;
            @SerializedName("pgfault")
            private Long pageFault;
            @SerializedName("pgmajfault")
            private Long pageMajFault;
            @SerializedName("pgpgin")
            private Long pagePageIn;
            @SerializedName("pgpgout")
            private Long pagePageOut;
            private Long rss;
            @SerializedName("rss_huge")
            private Long rssHuge;
            @SerializedName("swap")
            private Long swap;
            @SerializedName("total_active_anon")
            private Long totalActiveAnon;
            @SerializedName("total_active_file")
            private Long totalActiveFile;
            @SerializedName("total_cache")
            private Long totalCache;
            @SerializedName("total_dirty")
            private Long totalDirty;
            @SerializedName("total_inactive_anon")
            private Long totalInactiveAnon;
            @SerializedName("total_inactive_file")
            private Long totalInactiveFile;
            @SerializedName("total_mapped_file")
            private Long totalMappedFile;
            @SerializedName("total_pgfault")
            private Long totalPgfault;
            @SerializedName("total_pgmajfault")
            private Long totalPgmajfault;
            @SerializedName("total_pgpgin")
            private Long totalPgpgin;
            @SerializedName("total_pgpgout")
            private Long totalPgpgout;
            @SerializedName("total_rss")
            private Long totalRss;
            @SerializedName("total_rss_huge")
            private Long totalRssHuge;
            @SerializedName("total_swap")
            private Long totalSwap;
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
    public class CpuStats {
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
        public class CpuUsageStats {
            @SerializedName("total_usage")
            private Long totalUsage;
            @SerializedName("percpu_usage")
            private List<Long> perCpuUsage;
            @SerializedName("usage_in_kernelmode")
            private Long usageInKernelMode;
            @SerializedName("usage_in_usermode")
            private Long usageInUserMode;
        }

        @Data
        @Builder
        public class ThrottlingDataStats {
            private Long periods;
            @SerializedName("throttled_periods")
            private Long throttledPeriods;
            @SerializedName("throttled_time")
            private Long throttledTime;
        }
    }


}
