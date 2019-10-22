package org.builder.session.jackson.client.wrapper;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.builder.session.jackson.client.Client;
import org.builder.session.jackson.client.SimpleClient;
import org.builder.session.jackson.exception.ConsumerClientException;
import org.builder.session.jackson.exception.ConsumerDependencyException;
import org.builder.session.jackson.exception.ConsumerInternalException;
import org.builder.session.jackson.utils.NoArgs;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;

import lombok.Getter;
import lombok.NonNull;

public class CachedClient<INPUT, OUTPUT> implements Client<INPUT, OUTPUT> {

    public static <IN, OUT> Client<IN, OUT> wrap(@NonNull final Client<IN, OUT> client,
                                                 @NonNull final Duration timeToLive,
                                                 boolean doBackgroundRefresh) {
        return new CachedClient<>(client, timeToLive, doBackgroundRefresh);
    }

    public static <OUT> SimpleClient<OUT> wrap(@NonNull final SimpleClient<OUT> client,
                                               @NonNull final Duration timeToLive,
                                               boolean doBackgroundRefresh) {
        return new SimpleClient<OUT>() {

            Client<NoArgs, OUT> cachedClient = new CachedClient<NoArgs, OUT>(client, timeToLive, doBackgroundRefresh);

            @Override
            public OUT call (NoArgs noArgs) throws ConsumerInternalException, ConsumerDependencyException, ConsumerClientException {
                return cachedClient.call(noArgs);
            }
        };
    }


    @NonNull
    private final Client<INPUT, OUTPUT> client;
    @NonNull
    private final Cache<INPUT, OUTPUT> cache;
    @NonNull
    @Getter
    private final Duration timeToLive;

    protected CachedClient(@NonNull final Client<INPUT, OUTPUT> client,
                           @NonNull final Duration timeToLive,
                           final boolean doBackgroundRefresh) {
        this.client = client;
        this.timeToLive = timeToLive;
        if(doBackgroundRefresh) {
            this.cache = CacheBuilder.newBuilder()
                                     .expireAfterWrite(timeToLive.toMillis(), TimeUnit.MILLISECONDS)
                                     .build();
        } else {
            this.cache = CacheBuilder.newBuilder()
                                     .expireAfterWrite(timeToLive.toMillis(), TimeUnit.MILLISECONDS)
                                     .build(new CacheLoader<INPUT, OUTPUT>() {
                                         @Override
                                         public OUTPUT load (INPUT key) throws Exception {
                                             return this.load(key);
                                         }
                                     });
        }
    }

    @Override
    public OUTPUT call (INPUT input) {
        this.cache.cleanUp();
        OUTPUT output = this.cache.getIfPresent(input);
        if(output == null) {
            output = this.client.call(input);
            //We can only add values that are non-null
            if(output != null) {
                this.cache.put(input, output);
            }
        }
        return output;
    }
}
