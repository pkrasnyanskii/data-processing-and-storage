package org.example.j1.client;

import org.example.j1.common.Protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class KeyClientMain {

    private KeyClientMain() {
    }

    public static void main(String[] args) {
        ClientConfig config;
        try {
            config = parseArgs(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            printUsage();
            System.exit(1);
            return;
        }

        try {
            run(config);
        } catch (IOException e) {
            System.err.println("Connection failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(ClientConfig config) throws IOException {
        System.out.println("Connecting to " + config.getHost() + ":" + config.getPort()
                + " as '" + config.getName() + "'...");

        try (Socket socket = new Socket(config.getHost(), config.getPort())) {
            OutputStream out = socket.getOutputStream();
            out.write(Protocol.encodeRequest(config.getName()));
            out.flush();
            System.out.println("Request sent.");

            if (config.isCrash()) {
                System.out.println("--crash: closing the connection without reading the response (simulated client crash).");
                return;
            }

            if (config.getDelaySeconds() > 0) {
                System.out.println("--delay " + config.getDelaySeconds()
                        + "s: sleeping before reading the response (simulated slow client)...");
                sleepSeconds(config.getDelaySeconds());
            }

            Protocol.Response response = Protocol.readResponse(socket.getInputStream());
            if (!response.isOk()) {
                System.err.println("Server returned an error: " + response.getErrorMessage());
                System.exit(1);
                return;
            }

            saveFiles(config, response);
        }
    }

    private static void saveFiles(ClientConfig config, Protocol.Response response) throws IOException {
        Files.createDirectories(config.getOutputDir());
        Path keyFile = config.getOutputDir().resolve(config.getName() + ".key");
        Path crtFile = config.getOutputDir().resolve(config.getName() + ".crt");
        Files.writeString(keyFile, response.getKeyPem());
        Files.writeString(crtFile, response.getCertPem());
        System.out.println("Saved " + keyFile.toAbsolutePath());
        System.out.println("Saved " + crtFile.toAbsolutePath());
    }

    private static void sleepSeconds(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final Set<String> BOOLEAN_FLAGS = new HashSet<>(Set.of("crash"));

    private static ClientConfig parseArgs(String[] args) {
        Map<String, String> flags = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + arg);
            }
            String key = arg.substring(2);
            if (BOOLEAN_FLAGS.contains(key)) {
                flags.put(key, "true");
                continue;
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for --" + key);
            }
            flags.put(key, args[++i]);
        }

        String name = requireFlag(flags, "name");
        String host = requireFlag(flags, "host");
        int port = parsePositiveInt("port", requireFlag(flags, "port"));
        int delay = flags.containsKey("delay") ? parsePositiveInt("delay", flags.get("delay")) : 0;
        boolean crash = flags.containsKey("crash");
        Path outDir = Path.of(flags.getOrDefault("out", "."));

        return new ClientConfig(name, host, port, delay, crash, outDir);
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
                Usage: KeyClientMain --name <name> --host <host> --port <port> [--delay <seconds>] [--crash] [--out <dir>]

                  --name    subject name to request a key/certificate for (required)
                  --host    server address or DNS name (required)
                  --port    server TCP port (required)
                  --delay   sleep this many seconds after sending the request, before reading the response
                  --crash   send the request, then disconnect without reading the response at all
                  --out     directory to write <name>.key / <name>.crt into (default: current directory)
                """);
    }
}
