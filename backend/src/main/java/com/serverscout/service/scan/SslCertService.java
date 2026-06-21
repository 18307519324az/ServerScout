package com.serverscout.service.scan;

import com.serverscout.entity.Port;
import com.serverscout.entity.SslCertificate;
import com.serverscout.repository.SslCertificateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class SslCertService {

    private final SslCertificateRepository sslCertRepository;

    public SslCertService(SslCertificateRepository sslCertRepository) {
        this.sslCertRepository = sslCertRepository;
    }

    public record SslCertResult(
            String subject,
            String issuer,
            Instant notBefore,
            Instant notAfter,
            String serialNumber,
            String fingerprintSha256,
            String san,
            String sigAlg,
            int keySize,
            boolean isExpired
    ) {}

    public SslCertResult fetchCertificate(String ip, Port port) {
        int portNum = port.getPortNumber();

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] c, String a) {}
                        public void checkServerTrusted(X509Certificate[] c, String a) {}
                    }
            };
            sslContext.init(null, trustAll, new java.security.SecureRandom());

            SSLSocketFactory factory = sslContext.getSocketFactory();
            try (SSLSocket socket = (SSLSocket) factory.createSocket(ip, portNum)) {
                socket.setSoTimeout(5000);
                socket.startHandshake();

                X509Certificate cert = (X509Certificate) socket.getSession().getPeerCertificates()[0];

                String subject = cert.getSubjectX500Principal().getName();
                String issuer = cert.getIssuerX500Principal().getName();
                Instant notBefore = cert.getNotBefore().toInstant();
                Instant notAfter = cert.getNotAfter().toInstant();
                String serialNumber = cert.getSerialNumber().toString(16);
                String sigAlg = cert.getSigAlgName();
                int keySize = cert.getPublicKey().getEncoded().length * 8;
                boolean isExpired = Instant.now().isAfter(notAfter) || Instant.now().isBefore(notBefore);

                // SHA-256 fingerprint
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] hash = md.digest(cert.getEncoded());
                StringBuilder hex = new StringBuilder();
                for (byte b : hash) hex.append(String.format("%02x", b));
                String fingerprint = hex.toString().toUpperCase();

                // SAN
                List<String> sanList = new ArrayList<>();
                if (cert.getSubjectAlternativeNames() != null) {
                    for (List<?> entry : cert.getSubjectAlternativeNames()) {
                        if (entry.size() >= 2) sanList.add(entry.get(1).toString());
                    }
                }
                String san = sanList.isEmpty() ? null : String.join(",", sanList);

                return new SslCertResult(subject, issuer, notBefore, notAfter,
                        serialNumber, fingerprint, san, sigAlg, keySize, isExpired);
            }
        } catch (Exception e) {
            log.debug("SSL cert fetch failed for {}:{} - {}", ip, portNum, e.getMessage());
            return null;
        }
    }

    public void saveCertResult(Port port, SslCertResult result) {
        if (result == null) return;

        Optional<SslCertificate> existing = sslCertRepository.findByPortId(port.getId());
        SslCertificate cert;
        if (existing.isPresent()) {
            cert = existing.get();
        } else {
            cert = SslCertificate.builder().port(port).build();
        }

        cert.setSubject(result.subject());
        cert.setIssuer(result.issuer());
        cert.setNotBefore(result.notBefore());
        cert.setNotAfter(result.notAfter());
        cert.setSerialNumber(result.serialNumber());
        cert.setFingerprintSha256(result.fingerprintSha256());
        cert.setSan(result.san());
        cert.setSigAlg(result.sigAlg());
        cert.setKeySize(result.keySize());
        cert.setIsExpired(result.isExpired());

        sslCertRepository.save(cert);
    }
}
