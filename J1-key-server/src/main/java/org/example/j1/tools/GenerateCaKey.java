package org.example.j1.tools;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.example.j1.common.BouncyCastleInit;
import org.example.j1.common.PemUtil;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

public final class GenerateCaKey {

    private static final int DEFAULT_CA_KEY_SIZE_BITS = 4096;
    private static final Duration CA_CERT_VALIDITY = Duration.ofDays(3650);

    private GenerateCaKey() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: GenerateCaKey <out-key.pem> <out-cert.pem> <issuer-common-name> [key-size-bits]");
            System.exit(1);
            return;
        }

        Path keyOut = Path.of(args[0]);
        Path certOut = Path.of(args[1]);
        String issuerName = args[2];
        int keySize = args.length > 3 ? Integer.parseInt(args[3]) : DEFAULT_CA_KEY_SIZE_BITS;

        BouncyCastleInit.register();

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(keySize);
        KeyPair caKeyPair = generator.generateKeyPair();

        X500Name subject = new X500Name("CN=" + issuerName);
        BigInteger serial = new BigInteger(128, new SecureRandom()).abs();
        Instant now = Instant.now();
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                subject, serial, Date.from(now), Date.from(now.plus(CA_CERT_VALIDITY)), subject, caKeyPair.getPublic());
        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(caKeyPair.getPrivate());
        X509CertificateHolder holder = certBuilder.build(signer);
        X509Certificate selfSigned = new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);

        Files.writeString(keyOut, PemUtil.toPem(caKeyPair.getPrivate()));
        Files.writeString(certOut, PemUtil.toPem(selfSigned));

        System.out.println("Generated CA key pair (" + keySize + "-bit RSA), self-signed for CN=" + issuerName);
        System.out.println("  private key -> " + keyOut.toAbsolutePath());
        System.out.println("  certificate -> " + certOut.toAbsolutePath());
        System.out.println();
        System.out.println("Start the server with: --ca-key " + keyOut + " --issuer \"" + issuerName + "\"");
    }
}
