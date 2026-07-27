# Project AGENTS.md

This document outlines the architecture, coding conventions, and safety standards for the **User List App**.

## 1. Architecture: MVVM with Clean Architecture

The project follows Clean Architecture principles, ensuring a separation of concerns between data, domain, and UI layers.

### Layers
- **UI Layer (feature/*):** Built with Jetpack Compose. Follows the MVVM pattern with `StateFlow` and unidirectional UI state/actions.
  - **ViewModels:** Maintain UI state using `StateFlow` and emit one-time events via `SharedFlow`.
  - **Composables:** Route-level Composables collect state and pass it down. Reusable UI components should be state-hoisted where practical to maintain statelessness.
- **Domain Layer (domain/*):** Contains business logic and models.
  - **Models:** Plain Kotlin objects representing the business entities.
  - **Use Cases:** Granular business logic components.
  - **Repository Interfaces:** Defined here to be implemented in the data layer.
- **Data Layer (data/*):** Handles data persistence and remote communication.
  - **Room:** Local persistence.
  - **Retrofit:** Remote API communication.
  - **Repositories:** Coordinate local and remote data sources.

## 2. Coding Conventions

- **Concurrency:** Use Kotlin Coroutines and Flow. Prefer `stateIn` for exposing state in ViewModels.
- **Dependency Injection:** Hilt is used for managing dependency injection for key components including the Application, Activities, ViewModels, and Workers.
- **Unidirectional Data Flow:** UI state flows down, actions (events) flow up.
- **Time:** The `TimeProvider` interface is the project convention for all time-dependent behavior to ensure deterministic testing.

## 3. Persistence and Safety

- **Room Migrations:** Every schema change must include a version increment and an explicit `Migration` object.
- **Data Integrity:** Local interaction data (favorites, notes, etc.) must be preserved during backend syncs. Use separate tables or distinct columns that are not overwritten by remote DTOs.
- **Resource Safety:** Never hardcode strings in UI; use `res/values/strings.xml`.

## 4. Testing Standards

- **Unit Tests:** Mandatory for Use Cases and ViewModels.
- **Data Tests:** Room DAOs and Migrations must be tested with instrumented tests.
- **UI Tests:** Compose UI tests for critical user flows.
- **Dependencies:** Use fakes, test implementations, or mocked dependencies in tests to ensure isolation.

## 5. Agent Safety

- **No Credential Access:** Do not access or expose `local.properties`, `.env`, keystores, or JSON service accounts.
- **Change Management:** Preserve unrelated user changes. Always review the exact diff before applying modifications to ensure no unintended data loss or desync.
- **Explicit Approval:** Ask for approval before network access, dependency changes, or destructive operations.
