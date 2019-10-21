package org.builder.session.jackson.workflow.utilize;

import java.time.Duration;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractPidConsumer implements Consumer {

    @NonNull
    private final PIDConfig config;
    private long previousError = 0;
    private long totalError = 0;
    private long load = 0;

    protected abstract long getConsumed ();

    protected abstract long getGoal ();

    protected abstract void generateLoad (long scale);

    protected abstract void destroyLoad (long scale);

    protected Duration getRunDelay () {
        return config.getPace();
    }

    @Override
    public final void consume () {
        while (true) {
            try {
                //PID algorithm with some slight modifications to avoid integral overtake.
                long goal = getGoal();
                long consumed = getConsumed();
                long currentError = goal - consumed;
                double p = (currentError * config.getProportionFactor());
                double d = ((currentError - previousError) * config.getDerivativeFactor());
                double i = (totalError * config.getIntegralFactor());
                long scale = (long) (p + i + d);
                load += scale;
                if (scale > 0) {
                    generateLoad(scale);
                } else {
                    destroyLoad(scale);

                    if (load <= 0) {
                        //Reset total error if the load has been emptied.
                        totalError = 0;
                    }
                }

                long signOfError = currentError == 0 ? 0 : currentError / Math.abs(currentError);
                long signOfPreviousError = previousError == 0 ? 0 : previousError / Math.abs(previousError);
                if(signOfError != signOfPreviousError) {
                    //Reset total error if the error changed signs between + and -
                    totalError = 0;
                }

                previousError = currentError;
                totalError *= config.getIntegralDecay();
                totalError += currentError;
                log.debug("Status of {}: [Goal: {}, Consumed: {}, Load: {}, P: {}, D: {}, I: {} = S: {}]",
                          new Object[] { this.getClass().getSimpleName(),
                                         goal,
                                         consumed,
                                         load,
                                         p, d, i,
                                         scale });
                Thread.sleep(config.getPace().toMillis());
            } catch (Throwable t) {
                log.error("Caught an exception while consuming resources for {}. Swallowing.", this.getClass().getSimpleName(), t);
            }
        }
    }

    @Override
    public void close () {

    }
}
