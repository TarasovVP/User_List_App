---
name: android-feature-validation
description: Validate Android feature changes in User List App through scoped build, unit, Room migration, instrumented UI, lint, and device checks.
---

# Android Feature Validation Skill

This skill defines the standard procedure for validating new features in the **User List App**.

## Validation Procedure

### 1. Verification of Tools
- Check for Android CLI availability using `android --help` or equivalent project-specific tools.
- Verify device availability if instrumented tests or device validation is required.
- **Pre-requisite:** Do not assume tool success; confirm availability before proceeding.

### 2. Scoped Checks (Smallest First)
- **Local Logic:** Run targeted unit tests for the modified component first (e.g., `./gradlew :app:testDebugUnitTest --tests "com.example.userlistapp.SpecificTest"`).
- **Compilation:** Ensure the specific module builds.

### 3. Static Analysis & Build
- **Complete Build:** Execute `./gradlew assembleDebug` to ensure full project compilation.
- **Lint:** Execute `./gradlew lintDebug` to check for code quality and resource issues.

### 4. Logic Verification (Unit Tests)
- **Complete Suite:** Run `./gradlew :app:testDebugUnitTest` to verify business logic and state management across the application.

### 5. Persistence & Integration (Instrumented Tests)
- **Room Migrations:** Run instrumented migration tests to ensure schema stability:
  `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.userlistapp.UserDatabaseMigrationTest`
- **DAO Tests:** Run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.userlistapp.RoomUserDaoTest`

### 6. UI & Flow Validation
- **Compose UI Tests:** Run `./gradlew :app:connectedDebugAndroidTest` for end-to-end user flow verification.
- **Device Validation:** Select relevant Android CLI commands only after checking availability and project context. 

### 7. Determinism Check
- Verify that features involving time use an injected `TimeProvider`.
- Ensure tests use a controlled `TimeProvider` to assert specific timestamps.

## Constraints & Approvals
- **Approval Required:** Explicit approval must be obtained before any network access, installation of new dependencies, or device-changing operations.
- **Preservation:** Ensure unrelated user changes are preserved during the validation process.

## Output Checklist
For every validation run, provide a report containing:
- [ ] Executed command
- [ ] Exit status (Success/Failure)
- [ ] Failures (if any, with logs/details)
- [ ] Skipped checks (with reasons, e.g., "Device not available")
- [ ] Remaining risks
