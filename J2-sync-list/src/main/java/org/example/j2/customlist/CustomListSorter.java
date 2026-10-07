package org.example.j2.customlist;

import java.util.concurrent.atomic.AtomicLong;

final class CustomListSorter implements Runnable {

    private final LinkedRecordList list;
    private final long withinDelayMs;
    private final long betweenDelayMs;
    private final AtomicLong totalSteps;

    CustomListSorter(LinkedRecordList list, long withinDelayMs, long betweenDelayMs, AtomicLong totalSteps) {
        this.list = list;
        this.withinDelayMs = withinDelayMs;
        this.betweenDelayMs = betweenDelayMs;
        this.totalSteps = totalSteps;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            totalSteps.addAndGet(list.runBubblePass(withinDelayMs, betweenDelayMs));
        }
    }
}
