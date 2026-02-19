# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 1.0.0 (2026-02-19)


### Features

* add JacksonSupport utility and TypedJobHandler ([b909f04](https://github.com/openjobspec/ojs-java-sdk/commit/b909f042dc4374b69e738af0f3ff3267af67c41f))
* **client:** add async operations, retry builder, and AutoCloseable ([ca9cf82](https://github.com/openjobspec/ojs-java-sdk/commit/ca9cf82ea452e53d11e0dab774f7f1584274cf82))
* **client:** add schema management API (list, register, get, delete) ([b96e342](https://github.com/openjobspec/ojs-java-sdk/commit/b96e3422981b5ead31bb7935798e5af25106ed13))
* **client:** implement OJSClient with full OJS API ([2de67f0](https://github.com/openjobspec/ojs-java-sdk/commit/2de67f0c17cdfe4c9f9a089c848a5868681edad6))
* **core:** add error hierarchy and functional interfaces ([834bcff](https://github.com/openjobspec/ojs-java-sdk/commit/834bcff57afc5e215a39ed182285be8cd2134dc9))
* **core:** add Job record with OJS state lifecycle ([b52a727](https://github.com/openjobspec/ojs-java-sdk/commit/b52a7275007a91c78b5a4925c8da2a1367137edb))
* **core:** add JobContext and fluent JobRequest builder ([f6dcebd](https://github.com/openjobspec/ojs-java-sdk/commit/f6dcebd935afa550a2cc0270c8e2ac9eeb9eb22b))
* **core:** add RetryPolicy and UniquePolicy configuration types ([1c2b35a](https://github.com/openjobspec/ojs-java-sdk/commit/1c2b35a8feb4fda60bf557274ea133bcbe7efc07))
* **events:** add Event record and EventHandler interface ([dd726cc](https://github.com/openjobspec/ojs-java-sdk/commit/dd726ccedbaf66593a50af596e47a782e84782dc))
* **events:** add thread-safe EventEmitter ([e3417ac](https://github.com/openjobspec/ojs-java-sdk/commit/e3417acdb812e3e3606a82e7a5407b13f17139e9))
* **middleware:** add logging, retry, and timeout middleware implementations ([de06d05](https://github.com/openjobspec/ojs-java-sdk/commit/de06d051b8fe3fe2bf2639de4eb7806b56ed9653))
* **middleware:** add MetricsMiddleware for job execution metrics ([42d278e](https://github.com/openjobspec/ojs-java-sdk/commit/42d278e33b9f4a6b0256f42da6686a83b794fe93))
* **middleware:** add OpenTelemetry instrumentation middleware ([cc73606](https://github.com/openjobspec/ojs-java-sdk/commit/cc73606b6f79e4a3512bd563bbec3c6ba1c10083))
* **testing:** add FakeTransport and thread-safe OJSTesting ([04470ee](https://github.com/openjobspec/ojs-java-sdk/commit/04470ee15b36a952dd8287abc460be7d1a82d75b))
* **testing:** add OJSTesting fake mode and assertion utilities ([4d2db8a](https://github.com/openjobspec/ojs-java-sdk/commit/4d2db8a4bd1ac90c3ec09c97c5bd67c4a44399b2))
* **transport:** add getAbsolute for non-versioned API endpoints ([a16bf70](https://github.com/openjobspec/ojs-java-sdk/commit/a16bf70193f2af9c1e337b0d22a2a182cf496774))
* **transport:** add RetryableTransport with exponential backoff ([d9ef844](https://github.com/openjobspec/ojs-java-sdk/commit/d9ef844999f5faef6b4fd9f2cb74c001c4bb351f))
* **transport:** implement HTTP transport with zero-dep JSON parser ([3e73cee](https://github.com/openjobspec/ojs-java-sdk/commit/3e73ceead72c5fd9f8ab53cd03d57cdbcddc7e24))
* **worker:** add structured logging with System.Logger ([a1fea42](https://github.com/openjobspec/ojs-java-sdk/commit/a1fea426fe35ee0f82987f0b8e3230219addfde6))
* **worker:** implement OJSWorker with virtual thread concurrency ([5fe6812](https://github.com/openjobspec/ojs-java-sdk/commit/5fe6812268c94d506a42f24508d8c7bb7fe8bd42))
* **workflow:** add chain, group, and batch workflow builders ([3b1325f](https://github.com/openjobspec/ojs-java-sdk/commit/3b1325fab74bd30a1a04b5bd3e157221ee58b574))


### Bug Fixes

* **client,worker:** fix manifest path, URL encoding, fetch loop, and middleware chain ([61f64d3](https://github.com/openjobspec/ojs-java-sdk/commit/61f64d35bb03e4ef16be4e53a04a39e7f30aa214))
* **middleware:** correct OpenTelemetryMiddleware return type and add tests ([5b16c08](https://github.com/openjobspec/ojs-java-sdk/commit/5b16c086e7eacd3922b8bd91ee1add24a0070dad))
* **transport:** add bounds check for unicode escape and HTTP request timeouts ([abf5156](https://github.com/openjobspec/ojs-java-sdk/commit/abf515602e53f6dfafcd476bc2e35d4d526ebcde))
* **validation:** reject blank job type in JobRequest and OJSWorker ([902fea5](https://github.com/openjobspec/ojs-java-sdk/commit/902fea5a53f0e775ae5f43c648c246a07d4ab25e))
* **worker:** use structured error payload in nack with code, retryable, and details ([6656632](https://github.com/openjobspec/ojs-java-sdk/commit/66566323f6742cc73eb188fb0039a53d5078360e))


### Documentation

* add CHANGELOG, CONTRIBUTING, CODE_OF_CONDUCT, and SECURITY ([d251ca4](https://github.com/openjobspec/ojs-java-sdk/commit/d251ca41f1ab0e4069e69d5befbae29376cdfb15))
* add usage examples and README ([1de7c94](https://github.com/openjobspec/ojs-java-sdk/commit/1de7c94d77ea1a66b24a09ee3ec1a48e4633c1e6))
* **examples:** add JBang headers and examples README ([0f104a6](https://github.com/openjobspec/ojs-java-sdk/commit/0f104a612df6517aab6e54c8d82ff2b177f6cc44))
* **examples:** add middleware usage example ([847cca9](https://github.com/openjobspec/ojs-java-sdk/commit/847cca90c6450d576f2f55f33a5aed35b0b29061))
* **github:** generalize issue templates for project-wide use ([a0e48fe](https://github.com/openjobspec/ojs-java-sdk/commit/a0e48fe929228787a15b16511d28f8b7c9411be7))
* **readme:** add CI, coverage, license, and Java badges ([7ea186e](https://github.com/openjobspec/ojs-java-sdk/commit/7ea186eaca814e39864b160813f27cf7fca46648))
* **readme:** add JSON serialization documentation ([977a701](https://github.com/openjobspec/ojs-java-sdk/commit/977a701fbea4aee8534a9a444e7a1d5c5c09c771))

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
