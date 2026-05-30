# ServerScout

ServerScout is a full-stack attack-surface and vulnerability operations platform.

This repository contains:
- `backend`: Spring Boot service (API, scanning orchestration, reporting, SSE progress)
- `frontend`: React + TypeScript UI (dashboard, scan tasks, assets, topology, attack surface, intel, reports)
- `release`: deployment bundle and scripts

## Quick Start

### 1) Prerequisites
- Java 17+
- Maven 3.9+
- Node.js 18+ and npm
- MySQL 8.0+
- Optional: Redis 6/7 (for distributed target concurrency lock)
- Optional: Nmap / Nuclei installed in system PATH

### 2) Backend
```bash
cd backend
mvn spring-boot:run
```

Default:
- API: `http://localhost:8080`
- API docs: `http://localhost:8080/docs`

### 3) Frontend
```bash
cd frontend
npm install
npm run dev
```

Default:
- UI: `http://localhost:5173`

## Key Capabilities
- Scan task creation and orchestration (preset + custom strategy plugin)
- Real-time progress and discovery feed (SSE)
- Asset management with task-linked filtering and quick navigation
- Asset detail with ports/fingerprints/vulnerabilities/SSL/crawler/honeypot signals
- Interactive topology and attack-surface visualization
- Threat-intel lookup (IP / domain / CVE)
- PDF/Excel report export

## Operational Notes
- Same-target concurrency default is `1`:
  - `app.scan.target-concurrency.max-per-target` (config)
  - protects target stability and avoids duplicate aggressive scans
- Redis mode can be enabled for multi-instance deployments.

## Documentation
- Project summary (implementation, new additions, lessons learned):  
  `docs/PROJECT_SUMMARY.md`
- About document:  
  `docs/ABOUT.md`
- In-app manual page:
  `/manual`

## Current Update Scope
This round includes:
- Restored same-target concurrency upper bound to `1`
- Manual page TOC upgraded to auto-generated + real-time scroll sync
- Manual content refreshed to match implemented features
- Assets-found jump path and i18n flow documented

