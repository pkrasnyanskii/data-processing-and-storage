package org.example.j2.synclist;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SynchronizedListStoreTest {

    @Test
    void addFirstKeepsMostRecentAtHead() {
        SynchronizedListStore store = new SynchronizedListStore();
        store.addFirst("first");
        store.addFirst("second");
        store.addFirst("third");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        store.print(new PrintStream(out, true, StandardCharsets.UTF_8));

        assertEquals("third\nsecond\nfirst\n", out.toString(StandardCharsets.UTF_8));
    }
}
