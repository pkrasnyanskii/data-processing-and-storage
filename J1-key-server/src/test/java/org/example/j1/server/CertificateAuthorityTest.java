package org.example.j1.server;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CertificateAuthorityTest {

    @Test
    void issuedCertificateHasExpectedSubjectIssuerAndIsSignedByTheCa() throws Exception {
        KeyPair caKeyPair = smallRsaKeyPair();
        CertificateAuthority ca = new CertificateAuthority(caKeyPair.getPrivate(), "Test Course CA", 1024);

        IssuedCredential credential = ca.issue("petr");

        assertTrue(credential.getCertificate().getSubjectX500Principal().getName().contains("CN=petr"));
        assertTrue(credential.getCertificate().getIssuerX500Principal().getName().contains("CN=Test Course CA"));
        assertInstanceOf(RSAPrivateKey.class, credential.getPrivateKey());
        assertDoesNotThrow(() -> credential.getCertificate().verify(caKeyPair.getPublic()));
    }

    @Test
    void twoIssuedCredentialsForDifferentNamesAreIndependent() throws Exception {
        KeyPair caKeyPair = smallRsaKeyPair();
        CertificateAuthority ca = new CertificateAuthority(caKeyPair.getPrivate(), "Test Course CA", 1024);

        IssuedCredential alice = ca.issue("alice");
        IssuedCredential bob = ca.issue("bob");

        assertNotEquals(alice.getCertificate().getSerialNumber(), bob.getCertificate().getSerialNumber());
        assertNotEquals(alice.getPrivateKey(), bob.getPrivateKey());
    }

    private static KeyPair smallRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        return generator.generateKeyPair();
    }
}
