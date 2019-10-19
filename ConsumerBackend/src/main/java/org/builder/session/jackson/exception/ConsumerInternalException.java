package org.builder.session.jackson.exception;

/**
 * An exception for when the internal logic of the server fails.
 */
public class ConsumerInternalException extends RuntimeException {

    public ConsumerInternalException (String message, Throwable t) {
        super(message, t);
    }
}
