package org.example.j2.common;

import java.util.ArrayList;
import java.util.List;

public final class InputSplitter {

    public static final int MAX_LINE_LENGTH = 80;

    private InputSplitter() {
    }

    // Режет строку на куски по maxLength символов; короткая строка — один кусок,
    // пустая строка — ноль кусков.
    public static List<String> split(String line, int maxLength) {
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < line.length(); start += maxLength) {
            chunks.add(line.substring(start, Math.min(start + maxLength, line.length())));
        }
        return chunks;
    }
}
