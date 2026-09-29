package org.example.j1.common;

import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public final class PemUtil {

    private PemUtil() {
    }

    public static String toPem(Object pemObject) {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(sw)) {
            writer.writeObject(pemObject);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to PEM-encode " + pemObject.getClass(), e);
        }
        return sw.toString();
    }

    public static PrivateKey readPrivateKey(Reader reader) throws IOException {
        BouncyCastleInit.register();
        try (PEMParser parser = new PEMParser(reader)) {
            Object parsed = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            if (parsed instanceof PEMKeyPair pemKeyPair) {
                return converter.getKeyPair(pemKeyPair).getPrivate();
            }
            if (parsed instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo privateKeyInfo) {
                return converter.getPrivateKey(privateKeyInfo);
            }
            if (parsed instanceof PKCS8EncryptedPrivateKeyInfo) {
                throw new IOException("Encrypted CA private keys are not supported; generate an unencrypted one with GenerateCaKey");
            }
            throw new IOException("Unrecognized PEM content for a private key: " + (parsed == null ? "null" : parsed.getClass()));
        }
    }
}
