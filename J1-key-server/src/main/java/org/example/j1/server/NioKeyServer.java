package org.example.j1.server;

import org.example.j1.common.PemUtil;
import org.example.j1.common.Protocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;

public final class NioKeyServer implements AutoCloseable {

    private final ServerSocketChannel serverChannel;
    private final Selector selector;
    private final GenerationWorkerPool workerPool;
    private final ConcurrentLinkedQueue<ResponseTask> transmitQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean running = true;

    public NioKeyServer(ServerConfig config, CredentialIssuer issuer) throws IOException {
        this.selector = Selector.open();
        this.serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(config.getPort()));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        this.workerPool = new GenerationWorkerPool(config.getGeneratorThreads(), issuer);
    }

    public void run() throws IOException {
        System.out.println("Key server listening on port " + localPort()
                + " with " + "generator pool ready");
        while (running) {
            selector.select();

            drainTransmitQueue();

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> it = selectedKeys.iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();
                try {
                    if (!key.isValid()) {
                        continue;
                    }
                    if (key.isAcceptable()) {
                        handleAccept();
                    } else {
                        if (key.isValid() && key.isReadable()) {
                            handleRead(key);
                        }
                        if (key.isValid() && key.isWritable()) {
                            handleWrite(key);
                        }
                    }
                } catch (IOException e) {
                    closeQuietly(key);
                }
            }
        }
    }

    public int localPort() {
        try {
            return ((java.net.InetSocketAddress) serverChannel.getLocalAddress()).getPort();
        } catch (IOException e) {
            return -1;
        }
    }

    public void shutdown() {
        running = false;
        selector.wakeup();
    }

    private void handleAccept() throws IOException {
        SocketChannel client = serverChannel.accept();
        if (client == null) {
            return;
        }
        client.configureBlocking(false);
        SelectionKey clientKey = client.register(selector, SelectionKey.OP_READ);
        clientKey.attach(new ConnectionContext(client, clientKey));
    }

    private void handleRead(SelectionKey key) throws IOException {
        ConnectionContext ctx = (ConnectionContext) key.attachment();
        ByteBuffer readBuf = ByteBuffer.allocate(256);
        int n = ctx.channel.read(readBuf);
        if (n == -1) {
            closeQuietly(key);
            return;
        }
        if (n == 0) {
            return;
        }
        readBuf.flip();
        String name = ctx.feedNameBytes(readBuf);
        if (name != null) {
            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
            handleNameReceived(ctx, name);
        }
    }

    private void handleNameReceived(ConnectionContext ctx, String name) {
        CompletableFuture<IssuedCredential> future = workerPool.resolve(name);
        future.whenComplete((credential, error) -> {
            if (error != null) {
                transmitQueue.add(ResponseTask.error(ctx, describeError(error)));
            } else {
                transmitQueue.add(ResponseTask.success(ctx, credential));
            }
            selector.wakeup();
        });
    }

    private void drainTransmitQueue() {
        ResponseTask task;
        while ((task = transmitQueue.poll()) != null) {
            ConnectionContext ctx = task.getConnection();
            if (!ctx.key.isValid()) {
                continue;
            }
            byte[] payload = task.isSuccess()
                    ? Protocol.encodeSuccess(
                            PemUtil.toPem(task.getCredential().getCertificate()),
                            PemUtil.toPem(task.getCredential().getPrivateKey()))
                    : Protocol.encodeError(task.getErrorMessage());
            ctx.pendingWrite = ByteBuffer.wrap(payload);
            ctx.key.interestOps(ctx.key.interestOps() | SelectionKey.OP_WRITE);
        }
    }

    private void handleWrite(SelectionKey key) throws IOException {
        ConnectionContext ctx = (ConnectionContext) key.attachment();
        ByteBuffer buf = ctx.pendingWrite;
        if (buf == null) {
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
            return;
        }
        ctx.channel.write(buf);
        if (!buf.hasRemaining()) {
            closeQuietly(key);
        }
    }

    private void closeQuietly(SelectionKey key) {
        key.cancel();
        try {
            key.channel().close();
        } catch (IOException ignored) {
        }
    }

    private static String describeError(Throwable t) {
        Throwable cause = t.getCause() != null ? t.getCause() : t;
        return cause.getClass().getSimpleName() + (cause.getMessage() != null ? ": " + cause.getMessage() : "");
    }

    @Override
    public void close() throws IOException {
        shutdown();
        workerPool.close();
        selector.close();
        serverChannel.close();
    }
}
