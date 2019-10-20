package org.builder.session.jackson.utils;

/**
 * Much like Void, but doesn't require null values.
 */
public class NoArgs {

    public static final NoArgs INSTANCE = new NoArgs();

    @Override
    public boolean equals (Object obj) {
        return true;
    }

    @Override
    public int hashCode () {
        return 0;
    }
}
