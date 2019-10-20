package org.builder.session.jackson.client.messages;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

import org.builder.session.jackson.serialize.ZonedDateTimeSerializer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.NONE)
public class MetadataConstants {

    public static final DateTimeFormatter FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .appendZoneId()
            .toFormatter();

    public static GsonBuilder createGson() {
        return new Gson().newBuilder()
                         .enableComplexMapKeySerialization()
                         // Set date format for example: 2015-01-08T22:57:31.547920715Z
                         // GSON doesn't have nanosecond precision be default, so we add it.
                         .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeSerializer(FORMAT));
    }


}
