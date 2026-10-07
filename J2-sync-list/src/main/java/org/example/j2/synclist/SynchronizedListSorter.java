package org.example.j2.synclist;

import org.example.j2.common.Sleep;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

final class SynchronizedListSorter implements Runnable {

    private final List<String> list;
    private final long withinDelayMs;
    private final long betweenDelayMs;
    private final AtomicLong totalSteps;

    SynchronizedListSorter(List<String> list, long withinDelayMs, long betweenDelayMs, AtomicLong totalSteps) {
        this.list = list;
        this.withinDelayMs = withinDelayMs;
        this.betweenDelayMs = betweenDelayMs;
        this.totalSteps = totalSteps;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            totalSteps.addAndGet(runBubblePass());
        }
    }

    // Один проход. Весь шаг (сравнение + возможный обмен, вместе с withinDelay) сидит под
    // одним synchronized(list) — на время шага список занят целиком, другим нитям (включая
    // родительскую вставку) только и остаётся ждать. Это и даёт разницу в throughput с
    // собственным списком, которую просит измерить задание.
    private long runBubblePass() {
        long steps = 0;
        int i = 0;
        while (true) {
            boolean passDone;
            synchronized (list) {
                if (i + 1 >= list.size()) {
                    passDone = true;
                } else {
                    passDone = false;
                    steps++;
                    if (Sleep.quietly(withinDelayMs)) {
                        return steps;
                    }
                    String left = list.get(i);
                    String right = list.get(i + 1);
                    if (left.compareTo(right) > 0) {
                        list.set(i, right);
                        list.set(i + 1, left);
                    }
                }
            }
            if (passDone || Thread.currentThread().isInterrupted()) {
                return steps;
            }
            if (Sleep.quietly(betweenDelayMs)) {
                return steps;
            }
            i++;
        }
    }
}
