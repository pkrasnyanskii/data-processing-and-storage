package org.example.j1.server;

import lombok.Value;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

@Value
public class IssuedCredential {
    PrivateKey privateKey;
    X509Certificate certificate;
}
