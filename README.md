# ServerScout — Attack Surface Visualization & AI Risk Briefing

[![HackOnVibe 2026](https://img.shields.io/badge/HackOnVibe-2026-blue)](https://hackonvibe.com/)

ServerScout is a full-stack attack-surface management and vulnerability operations platform. It helps small teams discover assets, run scans, identify vulnerabilities, and produce actionable security reports.

> **HackOnVibe 2026 Submission** — the integrated **AI Risk Briefing Assistant** turns scan evidence into executive summaries, remediation priorities, and report-ready language.

## What It Does

- 🔍 **Asset Discovery** — scan IP ranges, discover hosts, ports, services, OS fingerprints
- 🛡️ **Vulnerability Detection** — Nuclei-based scanning with CVE/CVSS/EPSS correlation
- 🗺️ **Attack Surface Visualization** — interactive topology maps and exposure analysis
- 🌐 **Threat Intelligence** — Shodan, Censys, VirusTotal, and NVD CVE lookups
- 📊 **Reports** — PDF and Excel export with scan summaries
- 🤖 **AI Risk Briefing** — AI-powered assistant that explains findings in business-readable language

## Quick Start

### Prerequisites
- Java 17+, Maven 3.9+, Node.js 18+
- MySQL 8.0+
- Optional: Nmap, Nuclei, Redis

### Backend
```bash
cd serverscout/backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
API: `http://localhost:8080` | Swagger: `http://localhost:8080/docs`

### Frontend
```bash
cd serverscout/frontend
npm install
npm run dev
```
UI: `http://localhost:5173` | AI Briefing: `http://localhost:5173/ai-briefing`

### Docker
```bash
cd serverscout
docker-compose up -d
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 18, TypeScript, Vite, Tailwind CSS, Ant Design, ECharts, G6, D3.js |
| Backend | Spring Boot 3.3.5, Java 17, Spring Security + JWT, JPA/Hibernate |
| Database | MySQL 8.0, Redis (optional) |
| Scanning | Nmap, Nuclei |
| AI | OpenAI-compatible model gateway, local Qwen2.5 fallback |
| DevOps | Docker, docker-compose, deploy.sh |

## HackOnVibe — AI Risk Briefing Assistant

Built during HackOnVibe 2026 on top of the existing ServerScout platform:

- **Integrated `/ai-briefing` page** — paste scan evidence or load recent scan data with one click
- **AI generation API** — Spring Boot endpoint with input validation, signal extraction, and structured risk briefing
- **Optional LLM gateway** — OpenAI-compatible API with automatic fallback to transparent local analysis
- **Dual-language** — complete English and Chinese UI
- **Input safety** — rejects empty or unrelated content

📂 See [`submission/`](submission/) for full submission materials, build scope, and judging criteria mapping.

## Project Structure

```
├── serverscout/
│   ├── backend/          # Spring Boot API + scanning orchestration
│   ├── frontend/         # React + TypeScript SPA
│   ├── deploy.sh         # Alibaba Cloud deployment script
│   ├── docker-compose.yml
│   ├── Dockerfile
│   └── serverscout-init.sql
├── docs/images/          # Application screenshots
├── prototype/            # Standalone AI briefing HTML prototype
├── submission/           # HackOnVibe submission package
│   ├── demo-video.mp4
│   ├── dorahacks-submission-text.md
│   ├── build-scope.md
│   └── judging-criteria-mapping.md
└── README.md
```

## License

This project is submitted as part of HackOnVibe 2026.
