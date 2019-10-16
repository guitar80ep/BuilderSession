package org.builder.session.jackson.workflow;

import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.builder.session.jackson.workflow.utilize.Consumer;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Workflow {
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<String, ConsumerHandle> consumers = new ConcurrentHashMap<>();

    public void consume(@NonNull Consumer consumer) {
        AtomicBoolean computed = new AtomicBoolean(false);
        consumers.computeIfAbsent(consumer.getName(), name -> {
            Future future = executor.submit(() -> consumer.consume());
            computed.set(true);
            return new ConsumerHandle(name, future, consumer);
        });

        if(!computed.get()) {
            throw new IllegalArgumentException(
                    "Attempted to supply a consumer that already is register for this name "
                            + consumer.getName() + ".");
        }
    }

    public void cancel(@NonNull String name) {
        Optional<ConsumerHandle> handle = Optional.ofNullable(consumers.remove(name));
        handle.ifPresent(h -> h.cancel());
    }

    @RequiredArgsConstructor
    public class ConsumerHandle {
        @Getter
        @NonNull
        private final String id;
        @NonNull
        private final Future thread;
        @NonNull
        private final Consumer consumer;

        public void cancel() {
            if(!thread.cancel(true)) {
                throw new CancellationException("Failed to cancel consumer " + this.id + ".");
            }
            consumer.close();
        }
    }
}
