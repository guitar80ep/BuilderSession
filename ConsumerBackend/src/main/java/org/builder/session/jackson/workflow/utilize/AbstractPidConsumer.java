package org.builder.session.jackson.workflow.utilize;

import java.io.Closeable;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractPidConsumer implements Consumer {

    public static final Duration DELAY_BETWEEN_LOGS = Duration.ofSeconds(1);

    @NonNull
    private final PIDConfig config;
    private long previousError = 0;
    private long totalError = 0;
    // We choose this because we can be more clear about the memory and performance implications as it grows.
    private final ConcurrentLinkedQueue<Load> loadSet = new ConcurrentLinkedQueue<>();

    protected abstract long getConsumed ();

    protected abstract long getGoal ();

    protected abstract Load generateLoad ();

    protected int getLoadSize () {
        return loadSet.size();
    }

    protected Duration getRunDelay () {
        return config.getPace();
    }

    @Override
    public final void consume () {
        Instant previousLog = Instant.now();
        while (true) {
            try {
                long goal = getGoal();
                long consumed = getConsumed();
                long currentError = goal - consumed;
                double p = (currentError * config.getProportionFactor());
                double d = ((currentError - previousError) * config.getDerivativeFactor());
                double i = (totalError * config.getIntegralFactor());
                long scale = (long) (p + i + d);
                if (scale > 0) {
                    loadSet.addAll(generateLoad(scale));
                } else {
                    for (int k = 0; k < -scale && !loadSet.isEmpty(); k++) {
                        loadSet.poll();
                    }

                    if (loadSet.size() == 0) {
                        //Reset total error if the loadSet has been emptied.
                        totalError = 0;
                    }
                }
                previousError = currentError;
                totalError *= config.getIntegralDecay();
                totalError += currentError;
                if (Duration.between(previousLog, Instant.now()).toMillis() >= DELAY_BETWEEN_LOGS.toMillis()) {
                    previousLog = Instant.now();
                    log.debug("Status of {}: [Goal: {}, Consumed: {}, Load: {}, P: {}, D: {}, I: {} = S: {}]",
                              new Object[] { this.getClass().getSimpleName(),
                                             goal,
                                             consumed,
                                             this.getLoadSize(),
                                             p, d, i,
                                             scale });
                }

                Thread.sleep(config.getPace().toMillis());
            } catch (Throwable t) {
                log.error("Caught an exception while consuming resources for {}. Swallowing.", this.getClass().getSimpleName(), t);
            }
        }
    }

    private final Set<Load> generateLoad (long scale) {
        Set<Load> generated = new HashSet<>();
        for (long i = 0; i < scale; i++) {
            generated.add(generateLoad());
        }
        return generated;
    }

    @Override
    public void close () {
        loadSet.forEach(c -> c.close());
        loadSet.clear();
    }

    @FunctionalInterface
    protected interface Load extends Closeable {

        public void close ();
    }

}
