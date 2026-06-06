# Judging Criteria Mapping

Source pages:

- https://hackonvibe.com/
- https://dorahacks.io/hackathon/hackonvibe/detail

## Required Submission Items

| Requirement | Package Evidence |
|---|---|
| Clear product description | `submission/dorahacks-submission-text.md` |
| Working demo or prototype | Integrated `/ai-briefing` page, deployed ServerScout URL, and `prototype/ai-security-report-assistant.html` |
| Target users | `submission/dorahacks-submission-text.md` |
| Core functionality | `submission/dorahacks-submission-text.md` |
| AI-powered features | `submission/dorahacks-submission-text.md`, integrated `/ai-briefing` page, and prototype |
| User flow or key screens | `submission/dorahacks-submission-text.md` and `screenshots` |
| Business model and monetization idea | `submission/dorahacks-submission-text.md` |
| Go-to-market strategy | `submission/dorahacks-submission-text.md` |
| What was built during the hackathon | `submission/build-scope.md` |
| Future development roadmap | `submission/dorahacks-submission-text.md` |

## Usefulness

ServerScout AI Risk Briefing Assistant solves a recurring operational problem: small teams receive scan output but do not know how to turn it into prioritized remediation work. The assistant converts technical evidence into a risk summary, priority order, and action list.

## Functionality

The base ServerScout product already provides authentication, dashboard, assets, scan tasks, vulnerabilities, topology, external intelligence, reports, settings, and logs. The hackathon work adds an integrated AI briefing workflow that explains the business value of scan findings.

## AI Implementation

The AI feature is the risk briefing assistant. It uses scan evidence such as assets, ports, services, vulnerabilities, CVE, CVSS, EPSS, exposure, and intelligence results as input. The output is a concise risk brief, remediation priority, and report-ready explanation.

## Business Potential

Target customers are small businesses, internal IT teams, freelance security testers, and junior security engineers. The pricing model can start with free local/student use, then expand to team subscription, per-report pricing, and consultant service packages.

## Execution

The package contains a deployed baseline product, an integrated AI briefing page in the copied source code, a browser-openable AI briefing prototype, screenshots, submission text, video script, and a build-scope document that separates existing work from the HackOnVibe addition.

## Presentation

The demo can be presented in 2-3 minutes:

1. Show deployed ServerScout.
2. Explain scan evidence and risk data.
3. Open the integrated AI Briefing page.
4. Generate a risk brief.
5. Explain why the result helps small teams decide what to fix first.

## Community Validation

Before final submission, collect at least one lightweight validation signal:

- ask a classmate or developer friend whether the risk brief is understandable
- post the demo in the HackOnVibe Discord
- ask for feedback during the mentor session
- record one piece of feedback and add it to the DoraHacks submission

Do not leave community validation blank if there is time before submission.
