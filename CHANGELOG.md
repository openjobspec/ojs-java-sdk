# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed
- Fixed compilation error in `OpenTelemetryMiddleware` where lambda returned a value from void method
- Fixed silent exception swallowing in `OJSWorker` heartbeat loop — errors are now logged
- Fixed DRY violation in `HttpTransport` by extracting shared request logic into `doRequest()`
- Fixed `OJSTesting` thread-safety: replaced `volatile` singleton with `ThreadLocal` for safe parallel test execution
- Fixed silent exception swallowing in `OJSWorker.nackJob()` — errors are now logged at DEBUG level

### Changed
- Extracted `HttpTransport.Json` nested class to top-level `org.openjobspec.ojs.transport.Json` for better modularity
- Decoupled `OJSTesting` from `OJSClient` — `OJSClient` no longer imports or references `OJSTesting`
- `OJSTesting.client()` and `OJSTesting.transport()` provide a `FakeTransport`-backed client for tests

### Added
- `OJSClient` implements `AutoCloseable` — enables try-with-resources for proper async executor shutdown
- `module-info.java` for JPMS support — exports `org.openjobspec.ojs`, `org.openjobspec.ojs.transport`, `org.openjobspec.ojs.testing`
- Input validation using `OJSError.ValidationError` for blank job types in `JobRequest` and `OJSWorker.register()`
- JaCoCo coverage badge in README and CI workflow
- JSON parser safety limits: max depth (128) and max input length (10 MB) to prevent stack overflow and OOM
- Unit tests for `OpenTelemetryMiddleware` (0% → 100% coverage)
- Unit tests for `OJSTesting` assertions and utilities (53% → 100% coverage)
- Unit tests for `OJSClient.Builder` retry and transport options (52% → 100% coverage)
- `OJSTesting` fake mode integration with `OJSClient` — `OJSTesting.fake()` now intercepts enqueue calls
- Platform logging (`System.Logger`) throughout `OJSWorker` for start/stop, job processing, and errors
- `CONTRIBUTING.md` with build instructions and contribution guidelines
- `CODE_OF_CONDUCT.md` (Contributor Covenant v2.1)
- `SECURITY.md` with vulnerability reporting policy
- `CHANGELOG.md` (this file)
- GitHub issue templates (bug report, feature request)
- GitHub pull request template
- Dependabot configuration for Maven dependency updates

## [0.1.0] - 2026-02-13

### Added
- Initial release of the OJS Java SDK
- `OJSClient` for enqueuing jobs, managing workflows, and querying job status
- `OJSWorker` with virtual thread (Project Loom) concurrent job processing
- `Job` record with full OJS Core Specification v1.0.0-rc.1 attributes
- `RetryPolicy` and `UniquePolicy` configuration records with builder pattern
- `Workflow` primitives: chain (sequential), group (parallel), batch (parallel with callbacks)
- `JobHandler` and `Middleware` functional interfaces
- `OJSError` sealed interface hierarchy (`ApiError`, `ValidationError`, `TransportError`)
- `HttpTransport` with zero-dependency JSON encoder/decoder
- `Transport` interface for custom/test transports
- `OpenTelemetryMiddleware` for instrumentation via `TelemetryHooks`
- `OJSTesting` fake mode for unit testing
- Dead letter queue management (list, retry, discard)
- Cron job management (list, register, unregister)
- Queue management (list, stats, pause, resume)
- Server health check and conformance manifest
- Optional Jackson annotations support
- Usage examples (BasicEnqueue, WorkerProcessing, WorkflowChain)
- JUnit 5 + Mockito test suite (250 tests, 83% instruction coverage)
- Integration test suite for Redis backend
- GitHub Actions CI with JaCoCo coverage reporting
- Dual Maven and Gradle build support

[Unreleased]: https://github.com/openjobspec/ojs-java-sdk/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/openjobspec/ojs-java-sdk/releases/tag/v0.1.0
