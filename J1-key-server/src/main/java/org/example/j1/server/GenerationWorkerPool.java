package org.example.j1.server;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class GenerationWorkerPool implements Closeable {

    // В кэше лежит FUTURE, а не готовый результат — поэтому повторный запрос того же имени,
    // пока генерация ещё не закончена, просто подвисает на тот же future, а не запускает вторую генерацию.
    private final ConcurrentHashMap<String, CompletableFuture<IssuedCredential>> cache = new ConcurrentHashMap<>();
    private final ExecutorService generatorPool;
    private final CredentialIssuer issuer;

    public GenerationWorkerPool(int threadCount, CredentialIssuer issuer) {
        if (threadCount < 1) {
            throw new IllegalArgumentException("threadCount must be >= 1, was " + threadCount);
        }
        this.issuer = issuer;
        this.generatorPool = Executors.newFixedThreadPool(threadCount, GenerationWorkerPool::newGeneratorThread);
    }

    // computeIfAbsent атомарный: сколько нитей ни дёрни resolve(name) одновременно,
    // startGeneration вызовется максимум один раз на имя, остальные получат тот же CompletableFuture.
    public CompletableFuture<IssuedCredential> resolve(String name) {
        return cache.computeIfAbsent(name, this::startGeneration);
    }

    private CompletableFuture<IssuedCredential> startGeneration(String name) {
        CompletableFuture<IssuedCredential> future = new CompletableFuture<>();
        generatorPool.execute(() -> { // выполнится в одной из нитей пула, а не в той, что вызвала resolve
            try {
                future.complete(issuer.issue(name));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public int pendingOrCachedNames() {
        return cache.size();
    }

    @Override
    public void close() {
        generatorPool.shutdown();
        try {
            if (!generatorPool.awaitTermination(10, TimeUnit.SECONDS)) {
                generatorPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            generatorPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static Thread newGeneratorThread(Runnable r) {
        Thread t = new Thread(r);
        t.setName("key-generator-" + t.threadId());
        t.setDaemon(true);
        return t;
    }
}
