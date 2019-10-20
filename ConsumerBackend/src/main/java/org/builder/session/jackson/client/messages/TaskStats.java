package org.builder.session.jackson.client.messages;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@JsonAdapter(TaskStats.Serializer.class)
public class TaskStats {
    private final Map<String, ContainerStats> containers;

    @RequiredArgsConstructor
    public static class Serializer extends TypeAdapter<TaskStats> {

        @NonNull
        private final TypeAdapter<ContainerStats> adapter;

        public Serializer() {
            adapter = new Gson().newBuilder()
                                .enableComplexMapKeySerialization()
                                .create()
                                .getAdapter(ContainerStats.class);
        }

        @Override
        public TaskStats read (JsonReader in) throws IOException {
            if(in.peek().equals(JsonToken.NULL)) {
                return new TaskStats(ImmutableMap.of());
            } else {
                in.beginObject();
                Map<String, ContainerStats> containers = new HashMap<>();
                while (in.hasNext()) {
                    String name = in.nextName();
                    ContainerStats stats = in.peek().equals(JsonToken.NULL) ?
                                           null : adapter.read(in);
                    containers.put(name, stats);
                }
                in.endObject();
                return TaskStats.builder()
                                .containers(Collections.unmodifiableMap(containers))
                                .build();
            }
        }

        @Override
        public void write (JsonWriter out, TaskStats value) throws IOException {
            if(value == null || value.getContainers() == null) {
                out.nullValue();
            } else {
                out.beginObject();
                for(Map.Entry<String, ContainerStats> e : value.getContainers().entrySet()) {
                    out.name(e.getKey());
                    adapter.write(out, e.getValue());
                }
                out.endObject();
            }
        }
    }
}
