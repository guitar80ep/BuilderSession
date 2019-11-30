package org.builder.session.jackson.utils;

import java.util.Optional;
import java.util.function.Function;

import com.google.common.base.Preconditions;

import lombok.NonNull;

public class CommandLineArguments {


    public static <T> Optional<T> parseArg(final @NonNull String[] args,
                                              final boolean required,
                                              final @NonNull String parameterName,
                                              final @NonNull Function<String, T> func) {
        Preconditions.checkArgument(args.length % 2 == 0, "Expected an even number of arguments, but got " + args);
        for(int i = 0; i < args.length; i += 2) {
            String currentParamName = args[i];
            String currentParamValue = args[i+1];
            if(parameterName.equals(currentParamName)) {
                return Optional.of(func.apply(currentParamValue));
            }
        }

        if(required) {
            throw new IllegalArgumentException("Could not find appropriate parameter " + parameterName);
        } else {
            return Optional.empty();
        }
    }

}
