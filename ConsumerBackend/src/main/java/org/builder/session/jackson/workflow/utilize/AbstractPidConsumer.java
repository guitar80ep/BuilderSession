package org.builder.session.jackson.workflow.utilize;

import java.io.Closeable;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.builder.session.jackson.utils.PIDConfig;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractPidConsumer implements Consumer {

    public static final Duration DELAY_BETWEEN_ADJUSTMENTS = Duration.ofMillis(50);
    @NonNull
    private final PIDConfig config;
    private long previousError = 0;
    private long totalError = 0;
    public final List<Load> loadSet = new LinkedList<>();

    protected abstract long getConsumed();
    protected abstract long getGoal();
    protected abstract Load generateLoad();

    @Override
    public final void consume () {
        try {
            while (true) {
                long currentError = getGoal() - getConsumed();
                double p = (currentError * config.getProportionFactor());
                double d = ((currentError - previousError) * config.getDerivativeFactor());
                double i = (totalError * config.getIntegralFactor());
                long scale = (long)(p + i + d);
                if(scale > 0) {
                    loadSet.addAll(generateLoad(scale));
                } else {
                    for(int k = 0; k < -scale && !loadSet.isEmpty(); k++) {
                        loadSet.remove(loadSet.size() - 1).close();
                    }
                }
                previousError = currentError;
                totalError += currentError;
                Thread.sleep(DELAY_BETWEEN_ADJUSTMENTS.toMillis());
            }
        } catch (Throwable t) {
            log.error("Caught an exception while consuming resources for {}. Swallowing.",
                      this.getClass().getSimpleName(),
                      t);
        }
    }

    private final Set<Load> generateLoad(long scale) {
        Set<Load> generated = new HashSet<>();
        for(long i = 0; i < scale; i++) {
            generated.add(generateLoad());
        }
        return generated;
    }

    @Override
    public final void close () {
        loadSet.forEach(c -> c.close());
        loadSet.clear();
    }

    @FunctionalInterface
    protected interface Load extends Closeable {
        public void close();
    }

}
