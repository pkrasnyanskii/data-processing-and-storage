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

// Реактор в одной нити: только она трогает Selector, сокеты и ConnectionContext.
// Генерирующие нити сюда не лезут напрямую — только через transmitQueue (потокобезопасная) + wakeup().
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

    // Блокируется здесь, пока shutdown() не выставит running = false и не разбудит selector.
    public void run() throws IOException {
        System.out.println("Key server listening on port " + localPort()
                + " with " + "generator pool ready");
        while (running) {
            selector.select(); // спит, пока какой-то канал не готов или кто-то не позвал wakeup()

            drainTransmitQueue(); // забираем ответы, которые генерирующие нити успели подготовить пока спали

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> it = selectedKeys.iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove(); // обязательно: select() сам это множество не чистит
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
                    // один упавший/сломанный клиент, остальных это не касается
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

    // Потокобезопасно: вызывается из shutdown hook, то есть из другой нити, не из run().
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

    // Читаем то, что пришло прямо сейчас; имя клиента может доехать по кускам за несколько вызовов.
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
            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ); // один запрос на соединение, больше читать не ждём
            handleNameReceived(ctx, name);
        }
    }

    // Отдаём имя в пул на генерацию. whenComplete ниже выполнится в генерирующей нити,
    // а не в реакторе — поэтому только кладём результат в очередь и будим selector.
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

    // Выполняется только в нити реактора — поэтому ключи/каналы тут трогать безопасно.
    private void drainTransmitQueue() {
        ResponseTask task;
        while ((task = transmitQueue.poll()) != null) {
            ConnectionContext ctx = task.getConnection();
            if (!ctx.key.isValid()) {
                continue; // клиент уже отвалился, пока мы генерировали ему ключ
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
        ctx.channel.write(buf); // неблокирующая запись, может уйти не весь буфер за раз
        if (!buf.hasRemaining()) {
            closeQuietly(key); // всё отправили, этому клиенту мы больше ничего не должны
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
