package org.builder.session.jackson.utils;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

import org.build.session.jackson.proto.Resource;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.AtomicDouble;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Profiles the refresh rate for the supplied SystemUtil.
 */
@Slf4j
public class Profiler implements Closeable {
    // Used to help generate random CPU usage for profiling.
    public static final int PROFILING_MAX_SLEEP_IN_NANOS = 3000000;
    public static final int PROFILING_MAX_COMPUTATIONS = 1000000;
    public static final Map<Resource, Function<SystemUtil, Double>> RESOURCES_TO_PROFILE
            = ImmutableMap.<Resource, Function<SystemUtil, Double>>builder()
                          .put(Resource.CPU, s -> s.getCpuPercentage())
                          .put(Resource.MEMORY, s -> s.getMemoryPercentage())
                          .build();

    @NonNull
    private final ExecutorService executor;
    @NonNull
    private final Future consumer;
    @NonNull
    private final Duration totalDuration;
    @NonNull
    private final Random random;

    public Profiler(Duration totalDuration) {
        this.totalDuration = totalDuration;
        this.random = new Random(Instant.now().toEpochMilli());
        this.executor = Executors.newSingleThreadScheduledExecutor();
        this.consumer = executor.submit(() -> {
            Random random = new Random();
            while(true) {
                int sleepTimeInNanos = random.nextInt(PROFILING_MAX_SLEEP_IN_NANOS);
                int computation = random.nextInt(PROFILING_MAX_COMPUTATIONS);
                AtomicDouble value = new AtomicDouble();
                random.doubles(computation).parallel().forEach(d -> {
                    value.addAndGet(d);
                });
                Thread.sleep(0, sleepTimeInNanos);
            }
        });
    }

    public void profile (SystemUtil utilToProfile) throws Exception {
        Instant profileStart = Instant.now();

        //Set inital state and StatTrackers...
        Map<Resource, StatTracker> statsMap = new HashMap<>();
        Map<Resource, Instant> lastRefreshMap = new HashMap<>();
        RESOURCES_TO_PROFILE.entrySet().forEach(e -> {
            statsMap.put(e.getKey(), new StatTracker(e.getValue().apply(utilToProfile)));
            lastRefreshMap.put(e.getKey(), Instant.now());
        });

        //Now to actually profile...
        while (Duration.between(profileStart, Instant.now()).toMillis() < totalDuration.toMillis()) {
            statsMap.entrySet().forEach(e -> {
                Resource resource = e.getKey();
                StatTracker stat = e.getValue();
                double currentValue = RESOURCES_TO_PROFILE.get(resource).apply(utilToProfile);
                if(Double.compare(currentValue, stat.getPrevious()) != 0) {
                    //We have a change or an update!!
                    Instant timeNow = Instant.now();
                    Instant previousRefresh = lastRefreshMap.put(resource, timeNow);
                    Duration delayForRefresh = Duration.between(previousRefresh, timeNow);
                    stat.addStat(delayForRefresh.toMillis());
                }
            });
            Thread.sleep(1);
        }

        log.info(utilToProfile.toCpuString());
        log.info(utilToProfile.toMemoryString());
        statsMap.entrySet().forEach(e -> {
            Resource resource = e.getKey();
            StatTracker stat = e.getValue();
            log.info("Found {} RefreshRate (ms): {}", resource.name(), stat);
        });
    }

    @Override
    public void close () throws IOException {
        consumer.cancel(true);
        executor.shutdown();
    }
}
