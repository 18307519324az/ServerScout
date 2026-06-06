# DoraHacks Submission Text

## Project Title

ServerScout AI Risk Briefing Assistant

## One-Sentence Pitch

An AI-assisted security reporting assistant that turns ServerScout asset, port, vulnerability, CVE, CVSS, EPSS, and intelligence data into clear remediation priorities for small teams.

## Product Description

ServerScout is an attack-surface and vulnerability operations platform with a private deployment and a verified local build. It helps users discover assets, run scan tasks, identify services and vulnerabilities, view topology and attack-surface relationships, query external intelligence, and export PDF or Excel reports.

For HackOnVibe, the product is extended into an AI-assisted micro-product: ServerScout AI Risk Briefing Assistant. The assistant is integrated into the ServerScout frontend as an `AI Briefing` page. It takes technical scan evidence and produces a business-readable risk brief. It explains the most urgent risks, why they matter, what should be fixed first, and how the result can be written into a security report.

## Target Users

- Small and medium-sized businesses without dedicated security analysts
- Internal IT teams responsible for basic vulnerability remediation
- Junior security engineers who need help writing readable reports
- Freelance security testers who need faster client-facing summaries
- Student teams learning asset scanning and vulnerability triage

## Problem

Security scanners produce technical data, but small teams often struggle to decide what matters first. They may see open ports, service fingerprints, CVE IDs, CVSS scores, EPSS scores, screenshots, and intelligence results, but still need someone to translate the data into a concrete remediation plan.

## Core Functionality

- Asset and service discovery through ServerScout
- Vulnerability identification and CVE correlation
- External intelligence lookup
- Risk scoring and vulnerability status tracking
- PDF and Excel report export
- AI risk briefing that summarizes the most important findings
- AI remediation plan that explains short-term and long-term actions
- One-click loading of recent ServerScout asset and vulnerability data
- Free-form input parsing, security relevance validation, and rejection of unrelated content

## AI-Powered Features

- Converts raw vulnerability and asset data into an executive summary
- Explains why specific findings should be prioritized
- Groups remediation actions into immediate, short-term, and follow-up work
- Produces report-ready language for business stakeholders
- Helps users understand scan evidence without reading every raw technical detail
- Uses a configured OpenAI-compatible language model when available
- Provides a transparent input-driven local security analysis mode when no model endpoint is configured
- Verified with a portable local Qwen2.5 1.5B model through an OpenAI-compatible `llama.cpp` server

## User Flow

1. User logs in to ServerScout.
2. User creates a scan task for an IP address or domain.
3. ServerScout collects asset, port, service, vulnerability, screenshot, and intelligence data.
4. User opens the AI Risk Briefing Assistant.
5. The assistant validates the input and generates a readable, input-specific risk summary and prioritized remediation plan.
6. User exports or copies the result into a PDF or Excel report.

## Key Screens

- Login page
- Dashboard
- Asset list and asset detail
- Scan task list and scan task detail
- Vulnerability list and vulnerability detail
- Topology and attack-surface visualization
- External intelligence page
- Report center
- Integrated AI Briefing page
- Standalone AI Risk Briefing Assistant prototype

## Technology Stack

Frontend:

- React 18
- TypeScript
- Vite
- Tailwind CSS
- Ant Design
- React Query
- React Router
- i18next
- jsencrypt

Visualization:

- ECharts
- G6
- D3.js

Backend:

- Spring Boot 3.3.5
- Java 17
- Spring Security
- JWT
- RSA
- Spring Data JPA
- Hibernate

Database and tools:

- MySQL 8.0
- Nmap
- Nuclei
- iText 7
- Apache POI
- Maven
- npm
- Flyway
- Dockerfile
- docker-compose

## Business Model

Initial customers are small businesses, small IT teams, security service providers, and students learning practical security operations.

Pricing idea:

- Free tier for local or student use
- Monthly team plan for small companies
- Per-report pricing for freelancers
- Service package for security consultants who need client-facing reports

## Go-To-Market Plan

- Publish a demo video and share the demo link privately when needed
- Share with small IT teams, student security communities, and freelance security testers
- Offer free report generation for early users
- Collect feedback on report clarity and remediation usefulness
- Add integrations based on the first users' repeated workflows

## What Was Built During HackOnVibe

During HackOnVibe, I built the AI risk briefing product layer on top of the existing ServerScout platform:

- Integrated AI Briefing page at `/ai-briefing`
- Sidebar navigation entry for the new page
- Scan evidence input area
- Live recent-scan data loading
- Backend generation API and optional secure model gateway
- Dynamic risk brief, signal extraction, remediation priority, and report-ready wording
- Unrelated-input rejection and English/Chinese modes
- AI assistant product concept
- Automated backend behavior tests and frontend production build verification
- Demo flow from scan result to risk brief
- DoraHacks submission text
- Judging criteria response
- Short video script
- Business model and go-to-market plan
- Resume-ready competition summary

## Future Roadmap

- Generate PDF report sections directly from AI output
- Add organization-specific remediation templates
- Add feedback buttons for report quality improvement
- Add team collaboration and approval workflow for remediation plans

## Demo Links

- ServerScout demo link: shared privately when needed
- Integrated local AI briefing page: `/ai-briefing`
- Local AI briefing prototype: `prototype/ai-security-report-assistant.html`
- Source copy: `source/serverscout`
