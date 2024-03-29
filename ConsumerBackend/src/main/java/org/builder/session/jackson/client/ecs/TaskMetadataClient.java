package org.builder.session.jackson.client.ecs;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

import org.builder.session.jackson.client.Client;
import org.builder.session.jackson.client.SimpleClient;
import org.builder.session.jackson.client.general.JsonHttpClient;
import org.builder.session.jackson.client.messages.ContainerStats;
import org.builder.session.jackson.client.messages.MetadataConstants;
import org.builder.session.jackson.client.messages.TaskMetadata;
import org.builder.session.jackson.client.messages.TaskStats;
import org.builder.session.jackson.client.wrapper.CachedClient;
import org.builder.session.jackson.utils.NoArgs;

import com.google.gson.Gson;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A client meant to read JSON values from the TaskMetadataEndpoint as described here:
 * https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-metadata-endpoint-v3.html
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskMetadataClient<T> implements SimpleClient<T> {

    private static final String BASE_ENDPOINT = System.getenv("ECS_CONTAINER_METADATA_URI");
    private static final Gson SERIALIZER = MetadataConstants.createGson().create();

    public static SimpleClient<TaskMetadata> createTaskMetadataClient(Duration cacheTime) {
        return new TaskMetadataClient<TaskMetadata>(BASE_ENDPOINT + "/task",
                                                    TaskMetadata.class,
                                                    cacheTime);
    }

    public static SimpleClient<TaskStats> createTaskStatsClient(Duration cacheTime) {
        return new TaskMetadataClient<TaskStats>(BASE_ENDPOINT + "/task/stats",
                                                 TaskStats.class,
                                                 cacheTime);
    }

    public static SimpleClient<ContainerStats> createContainerStatsClient(Duration cacheTime) {
        return new TaskMetadataClient<ContainerStats>(BASE_ENDPOINT + "/stats",
                                                      ContainerStats.class,
                                                      cacheTime);
    }

    @NonNull
    private final URL endpoint;
    @NonNull
    private final Client<URL, T> client;

    public TaskMetadataClient (@NonNull final String endpoint,
                               @NonNull final Class<T> clazz,
                               @NonNull final Duration cacheTime) {
        try {
            this.endpoint = new URL(endpoint);
            this.client = CachedClient.wrap(new JsonHttpClient<T>(SERIALIZER, clazz),
                                            cacheTime,
                                            // We only want to load these values on-demand.
                                            false);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("The endpoint \"" + endpoint + "\" was invalid.");
        }

    }

    @Override
    public T call (NoArgs passAnything) {
        return this.client.call(endpoint);
    }
}
