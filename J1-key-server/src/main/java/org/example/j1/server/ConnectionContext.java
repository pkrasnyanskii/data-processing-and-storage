package org.example.j1.server;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

final class ConnectionContext {

    final SocketChannel channel;
    final SelectionKey key;

    private final ByteArrayOutputStream nameAccumulator = new ByteArrayOutputStream(64);
    private boolean nameComplete = false;

    ByteBuffer pendingWrite;

    ConnectionContext(SocketChannel channel, SelectionKey key) {
        this.channel = channel;
        this.key = key;
    }

    boolean isNameComplete() {
        return nameComplete;
    }

    String feedNameBytes(ByteBuffer readBuf) throws java.io.IOException {
        while (readBuf.hasRemaining()) {
            byte b = readBuf.get();
            if (b == 0) {
                nameComplete = true;
                return nameAccumulator.toString(java.nio.charset.StandardCharsets.US_ASCII);
            }
            if (nameAccumulator.size() >= org.example.j1.common.Protocol.MAX_NAME_BYTES) {
                throw new java.io.IOException("Client name exceeds " + org.example.j1.common.Protocol.MAX_NAME_BYTES + " bytes without a terminator");
            }
            nameAccumulator.write(b);
        }
        return null;
    }
}
