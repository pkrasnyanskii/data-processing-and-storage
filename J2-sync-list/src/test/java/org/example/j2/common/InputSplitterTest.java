package org.example.j2.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InputSplitterTest {

    @Test
    void shortLineIsNotSplit() {
        assertEquals(List.of("hello"), InputSplitter.split("hello", 80));
    }

    @Test
    void emptyLineProducesNoChunks() {
        assertEquals(List.of(), InputSplitter.split("", 80));
    }

    @Test
    void exactMultipleSplitsEvenly() {
        String line = "a".repeat(10);
        assertEquals(List.of("aaaaa", "aaaaa"), InputSplitter.split(line, 5));
    }

    @Test
    void remainderGoesIntoShorterLastChunk() {
        String line = "a".repeat(12);
        assertEquals(List.of("aaaaa", "aaaaa", "aa"), InputSplitter.split(line, 5));
    }
}
