package org.builder.session.jackson.workflow.utilize;

import java.io.Closeable;
import java.time.Duration;
import java.time.Instant;
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

    public static final Duration DELAY_BETWEEN_LOGS = Duration.ofSeconds(30);

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
            Instant previousLog = Instant.now();
            while (true) {
                long goal = getGoal();
                long consumed = getConsumed();
                long currentError = goal - consumed;
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

                    if(loadSet.size() == 0) {
                        //Reset total error if the loadSet has been emptied.
                        totalError = 0;
                    }
                }
                previousError = currentError;
                totalError += currentError;
                if(Duration.between(previousLog, Instant.now()).toMillis() >= DELAY_BETWEEN_LOGS.toMillis()) {
                    previousLog = Instant.now();
                    log.debug("Status of {}: [Goal: {}, Consumed: {}, P: {}, D: {}, I: {} = S: {}]", new Object[] {
                                      this.getClass().getSimpleName(), goal, consumed, p, d, i, scale
                    });
                }

                Thread.sleep(config.getPace().toMillis());
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
