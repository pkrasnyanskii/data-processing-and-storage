package org.example.j2.synclist;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SynchronizedListSorterTest {

    @Test
    void repeatedPassesEventuallySortTheList() throws InterruptedException {
        List<String> list = Collections.synchronizedList(
                new ArrayList<>(List.of("banana", "apple", "cherry", "date", "apple")));

        Thread sorterThread = new Thread(new SynchronizedListSorter(list, 0, 0, new AtomicLong()));
        sorterThread.setDaemon(true);
        sorterThread.start();
        Thread.sleep(300);
        sorterThread.interrupt();
        sorterThread.join(2000);

        List<String> snapshot = new ArrayList<>(list);
        List<String> expected = new ArrayList<>(snapshot);
        expected.sort(Comparator.naturalOrder());
        assertEquals(expected, snapshot);
    }

    @Test
    void concurrentInsertsAndSortingNeverLoseOrDuplicateRecords() throws InterruptedException {
        List<String> list = Collections.synchronizedList(new ArrayList<>());
        int producers = 4;
        int perProducer = 50;
        CountDownLatch done = new CountDownLatch(producers);
        ExecutorService executor = Executors.newFixedThreadPool(producers);
        for (int p = 0; p < producers; p++) {
            int producerId = p;
            executor.submit(() -> {
                for (int i = 0; i < perProducer; i++) {
                    list.add(0, "p" + producerId + "-" + i);
                }
                done.countDown();
            });
        }

        Thread sorterThread = new Thread(new SynchronizedListSorter(list, 0, 0, new AtomicLong()));
        sorterThread.setDaemon(true);
        sorterThread.start();

        assertTrue(done.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        sorterThread.interrupt();
        sorterThread.join(2000);

        assertEquals(producers * perProducer, list.size());
    }
}
