package org.builder.session.jackson.client;

import org.builder.session.jackson.exception.ConsumerClientException;
import org.builder.session.jackson.exception.ConsumerDependencyException;
import org.builder.session.jackson.exception.ConsumerInternalException;

public interface Client<INPUT, OUTPUT> {
    public OUTPUT call(INPUT input) throws ConsumerInternalException,
                                           ConsumerDependencyException,
                                           ConsumerClientException;
}
