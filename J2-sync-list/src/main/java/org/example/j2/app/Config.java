package org.example.j2.app;

import lombok.Value;

@Value
public class Config {
    Impl impl;
    int threads;
    long withinDelayMs;
    long betweenDelayMs;
    boolean benchmark;
    int durationSeconds;
    int seedSize;
}
