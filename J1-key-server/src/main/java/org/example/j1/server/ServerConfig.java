package org.example.j1.server;

import lombok.Value;

import java.nio.file.Path;

@Value
public class ServerConfig {
    int port;
    int generatorThreads;
    Path caKeyFile;
    String issuerName;
    int keySizeBits;
}
