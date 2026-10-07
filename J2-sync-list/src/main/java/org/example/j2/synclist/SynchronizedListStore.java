package org.example.j2.synclist;

import org.example.j2.common.RecordStore;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

// Обёртка над Collections.synchronizedList. Каждый отдельный вызов get/add/set уже сам по
// себе потокобезопасен, но составную операцию (сравнить и, может быть, обменять два
// элемента) приходится защищать собственным synchronized(backing) — так и советует
// документация этого класса для любой составной работы со списком.
public final class SynchronizedListStore implements RecordStore {

    private final List<String> backing = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void addFirst(String value) {
        backing.add(0, value);
    }

    @Override
    public void print(PrintStream out) {
        synchronized (backing) {
            for (String value : backing) {
                out.println(value);
            }
        }
    }

    @Override
    public Runnable newSorter(long withinDelayMs, long betweenDelayMs, AtomicLong totalSteps) {
        return new SynchronizedListSorter(backing, withinDelayMs, betweenDelayMs, totalSteps);
    }
}
