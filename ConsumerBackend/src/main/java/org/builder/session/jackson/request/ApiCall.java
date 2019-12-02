package org.builder.session.jackson.request;

@FunctionalInterface
public interface ApiCall<R, T> {
    public T call(R request, String requestId) throws Throwable;
}
