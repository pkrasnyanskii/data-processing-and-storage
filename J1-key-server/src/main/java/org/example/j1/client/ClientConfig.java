package org.example.j1.client;

import lombok.Value;

import java.nio.file.Path;

@Value
public class ClientConfig {
    String name;
    String host;
    int port;
    int delaySeconds;
    boolean crash;
    Path outputDir;
}
