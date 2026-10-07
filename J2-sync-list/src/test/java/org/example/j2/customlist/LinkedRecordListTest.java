package org.example.j2.customlist;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkedRecordListTest {

    @Test
    void addFirstKeepsMostRecentAtHead() {
        LinkedRecordList list = new LinkedRecordList();
        list.addFirst("first");
        list.addFirst("second");
        list.addFirst("third");

        assertEquals(List.of("third", "second", "first"), toList(list));
    }

    @Test
    void repeatedBubblePassesEventuallySortTheList() {
        LinkedRecordList list = new LinkedRecordList();
        for (String word : List.of("banana", "apple", "cherry", "date", "apple")) {
            list.addFirst(word);
        }

        for (int i = 0; i < 20; i++) {
            list.runBubblePass(0, 0);
        }

        List<String> sorted = toList(list);
        List<String> expected = new ArrayList<>(sorted);
        expected.sort(Comparator.naturalOrder());
        assertEquals(expected, sorted);
    }

    @Test
    void concurrentInsertsAndSortingNeverLoseOrDuplicateRecords() throws InterruptedException {
        LinkedRecordList list = new LinkedRecordList();
        int producers = 4;
        int perProducer = 50;
        CountDownLatch done = new CountDownLatch(producers);
        ExecutorService executor = Executors.newFixedThreadPool(producers);
        for (int p = 0; p < producers; p++) {
            int producerId = p;
            executor.submit(() -> {
                for (int i = 0; i < perProducer; i++) {
                    list.addFirst("p" + producerId + "-" + i);
                }
                done.countDown();
            });
        }

        Thread sorter = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                list.runBubblePass(0, 0);
            }
        });
        sorter.setDaemon(true);
        sorter.start();

        assertTrue(done.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        sorter.interrupt();
        sorter.join(2000);

        assertEquals(producers * perProducer, toList(list).size());
    }

    private static List<String> toList(LinkedRecordList list) {
        List<String> result = new ArrayList<>();
        for (String value : list) {
            result.add(value);
        }
        return result;
    }
}
