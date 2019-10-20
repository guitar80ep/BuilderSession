package org.builder.session.jackson.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.time.Duration;

import org.builder.session.jackson.exception.ConsumerDependencyException;
import org.builder.session.jackson.exception.ConsumerInternalException;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A client meant to "GET" JSON values from an arbitrary HTTP endpoint
 * and deserialize them.
 */
@RequiredArgsConstructor
@AllArgsConstructor
@Slf4j
public class JsonHttpClient<T> implements Client<URL, T> {

    @NonNull
    private final Gson serializer;
    @NonNull
    private final Class<T> clazz;
    private Duration connectTimeout = Duration.ofSeconds(1);
    private Duration readTimeout = Duration.ofSeconds(1);

    public T call (URL url) {
        T result = getFromUrl(url, clazz);
        log.debug("Received payload from {}. Result: {}", url, result);
        return result;
    }

    private final <T> T getFromUrl(URL url, Class<T> clazz) {
        String payload = getFromUrl(url);
        try {
            log.debug("Received payload from {}. Deserializing: {}", url, payload);
            return this.serializer.fromJson(payload, clazz);
        } catch (JsonSyntaxException e) {
            throw new ConsumerInternalException("Failed to properly parse JSON payload from TaskMetadata endpoint. Payload: " + payload, e);
        }
    }

    private final String getFromUrl (URL url) throws ConsumerInternalException, ConsumerDependencyException {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout((int)connectTimeout.toMillis());
            connection.setReadTimeout((int)readTimeout.toMillis());

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
