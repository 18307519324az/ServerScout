# AI Briefing

## Runtime Modes

`POST /api/v1/ai-briefing/generate` supports two explicit modes:

- `llm`: calls a configured OpenAI-compatible chat-completions endpoint and renders its structured response.
- `local-analysis`: validates security relevance, extracts evidence, and builds input-driven sections locally when no model endpoint is configured or the model call fails.

The frontend displays the returned mode. It does not present local analysis as a language-model response.

## Language Model Configuration

Set these environment variables before starting the backend:

```powershell
$env:AI_BASE_URL="https://your-openai-compatible-endpoint/v1/chat/completions"
$env:AI_API_KEY="your-api-key"
$env:AI_MODEL="your-model-name"
$env:AI_TIMEOUT_SECONDS="45"
$env:AI_MAX_ATTEMPTS="2"
```

`AI_BASE_URL` must be the full chat-completions endpoint. `AI_API_KEY` may be empty only when the configured endpoint does not require authentication.
Malformed or unavailable model responses are retried before the clearly labeled local-analysis fallback is used.

## Installed Local Model

The verified local setup uses:

- Server: `D:\local-llm\server\llama-server.exe`
- Model: `D:\local-llm\qwen2.5-1.5b-instruct-q4_k_m.gguf`
- Endpoint: `http://127.0.0.1:11434/v1/chat/completions`
- Spring profile: `local-ai`

Start the model service:

```powershell
.\start-local-llm.ps1
```

Then include `local-ai` when starting the ServerScout backend. Combine it with the profile and database configuration you normally use.

For a self-contained local demo with the H2 test database, use the existing ASCII path link and run:

```powershell
cd D:\serverscout-ai\backend
mvn spring-boot:test-run -Dspring-boot.run.profiles=test,local-ai
```

The ASCII path link points to `D:\项目\serverscout`; it exists because the Spring Boot Maven launcher cannot construct a valid runtime classpath from the Chinese project path on this machine.

## Input Behavior

- Accepts free-form text, list-style findings, and JSON-like evidence.
- Extracts CVE IDs, IP addresses, domains, ports, CVSS, EPSS, severity, and technology signals.
- Changes section structure according to detected vulnerability and exposure signals.
- Rejects empty input and text that does not contain sufficient security evidence.
- Supports English and Chinese output through the `locale` request field.

## Request Example

```json
{
  "evidence": "Asset 10.0.0.8 runs Nginx on port 443 with CVE-2024-1234 and CVSS 9.8.",
  "locale": "en"
}
```

## Verification

```powershell
cd backend
mvn clean test

cd ..\frontend
npm run build
```

The backend test suite covers the HTTP endpoint, unrelated-input rejection, free-form and JSON-like input, dynamic section structure, Chinese output, and the configured language-model call path.

To run the optional smoke test against a local OpenAI-compatible model server on `127.0.0.1:11434`:

```powershell
$env:RUN_LOCAL_LLM_TEST="true"
mvn test -Dtest=AiBriefingLocalModelIntegrationTest
```
