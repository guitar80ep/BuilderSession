package org.builder.session.jackson.client;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

import org.builder.session.jackson.client.messages.ContainerStats;
import org.builder.session.jackson.client.messages.TaskMetadata;
import org.builder.session.jackson.client.messages.TaskStats;
import org.builder.session.jackson.serialize.ZonedDateTimeSerializer;

import com.google.gson.Gson;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A client meant to read JSON values from the TaskMetadataEndpoint as described here:
 * https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-metadata-endpoint-v3.html
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskMetadataClient<T> implements Client<Void, T> {

    private static final String BASE_ENDPOINT = System.getenv("ECS_CONTAINER_METADATA_URI");
    private static final DateTimeFormatter FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .appendZoneId()
            .toFormatter();
    private static final Gson SERIALIZER = new Gson().newBuilder()
                                                     .enableComplexMapKeySerialization()
                                                     // Set date format for example: 2015-01-08T22:57:31.547920715Z
                                                     // GSON doesn't have nanosecond precision be default, so we add it.
                                                     .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeSerializer(FORMAT))
                                                     .create();

    public static Client<Void, TaskMetadata> createTaskMetadataClient(Duration cacheTime) {
        return new TaskMetadataClient<TaskMetadata>(BASE_ENDPOINT + "/task",
                                                    TaskMetadata.class,
                                                    cacheTime);
    }

    public static Client<Void, TaskStats> createTaskStatsClient(Duration cacheTime) {
        return new TaskMetadataClient<TaskStats>(BASE_ENDPOINT + "/task/stats",
                                                 TaskStats.class,
                                                 cacheTime);
    }

    public static Client<Void, ContainerStats> createContainerStatsClient(Duration cacheTime) {
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
            this.client = new CachedClient<>(new JsonHttpClient<T>(SERIALIZER, clazz), cacheTime);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("The endpoint \"" + endpoint + "\" was invalid.");
        }

    }

    @Override
    public T call (Void onlyPassNull) {
        return this.client.call(endpoint);
    }
}
