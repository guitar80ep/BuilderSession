package org.builder.session.jackson.client;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import lombok.Getter;
import lombok.NonNull;

public class CachedClient<INPUT, OUTPUT> implements Client<INPUT, OUTPUT> {

    @NonNull
    private final Client<INPUT, OUTPUT> client;
    @NonNull
    private final Cache<INPUT, OUTPUT> cache;
    @NonNull
    @Getter
    private final Duration timeToLive;


    public CachedClient(@NonNull final Client<INPUT, OUTPUT> client,
                        @NonNull final Duration timeToLive) {
        this.client = client;
        this.timeToLive = timeToLive;
        this.cache = CacheBuilder.newBuilder()
                                 .expireAfterWrite(timeToLive.toMillis(), TimeUnit.MILLISECONDS)
                                 .build();
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
