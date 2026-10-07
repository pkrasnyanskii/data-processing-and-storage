package org.example.j2.common;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicLong;

// Общий контракт для обеих реализаций списка (свой связный список и Collections.synchronizedList),
// чтобы Main не знал, с какой из них работает.
public interface RecordStore {

    void addFirst(String value);

    void print(PrintStream out);

    Runnable newSorter(long withinDelayMs, long betweenDelayMs, AtomicLong totalSteps);
}
