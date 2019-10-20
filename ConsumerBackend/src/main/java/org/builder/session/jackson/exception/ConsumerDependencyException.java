package org.builder.session.jackson.exception;

/**
 * An exception for when one of the dependencies of the server fails.
 */
public class ConsumerDependencyException extends RuntimeException {

    public ConsumerDependencyException (String message) {
        super(message);
    }

    public ConsumerDependencyException (String message, Throwable t) {
        super(message, t);
    }
}
