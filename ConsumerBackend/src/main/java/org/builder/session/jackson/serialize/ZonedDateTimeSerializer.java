package org.builder.session.jackson.serialize;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ZonedDateTimeSerializer extends TypeAdapter<ZonedDateTime> {

    @NonNull
    private final DateTimeFormatter formatter;

    @Override
    public void write (JsonWriter out, ZonedDateTime value) throws IOException {
        out.value(formatter.format(value));
    }

    @Override
    public ZonedDateTime read (JsonReader in) throws IOException {
        return formatter.parse(in.nextString(), ZonedDateTime::from);
    }
}
