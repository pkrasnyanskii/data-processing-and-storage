package org.example.j1.server;

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

import java.io.IOException;
import java.io.Reader;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

public final class CertificateAuthority implements CredentialIssuer {

    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final Duration CERTIFICATE_VALIDITY = Duration.ofDays(365);

    private final PrivateKey caPrivateKey;
    private final X500Name issuer;
    private final int subjectKeySizeBits;
    private final SecureRandom secureRandom = new SecureRandom();

    public CertificateAuthority(PrivateKey caPrivateKey, String issuerCommonName, int subjectKeySizeBits) {
        BouncyCastleInit.register();
        this.caPrivateKey = caPrivateKey;
        this.issuer = new X500Name("CN=" + issuerCommonName);
        this.subjectKeySizeBits = subjectKeySizeBits;
    }

    public static CertificateAuthority fromPemFile(Reader caKeyReader, String issuerCommonName, int subjectKeySizeBits) throws IOException {
        PrivateKey caKey = PemUtil.readPrivateKey(caKeyReader);
        return new CertificateAuthority(caKey, issuerCommonName, subjectKeySizeBits);
    }

    @Override
    public IssuedCredential issue(String subjectName) throws NoSuchAlgorithmException {
        KeyPair subjectKeyPair = generateRsaKeyPair(subjectKeySizeBits);
        X509Certificate certificate = signCertificate(subjectName, subjectKeyPair);
        return new IssuedCredential(subjectKeyPair.getPrivate(), certificate);
    }

    private KeyPair generateRsaKeyPair(int bits) throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair();
    }

    private X509Certificate signCertificate(String subjectName, KeyPair subjectKeyPair) {
        X500Name subject = new X500Name("CN=" + subjectName);
        BigInteger serial = new BigInteger(128, secureRandom).abs();
        Instant now = Instant.now();
        Date notBefore = Date.from(now);
        Date notAfter = Date.from(now.plus(CERTIFICATE_VALIDITY));

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, subject, subjectKeyPair.getPublic());
        try {
            certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            certBuilder.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
            ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                    .setProvider("BC")
                    .build(caPrivateKey);
            X509CertificateHolder holder = certBuilder.build(signer);
            return new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign certificate for subject '" + subjectName + "'", e);
        }
    }
}
