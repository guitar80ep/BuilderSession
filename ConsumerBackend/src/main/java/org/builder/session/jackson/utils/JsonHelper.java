package org.builder.session.jackson.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@NoArgsConstructor(access = AccessLevel.NONE)
public class JsonHelper {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final JsonParser PARSER = new JsonParser();

    public static String format(@NonNull Message message) {
        try {
            return JsonFormat.printer()
                             .includingDefaultValueFields()
                             .print(message);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("Failed to parse message "
                                               + message
                                               + " to Json.",
                                       e);
        }
    }

    public static String toSingleLine(@NonNull Message message) {
        return message.toString()
                      .replace("\n", " ")
                      .replace("\r", " ")
                      .replace("\t", " ");
    }

    public static String format(@NonNull String jsonData) {
        JsonElement je = PARSER.parse(jsonData);
        return GSON.toJson(je);
    }
}
