package org.builder.session.jackson.server;

import java.io.Closeable;
import java.io.IOException;

public interface Server extends Closeable {
    public void start() throws IOException;
}
