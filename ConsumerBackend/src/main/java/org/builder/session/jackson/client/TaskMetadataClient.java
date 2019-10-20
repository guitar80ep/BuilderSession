package org.builder.session.jackson.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.time.format.DateTimeFormatter;

import org.builder.session.jackson.client.messages.ContainerStats;
import org.builder.session.jackson.client.messages.TaskMetadata;
import org.builder.session.jackson.client.messages.TaskStats;
import org.builder.session.jackson.exception.ConsumerDependencyException;
import org.builder.session.jackson.exception.ConsumerInternalException;
import org.builder.session.jackson.serialize.ZonedDateTimeSerializer;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import lombok.NonNull;

/**
 * A client meant to read JSON values from the TaskMetadataEndpoint as described here:
 * https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-metadata-endpoint-v3.html
 */
public class TaskMetadataClient {

    private static final String ENV_VAR = "ECS_CONTAINER_METADATA_URI";

    @NonNull
    private final URL metadataEndpoint;
    private final URL taskStatsEndpoint;
    private final URL containerStatsEndpoint;
    private final Gson gson;

    public TaskMetadataClient () {
        try {
            String baseEndpoint = System.getenv(ENV_VAR);
            metadataEndpoint = new URL(baseEndpoint + "/task");
            taskStatsEndpoint = new URL(baseEndpoint + "/task/stats");
            containerStatsEndpoint = new URL(baseEndpoint + "/stats");
            gson = new Gson().newBuilder()
                             .enableComplexMapKeySerialization()
                             // Set date format for example: 2015-01-08T22:57:31.547920715Z
                             // GSON doesn't have nanosecond precision be default, so we add it.
                             .registerTypeAdapter(ZonedDateTimeSerializer.class, new ZonedDateTimeSerializer(DateTimeFormatter.ISO_DATE))
                             .create();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("The endpoint \"" + System.getenv(ENV_VAR) + "\" was invalid.");
        }
    }

    public TaskMetadata getMetadata () throws ConsumerInternalException, ConsumerDependencyException {
        return getFromUrl(metadataEndpoint, TaskMetadata.class);
    }

    public TaskStats getTaskStats () throws ConsumerInternalException, ConsumerDependencyException {
        return getFromUrl(taskStatsEndpoint, TaskStats.class);
    }

    public ContainerStats getContainerStats () throws ConsumerInternalException, ConsumerDependencyException {
        return getFromUrl(containerStatsEndpoint, ContainerStats.class);
    }

    private final <T> T getFromUrl(URL url, Class<T> clazz) {
        String payload = getFromUrl(url);
        try {
            return gson.fromJson(payload, clazz);
        } catch (JsonSyntaxException e) {
            throw new ConsumerInternalException("Failed to properly parse JSON payload from TaskMetadata endpoint. Payload: " + payload, e);
        }
    }

    private final String getFromUrl (URL url) throws ConsumerInternalException, ConsumerDependencyException {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);

            StringBuilder stringBuilder = new StringBuilder();
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF8"))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line);
                }
            }

            return stringBuilder.toString();
        } catch (ProtocolException e) {
            throw new ConsumerInternalException("Unexpected protocol failure while connecting to TaskMetadata endpoint.", e);
        } catch (IOException e) {
            throw new ConsumerDependencyException("Failed to GET from TaskMetadata endpoint.", e);
        }
    }
}
