package org.builder.session.jackson.exception;

/**
 * An exception for when the client provides faulty parameters or other common error.
 */
public class ConsumerClientException extends RuntimeException {

    public ConsumerClientException (String message, Throwable t) {
        super(message, t);
    }
}
