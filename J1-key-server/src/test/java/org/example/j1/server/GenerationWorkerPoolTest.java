package org.example.j1.server;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationWorkerPoolTest {

    @Test
    void sameNameIsGeneratedOnlyOnce() throws Exception {
        IssuedCredential fixture = dummyCredential();
        AtomicInteger calls = new AtomicInteger();
        CredentialIssuer issuer = name -> {
            calls.incrementAndGet();
            return fixture;
        };

        try (GenerationWorkerPool pool = new GenerationWorkerPool(2, issuer)) {
            CompletableFuture<IssuedCredential> first = pool.resolve("alice");
            CompletableFuture<IssuedCredential> second = pool.resolve("alice");

            assertSame(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        }
    }

    @Test
    void concurrentRequestsForSameNameStillGenerateExactlyOnce() throws Exception {
        IssuedCredential fixture = dummyCredential();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch releaseIssuer = new CountDownLatch(1);
        CredentialIssuer issuer = name -> {
            calls.incrementAndGet();
            assertTrue(releaseIssuer.await(2, TimeUnit.SECONDS), "test issuer was never released");
            return fixture;
        };

        int callerCount = 20;
        List<CompletableFuture<IssuedCredential>> futures = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch allSubmitted = new CountDownLatch(callerCount);
        ExecutorService clientThreads = Executors.newFixedThreadPool(callerCount);
        try (GenerationWorkerPool pool = new GenerationWorkerPool(4, issuer)) {
            for (int i = 0; i < callerCount; i++) {
                clientThreads.submit(() -> {
                    futures.add(pool.resolve("bob"));
                    allSubmitted.countDown();
                });
            }
            assertTrue(allSubmitted.await(2, TimeUnit.SECONDS), "not all callers reached resolve() in time");

            releaseIssuer.countDown();

            for (CompletableFuture<IssuedCredential> future : futures) {
                assertSame(fixture, future.get(5, TimeUnit.SECONDS));
            }
            assertEquals(1, calls.get(), "generation must run exactly once for one name, no matter how many concurrent callers");
        } finally {
            clientThreads.shutdown();
        }
    }

    private static IssuedCredential dummyCredential() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        KeyPair keyPair = generator.generateKeyPair();
        CertificateAuthority ca = new CertificateAuthority(keyPair.getPrivate(), "Dummy CA", 1024);
        return ca.issue("dummy");
    }
}
