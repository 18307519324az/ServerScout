# Verification Notes

## Source Project

Verified in:

`D:\项目\serverscout\frontend`

Commands and checks:

- `npm run build`
- `mvn test`
- Browser check for `http://127.0.0.1:4174/#architecture`
- Browser check for `http://127.0.0.1:4174/login`
- Browser check for `http://127.0.0.1:4175/ai-briefing`

Results:

- Frontend build passed.
- Backend `mvn clean test` passed with 37 tests: 36 passed and the opt-in live-model smoke test was skipped by default.
- Default UI language is English in a clean browser context with `zh-CN` locale.
- Login page no longer displays the removed notice text or the removed full-text link.
- Integrated AI Briefing page is present in the sidebar.
- Different free-form security inputs produce different generated results.
- Vulnerability and exposure signals dynamically change the returned section structure.
- JSON-like evidence is accepted without requiring the example format.
- Empty and unrelated input are rejected.
- Unrelated text containing only technology names is rejected.
- Clicking `Load Recent Scan Data` loads asset and vulnerability evidence into the input.
- Clicking `Generate AI Brief` renders dynamic sections returned by the backend API.
- Switching to Chinese updates the AI briefing interface.
- Frontend Playwright acceptance checks passed for scan-data loading, English output, Chinese UI, and unrelated-input rejection.
- The configured OpenAI-compatible model call path is covered by an HTTP integration test that verifies user evidence is sent and structured model output is used.
- Portable `llama.cpp` and Qwen2.5 1.5B were installed on drive D.
- The local model health endpoint returned `{"status":"ok"}`.
- ServerScout's optional local-model smoke test called the running Qwen service and returned `mode: llm` in three consecutive actual-project runs.
- `D:\serverscout-ai` was created as a directory link to the actual project to work around the Spring Boot Maven launcher's Chinese-path classpath failure.
- The H2 + `local-ai` backend started successfully on `127.0.0.1:8080`.
- Search found no remaining matches for the removed notice wording in `D:\项目\serverscout`.
- Search found no remaining matches for the removed notice wording in the submission package.

Generated screenshots:

- `prototype/home-default-english.png`
- `prototype/login-clean.png`
- `prototype/ai-briefing-integrated.png`

Generated demo video:

- `video/serverscout-ai-briefing-voiceover.mp4`
- Format: MP4, H.264 video, AAC audio, 1280x720
- Duration metadata verified: 112.104 seconds
- AI voice-over generated with Microsoft neural TTS through `edge-tts`.
- Burned-in English subtitles are included in the video.
- FFmpeg full decode verification passed from start to end.
- Frame extraction verified visible subtitles at 3 seconds, 60 seconds, and 108 seconds.

## Live Alibaba Cloud Deployment

Checked:

Private deployment link removed from the public package.

Current live result:

- The private deployment status is not exposed in the public package.

Action required before recording:

- record the video from the verified local preview.

## Submission Package Source Copy

The copied source under `source/serverscout/frontend` does not include `node_modules`, so `npm run build` is not runnable there until dependencies are installed. The verified source files were synced from `D:\项目\serverscout` into the package after the successful source-project build.

## Scan Reliability Verification - 2026-06-06

- Backend test suite: 39 tests executed, 0 failures, 1 skipped.
- Added Nmap command tests: explicit `22,80,443` produces `-p 22,80,443` and does not include `--top-ports`; Quick scan without an explicit range uses `--top-ports 100`.
- Frontend production build completed successfully with Vite 5.4.21.
- Code inspection confirms fingerprinting, vulnerability scanning, and crawling execute only when their corresponding task flags are enabled.
- State-flow hardening includes startup recovery and a configurable same-target wait timeout.
- Runtime API verification reproduced the original failure path and confirmed the final transition sequence: `pending / Retry 1/3` -> `pending / Retry 2/3` -> `pending / Retry 3/3` -> `failed`, instead of remaining indefinitely at the first retry.
- Runtime API verification confirmed invalid descending port range `9000-8000` returns HTTP `400`.
- Runtime API verification confirmed an active task can be cancelled and returns status `cancelled`.
