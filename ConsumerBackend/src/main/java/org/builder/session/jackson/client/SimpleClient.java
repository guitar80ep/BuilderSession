package org.builder.session.jackson.client;

import org.builder.session.jackson.exception.ConsumerClientException;
import org.builder.session.jackson.exception.ConsumerDependencyException;
import org.builder.session.jackson.exception.ConsumerInternalException;
import org.builder.session.jackson.utils.NoArgs;

public interface SimpleClient<OUTPUT> extends Client<NoArgs, OUTPUT> {

    public default OUTPUT call () throws ConsumerInternalException,
                                         ConsumerDependencyException,
                                         ConsumerClientException {
        return this.call(NoArgs.INSTANCE);
    }
}
