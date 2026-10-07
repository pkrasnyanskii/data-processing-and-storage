package org.example.j2.app;

import org.example.j2.common.InputSplitter;
import org.example.j2.common.RecordStore;
import org.example.j2.customlist.LinkedRecordList;
import org.example.j2.synclist.SynchronizedListStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class Main {

    private static final int DEFAULT_SEED_SIZE = 20;

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        Config config;
        try {
            config = parseArgs(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            printUsage();
            System.exit(1);
            return;
        }

        RecordStore store = config.getImpl() == Impl.CUSTOM
                ? new LinkedRecordList()
                : new SynchronizedListStore();

        if (config.isBenchmark()) {
            runBenchmark(config, store);
        } else {
            runInteractive(store, config);
        }
    }

    private static void runBenchmark(Config config, RecordStore store) throws InterruptedException {
        Random random = new Random(42); // фиксированный seed — чтобы прогон был воспроизводим
        for (int i = 0; i < config.getSeedSize(); i++) {
            store.addFirst(randomWord(random));
        }

        AtomicLong totalSteps = new AtomicLong();
        List<Thread> sorters = startSorters(config, store, totalSteps);

        System.out.println("Бенчмарк: impl=" + config.getImpl()
                + ", threads=" + config.getThreads()
                + ", withinDelay=" + config.getWithinDelayMs() + "ms"
                + ", betweenDelay=" + config.getBetweenDelayMs() + "ms"
                + ", seedSize=" + config.getSeedSize()
                + ", duration=" + config.getDurationSeconds() + "s");

        long startNanos = System.nanoTime();
        Thread.sleep(TimeUnit.SECONDS.toMillis(config.getDurationSeconds()));
        long elapsedNanos = System.nanoTime() - startNanos;

        stopSorters(sorters);

        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        long steps = totalSteps.get();
        System.out.printf("Итого шагов: %d за %.2fs (%.1f шагов/с)%n", steps, elapsedSeconds, steps / elapsedSeconds);
    }

    private static void runInteractive(RecordStore store, Config config) throws IOException, InterruptedException {
        AtomicLong totalSteps = new AtomicLong();
        List<Thread> sorters = startSorters(config, store, totalSteps);

        System.out.println("Вводи строки (Enter на пустой строке — печать списка, Ctrl+D — выход).");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isEmpty()) {
                store.print(System.out);
                continue;
            }
            List<String> chunks = InputSplitter.split(line, InputSplitter.MAX_LINE_LENGTH);
            for (int i = chunks.size() - 1; i >= 0; i--) {
                store.addFirst(chunks.get(i));
            }
        }

        stopSorters(sorters);
        System.out.println("Всего шагов сортировки за время работы: " + totalSteps.get());
    }

    private static List<Thread> startSorters(Config config, RecordStore store, AtomicLong totalSteps) {
        List<Thread> sorters = new ArrayList<>();
        for (int i = 0; i < config.getThreads(); i++) {
            Thread t = new Thread(store.newSorter(config.getWithinDelayMs(), config.getBetweenDelayMs(), totalSteps));
            t.setName("sorter-" + i);
            t.setDaemon(true);
            sorters.add(t);
            t.start();
        }
        return sorters;
    }

    private static void stopSorters(List<Thread> sorters) throws InterruptedException {
        for (Thread t : sorters) {
            t.interrupt();
        }
        for (Thread t : sorters) {
            t.join(2000);
        }
    }

    private static String randomWord(Random random) {
        int length = 3 + random.nextInt(10);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('a' + random.nextInt(26)));
        }
        return sb.toString();
    }

    private static Config parseArgs(String[] args) {
        Map<String, String> flags = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + arg);
            }
            String key = arg.substring(2);
            if (key.equals("benchmark")) {
                flags.put(key, "true");
                continue;
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for --" + key);
            }
            flags.put(key, args[++i]);
        }

        Impl impl = Impl.valueOf(requireFlag(flags, "impl").toUpperCase());
        int threads = parsePositiveInt("threads", requireFlag(flags, "threads"));
        long withinDelay = parseNonNegativeLong("within-delay", requireFlag(flags, "within-delay"));
        long betweenDelay = parseNonNegativeLong("between-delay", requireFlag(flags, "between-delay"));
        boolean benchmark = flags.containsKey("benchmark");
        int durationSeconds = benchmark ? parsePositiveInt("duration-seconds", requireFlag(flags, "duration-seconds")) : 0;
        int seedSize = benchmark && flags.containsKey("seed-size")
                ? parsePositiveInt("seed-size", flags.get("seed-size"))
                : DEFAULT_SEED_SIZE;

        return new Config(impl, threads, withinDelay, betweenDelay, benchmark, durationSeconds, seedSize);
    }

    private static String requireFlag(Map<String, String> flags, String name) {
        String value = flags.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("--" + name + " is required");
        }
        return value;
    }

    private static int parsePositiveInt(String name, String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--" + name + " must be a positive integer, was: " + value);
        }
    }

    private static long parseNonNegativeLong(String name, String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--" + name + " must be a non-negative integer, was: " + value);
        }
    }

    private static void printUsage() {
        System.err.println("""
                Usage: Main --impl custom|sync --threads <n> --within-delay <ms> --between-delay <ms> [--benchmark --duration-seconds <s> [--seed-size <n>]]

                  --impl             custom (свой связный список) или sync (Collections.synchronizedList)
                  --threads          число дочерних сортирующих нитей
                  --within-delay     задержка внутри шага сортировки, в мс
                  --between-delay    задержка между шагами сортировки, в мс
                  --benchmark        headless-режим измерения вместо интерактивного ввода
                  --duration-seconds длительность бенчмарка (обязателен с --benchmark)
                  --seed-size        сколько случайных строк засеять перед бенчмарком (по умолчанию 20)
                """);
    }
}
