package org.builder.session.jackson.client;

public interface Client<INPUT, OUTPUT> {
    public OUTPUT call(INPUT input);
}
