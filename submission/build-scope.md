# Build Scope

## Existing Baseline Before HackOnVibe

ServerScout already had a private deployment and a verified local build.

Existing capabilities:

- Login authentication and user management
- Asset inventory and asset detail pages
- Scan tasks with Quick, Stealth, Web, and Full templates
- Nmap-based port scanning
- Nuclei-based vulnerability detection
- Vulnerability list and detail workflow
- CVE, CVSS, and EPSS related risk data
- Topology, attack-surface map, crawler URL tree, and screenshots
- Shodan InternetDB, Censys, VirusTotal, and NVD CVE intelligence
- PDF and Excel report export
- Operation logs, settings, scheduled scan, webhook, mail notification, and plugin management
- Alibaba Cloud deployment

## Built For HackOnVibe

The HackOnVibe addition is ServerScout AI Risk Briefing Assistant.

New contribution:

- Product positioning for an AI-assisted security reporting workflow
- Integrated `AI Briefing` route in the ServerScout frontend at `/ai-briefing`
- Main navigation entry for `AI Briefing`
- AI briefing page with free-form evidence input, recent scan-data loading, request/loading/error states, dynamic output sections, and complete English/Chinese UI
- Spring Boot AI briefing API with security relevance validation, input-driven signal extraction, and explicit rejection of empty or unrelated input
- Optional OpenAI-compatible model gateway with secure environment-variable configuration and a clearly labeled local analysis fallback
- Verified portable local Qwen2.5 1.5B model service on drive D, with a `local-ai` Spring profile and startup script
- Automated tests proving different inputs produce different output, free-form evidence works, unrelated input is rejected, and Chinese output is supported
- Standalone AI briefing prototype page for fallback demo use
- A demo flow that starts from ServerScout scan evidence and ends with readable remediation guidance
- A judging-criteria response package for usefulness, functionality, AI implementation, business potential, execution, presentation, and community validation
- Short video script and DoraHacks submission text
- Business model, target users, go-to-market plan, and future roadmap
- Scan reliability hardening: startup recovery for interrupted tasks, bounded same-target queue waits, strict port-range validation, explicit-port Nmap behavior, cancel/rescan actions, conditional polling, and corrected active-task counting
- Automated Nmap command tests proving that explicit requested ports override Quick scan top-port defaults

## Why This Fits HackOnVibe

HackOnVibe asks for an AI-assisted application useful for individuals, small teams, or small and medium-sized businesses. ServerScout AI Risk Briefing Assistant targets small teams that receive security scan data but do not have enough security staff to translate raw findings into business-readable actions.

The integrated AI page turns technical signals into:

- executive risk summary
- prioritized remediation plan
- stakeholder-friendly explanation
- report-ready remediation language
- next action list for small teams

## What To Say In The Pitch

The base scanning platform already existed. During HackOnVibe, I focused the product into a smaller AI micro-product: an integrated risk briefing assistant that sits on top of scan data and helps small teams understand what to fix first and why.
