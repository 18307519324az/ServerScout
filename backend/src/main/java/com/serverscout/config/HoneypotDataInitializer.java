package com.serverscout.config;

import com.serverscout.entity.HoneypotRule;
import com.serverscout.repository.HoneypotRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoneypotDataInitializer implements CommandLineRunner {

    private final HoneypotRuleRepository ruleRepository;

    @Override
    public void run(String... args) {
        if (ruleRepository.count() > 0) {
            log.info("Honeypot rules already exist ({}), skipping seeding", ruleRepository.count());
            return;
        }

        List<HoneypotRule> rules = buildRules();
        ruleRepository.saveAll(rules);
        log.info("Seeded {} honeypot detection rules", rules.size());
    }

    private List<HoneypotRule> buildRules() {
        return List.of(
            // ═══════════ SSH Honeypots ═══════════
            rule("Cowrie SSH Banner", "SERVICE_BANNER", "banner",
                    "(?i).*SSH-2\\.0-OpenSSH.*cowrie.*", "Cowrie", "SSH", "HIGH",
                    "Cowrie SSH honeypot — banner contains 'cowrie' keyword"),

            rule("Cowrie SSH Version Pattern", "SERVICE_BANNER", "banner",
                    "(?i).*SSH-2\\.0-OpenSSH_6\\.0p1 Debian-4~bpo70\\+1.*", "Cowrie", "SSH", "HIGH",
                    "Cowrie default SSH banner version string"),

            rule("Kippo SSH Banner", "SERVICE_BANNER", "banner",
                    "(?i).*SSH-2\\.0-OpenSSH.*kippo.*", "Kippo", "SSH", "HIGH",
                    "Kippo SSH honeypot — legacy Cowrie predecessor"),

            rule("SSH Honeypot Default Port", "PORT_PATTERN", "port",
                    "(?i)(2222|22222).*ssh.*", "Generic SSH Honeypot", "SSH", "LOW",
                    "SSH on non-standard port (2222/22222) — weak indicator"),

            // ═══════════ Telnet Honeypots ═══════════
            rule("Cowrie Telnet Banner", "SERVICE_BANNER", "banner",
                    "(?i).*(cowrie|kippo).*", "Cowrie", "SSH", "HIGH",
                    "Telnet banner with Cowrie/Kippo identifier"),

            rule("Telnet Honeypot Login Prompt", "SERVICE_BANNER", "banner",
                    "(?i).*(Welcome to|login:|Username:|user:).*", "Generic Telnet Honeypot", "SSH", "LOW",
                    "Generic telnet login prompt — very weak indicator"),

            // ═══════════ HTTP Web Honeypots ═══════════
            rule("Glastopf HTTP Header", "HTTP_HEADER", "server",
                    "(?i).*Apache/2\\.2\\.22.*\\(Ubuntu\\).*", "Glastopf", "HTTP", "MEDIUM",
                    "Glastopf web honeypot — default Apache version header"),

            rule("Dionaea HTTP Server", "HTTP_HEADER", "server",
                    "(?i).*nginx/0\\.7\\.6[7-9].*", "Dionaea", "HTTP", "MEDIUM",
                    "Dionaea multi-service honeypot — known nginx version"),

            rule("Honeyd HTTP Server", "HTTP_HEADER", "server",
                    "(?i).*Apache/1\\.3\\.(27|29|31).*", "Honeyd", "HTTP", "MEDIUM",
                    "Honeyd honeypot framework — legacy Apache version"),

            rule("Nodepot HTTP Header", "HTTP_HEADER", "server",
                    "(?i).*Werkzeug/.*Python.*", "Nodepot", "HTTP", "LOW",
                    "Nodepot web honeypot — Python Werkzeug server"),

            rule("SNARE/TANNER HTTP", "HTTP_BODY", "body",
                    "(?i).*(snare|tanner|mushmush).*", "Snare/Tanner", "HTTP", "MEDIUM",
                    "SNARE/TANNER web honeypot framework"),

            rule("Web Honeypot Default Title", "HTTP_BODY", "title",
                    "(?i).*(phishing|login|sign.?in|admin.*panel).*", "Generic Web Honeypot", "HTTP", "LOW",
                    "Common honeypot login page titles — weak indicator"),

            // ═══════════ ICS/SCADA Honeypots ═══════════
            rule("Conpot HTTP Header", "HTTP_HEADER", "server",
                    "(?i).*Conpot.*", "Conpot", "ICS", "HIGH",
                    "Conpot ICS/SCADA honeypot — explicit header"),

            rule("Conpot Modbus Banner", "SERVICE_BANNER", "banner",
                    "(?i).*(conpot|modbus).*", "Conpot", "ICS", "HIGH",
                    "Conpot ICS honeypot — Modbus service banner"),

            rule("Conpot Siemens S7", "SERVICE_BANNER", "banner",
                    "(?i).*(Siemens.*SIMATIC|PLC.*S7).*", "Conpot", "ICS", "MEDIUM",
                    "Conpot — Siemens S7 PLC emulation pattern"),

            rule("GasPot HTTP", "HTTP_HEADER", "server",
                    "(?i).*GasPot.*", "GasPot", "ICS", "HIGH",
                    "GasPot ICS honeypot — gas tank monitoring emulation"),

            rule("DNP3 Honeypot", "SERVICE_BANNER", "banner",
                    "(?i).*(dnp3|outstation).*", "Generic ICS Honeypot", "ICS", "MEDIUM",
                    "DNP3 protocol service — potential ICS honeypot"),

            // ═══════════ Database Honeypots ═══════════
            rule("MySQL Honeypot Banner", "SERVICE_BANNER", "banner",
                    "(?i).*(mysql_native_password|mysql.*5\\.(5|6)\\.).*caching_sha2.*", "Dionaea", "DATABASE", "LOW",
                    "MySQL honeypot — common Dionaea MySQL banner pattern"),

            rule("Redis Honeypot", "SERVICE_NAME", "service",
                    "(?i).*(redis).*", "Generic DB Honeypot", "DATABASE", "LOW",
                    "Redis service — often deployed as honeypot"),

            rule("MongoDB Honeypot Port", "PORT_PATTERN", "port",
                    "(?i)27017.*mongod.*", "Generic DB Honeypot", "DATABASE", "LOW",
                    "MongoDB on default port 27017"),

            rule("Elasticsearch Honeypot", "HTTP_HEADER", "server",
                    "(?i).*(Elasticsearch|elastic).*", "ElasticPot", "DATABASE", "MEDIUM",
                    "Elasticsearch honeypot (ElasticPot)"),

            rule("Dionaea MSSQL Banner", "SERVICE_BANNER", "banner",
                    "(?i).*Microsoft SQL Server.*Dionaea.*", "Dionaea", "DATABASE", "HIGH",
                    "Dionaea MSSQL honeypot banner"),

            // ═══════════ FTP Honeypots ═══════════
            rule("Dionaea FTP Banner", "SERVICE_BANNER", "banner",
                    "(?i).*(220.*FTP|vsftpd).*dionaea.*", "Dionaea", "FTP", "MEDIUM",
                    "Dionaea FTP honeypot — banner contains dionaea"),

            rule("Honeyd FTP Banner", "SERVICE_BANNER", "banner",
                    "(?i).*(220.*vsftpd\\s+2\\.3\\.4).*", "Honeyd", "FTP", "MEDIUM",
                    "Honeyd FTP — known vulnerable vsftpd version banner"),

            rule("ProFTPD Backdoor Port", "SERVICE_BANNER", "banner",
                    "(?i).*ProFTPD 1\\.3\\.3c.*", "Generic FTP Honeypot", "FTP", "LOW",
                    "ProFTPD 1.3.3c (backdoored version) — often used in honeypots"),

            // ═══════════ SMB Honeypots ═══════════
            rule("Dionaea SMB Banner", "SERVICE_BANNER", "banner",
                    "(?i).*(SMB|Samba).*dionaea.*", "Dionaea", "SMB", "HIGH",
                    "Dionaea SMB honeypot — explicit dionaea in banner"),

            rule("Honeyd SMB", "SERVICE_NAME", "service",
                    "(?i).*(netbios-ssn|microsoft-ds).*", "Honeyd", "SMB", "LOW",
                    "Honeyd SMB honeypot — NetBIOS/SMB service (weak indicator)"),

            // ═══════════ SIP/VoIP Honeypots ═══════════
            rule("Dionaea SIP Banner", "SERVICE_BANNER", "banner",
                    "(?i).*(SIP/2\\.0|Asterisk).*dionaea.*", "Dionaea", "VOIP", "MEDIUM",
                    "Dionaea SIP honeypot banner"),

            // ═══════════ Other Honeypots ═══════════
            rule("Amun Honeypot", "SERVICE_BANNER", "banner",
                    "(?i).*amun.*", "Amun", "GENERIC", "HIGH",
                    "Amun low-interaction honeypot"),

            rule("KFSensor Banner", "SERVICE_BANNER", "banner",
                    "(?i).*(KF Sensor|KeyFocus).*", "KFSensor", "GENERIC", "HIGH",
                    "KFSensor commercial honeypot"),

            rule("Honeypot Service Labels", "SERVICE_NAME", "service",
                    "(?i).*(honeypot|honeynet|trap|decoy).*", "Generic", "GENERIC", "MEDIUM",
                    "Service name explicitly mentioning honeypot"),

            rule("VMWare Honeypot Pattern", "HTTP_HEADER", "server",
                    "(?i).*(VMware ESXi.*honeypot|vCenter.*decoy).*", "VMware Honeypot", "GENERIC", "HIGH",
                    "VMware-based honeypot detection pattern"),

            rule("Low-Interaction Python Honeypot", "SERVICE_BANNER", "banner",
                    "(?i).*(python|twisted|tornado).*(honeypot|honeynet).*", "Generic", "GENERIC", "MEDIUM",
                    "Python-based low-interaction honeypot framework"),

            rule("RDP Honeypot", "SERVICE_BANNER", "banner",
                    "(?i).*(RDP|Remote Desktop).*(pyrdp|rdpy).*", "PyRDP", "RDP", "MEDIUM",
                    "PyRDP-based RDP honeypot"),

            rule("Docker Honeypot Pattern", "HTTP_HEADER", "server",
                    "(?i).*(Cowrie.*docker|docker.*honeypot).*", "Docker Honeypot", "GENERIC", "MEDIUM",
                    "Docker-based honeypot detection"),

            // ═══════════ SSL/TLS Certificate Patterns ═══════════
            rule("Honeypot SSL Certificate", "SSL_CERT", "subject",
                    "(?i).*(honeypot|decoy|trap|fake).*", "Generic", "GENERIC", "HIGH",
                    "SSL certificate subject contains honeypot indicators"),

            rule("Self-Signed SSL Default", "SSL_CERT", "issuer",
                    "(?i).*(localhost|127\\.0\\.0\\.1|example|test).*", "Generic", "GENERIC", "LOW",
                    "Self-signed cert issued to localhost — very weak indicator")
        );
    }

    private HoneypotRule rule(String name, String matchType, String matchField,
                               String pattern, String honeypotType, String category,
                               String confidence, String description) {
        return HoneypotRule.builder()
                .ruleName(name)
                .matchType(matchType)
                .matchField(matchField)
                .matchPattern(pattern)
                .honeypotType(honeypotType)
                .honeypotCategory(category)
                .confidence(confidence)
                .description(description)
                .enabled(true)
                .build();
    }
}
