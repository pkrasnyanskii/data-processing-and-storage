package org.example.j1.server;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class KeyServerMain {

    private static final int DEFAULT_PORT = 9090;
    private static final int DEFAULT_SUBJECT_KEY_SIZE_BITS = 8192;

    private KeyServerMain() {
    }

    public static void main(String[] args) throws IOException {
        ServerConfig config;
        try {
            config = parseArgs(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            printUsage();
            System.exit(1);
            return;
        }

        System.out.println("Loading CA key from " + config.getCaKeyFile().toAbsolutePath());
        CertificateAuthority ca;
        try (Reader keyReader = Files.newBufferedReader(config.getCaKeyFile())) {
            ca = CertificateAuthority.fromPemFile(keyReader, config.getIssuerName(), config.getKeySizeBits());
        }

        try (NioKeyServer server = new NioKeyServer(config, ca)) {
            Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown, "key-server-shutdown"));
            System.out.println("Issuer name (fixed): " + config.getIssuerName());
            System.out.println("Generator threads:   " + config.getGeneratorThreads());
            System.out.println("Subject key size:    " + config.getKeySizeBits() + " bits");
            server.run();
        }
        System.out.println("Server stopped.");
    }

    private static ServerConfig parseArgs(String[] args) {
        Map<String, String> flags = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + arg);
            }
            String key = arg.substring(2);
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for --" + key);
            }
            flags.put(key, args[++i]);
        }

        String threadsStr = requireFlag(flags, "threads");
        String caKeyStr = requireFlag(flags, "ca-key");
        String issuer = requireFlag(flags, "issuer");
        int port = flags.containsKey("port") ? parseIntFlag(flags, "port") : DEFAULT_PORT;
        int keySize = flags.containsKey("key-size") ? parseIntFlag(flags, "key-size") : DEFAULT_SUBJECT_KEY_SIZE_BITS;
        int threads = parsePositiveInt("threads", threadsStr);

        Path caKeyFile = Path.of(caKeyStr);
        if (!Files.isReadable(caKeyFile)) {
            throw new IllegalArgumentException("Cannot read CA key file: " + caKeyFile.toAbsolutePath());
        }

        return new ServerConfig(port, threads, caKeyFile, issuer, keySize);
    }

    private static String requireFlag(Map<String, String> flags, String name) {
        String value = flags.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("--" + name + " is required");
        }
        return value;
    }

    private static int parseIntFlag(Map<String, String> flags, String name) {
        return parsePositiveInt(name, flags.get(name));
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

    private static void printUsage() {
        System.err.println("""
                Usage: KeyServerMain --threads <n> --ca-key <path> --issuer <name> [--port <port>] [--key-size <bits>]

                  --threads   number of key-generation worker threads (required)
                  --ca-key    path to the CA's PEM private key file, see GenerateCaKey (required)
                  --issuer    fixed Issuer Name put into every issued certificate (required)
                  --port      TCP port to listen on (default: %d)
                  --key-size  RSA key size in bits for issued (subject) keys (default: %d)
                """.formatted(DEFAULT_PORT, DEFAULT_SUBJECT_KEY_SIZE_BITS));
    }
}
