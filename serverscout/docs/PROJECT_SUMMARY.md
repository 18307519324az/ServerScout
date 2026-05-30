# ServerScout Project Summary

Updated: 2026-05-30

## 1. Project Baseline
Current framework and major functional modules are in place:
- Frontend: React + TypeScript + Vite
- Backend: Spring Boot + JPA
- Data: MySQL (primary), Redis (optional distributed lock)
- Core modules:
  - dashboard
  - scan tasks and task detail
  - asset management and asset detail
  - vulnerability management
  - topology
  - attack-surface map
  - external intel
  - report center
  - settings and operation logs

## 2. What Was Added in This Round

### 2.1 Concurrency Policy Adjustment
- Restored same-target concurrency limit default to `1`.
- Updated:
  - `backend/src/main/resources/application.yml`
  - `backend/src/main/java/com/serverscout/service/scan/TargetConcurrencyLimiter.java`

Rationale:
- avoid unstable behavior and duplicate high-intensity scans against the same target
- reduce contention and "pending pile-up" confusion in normal operations

### 2.2 Manual Module Enhancement
- Reworked manual TOC behavior:
  - headings are auto-discovered from rendered content
  - TOC highlight syncs with scroll position in real time
  - TOC updates when content changes
- Updated manual content to reflect current implemented features.

Updated file:
- `frontend/src/pages/ManualPage.tsx`

### 2.3 Documentation Completion
- Added root `README.md` with startup, structure, feature summary, and operational notes.
- Added `docs/ABOUT.md` to provide concise project-level "about" information.
- Added this document as summary accumulation and review artifact.

## 3. Key Issues Encountered

### 3.1 Task-linked asset query returning empty
Symptom:
- clicking "assets found" navigated to filtered asset list but page showed empty.

Root cause:
- task-scoped query sort path mismatch (`updatedAt` sorted on mapping query root).

Resolution:
- adjusted pagination/sort strategy for task-scoped asset queries (do not apply invalid sort path).

### 3.2 Packaging conflict on Windows
Symptom:
- Maven repackage failed to rename existing JAR.

Root cause:
- old running process/file lock held target JAR.

Resolution:
- stop listening process on `8080`
- remove locked artifact and rebuild
- restart service with explicit process management

### 3.3 Runtime content mismatch after frontend changes
Symptom:
- browser still showed old manual after frontend was rebuilt.

Root cause:
- backend static bundle in JAR was not repackaged/restarted after frontend dist update.

Resolution:
- rebuild frontend
- repackage backend
- restart backend and re-verify in browser

### 3.4 Mixed encoding / mojibake risk in some historical text blocks
Observation:
- parts of existing localized text contained encoding artifacts.

Mitigation:
- avoid broad risky rewrites on unrelated texts
- restrict edits to required scope and validate build/render after each change

## 4. Lessons Learned
- Keep UI routing + backend query contract tightly tested; "jump + filter" paths are high-risk regression points.
- For Windows packaging, process/JAR lock handling should be a standard release checklist step.
- Frontend dist updates must be coupled with backend repackage when serving static assets from backend.
- Large i18n files should be edited incrementally with frequent validation to avoid encoding damage.
- Manual/document pages should prefer auto-generated TOC to avoid drift from actual content structure.

## 5. Next Recommended Improvements
- Add automated end-to-end checks for:
  - task detail -> assets filtered navigation
  - manual TOC active-state behavior
  - i18n sanity smoke checks (zh/en)
- Add CI gate to validate backend package reproducibility on Windows runners.
- Add a dedicated changelog workflow for release cadence.

