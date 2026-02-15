# Contributing to OJS Java SDK

Thank you for considering contributing to the OJS Java SDK! This document provides guidelines and information to help you get started.

## Getting Started

### Prerequisites

- Java 21 or later
- Maven 3.9+ or Gradle 8+

### Building

```bash
# Maven
mvn clean verify

# Gradle
gradle build
```

### Running Tests

```bash
# Unit tests only
mvn test

# Unit + integration tests (requires running OJS server)
mvn verify -Dit.test=RedisBackendIT
```

### Local OJS Server (for integration tests)

Start a Redis-backed OJS server with Docker Compose:

```bash
docker compose up -d
# Server available at http://localhost:8080
# Stop with: docker compose down
```

## How to Contribute

### Reporting Bugs

- Use the [GitHub Issues](https://github.com/openjobspec/ojs-java-sdk/issues) page
- Check existing issues before creating a new one
- Include steps to reproduce, expected behavior, and actual behavior
- Include your Java version and OS

### Suggesting Features

- Open a [GitHub Issue](https://github.com/openjobspec/ojs-java-sdk/issues) with the "enhancement" label
- Describe the use case and expected behavior
- If possible, reference the relevant [OJS specification](https://openjobspec.org)

### Submitting Changes

1. Fork the repository
2. Create a feature branch from `main` (`git checkout -b feature/my-feature`)
3. Make your changes
4. Ensure all tests pass (`mvn clean verify`)
5. Commit with a descriptive message following [Conventional Commits](https://www.conventionalcommits.org/)
6. Push to your fork and open a Pull Request

### Commit Messages

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(client): add batch cancellation support
fix(worker): prevent semaphore leak on transport errors
test: add edge case tests for JSON parser
docs: update README with workflow examples
chore(build): bump JUnit to 5.11
```

## Code Style

- Follow existing code conventions in the project
- Use Java 21 features where appropriate (records, sealed interfaces, pattern matching)
- Keep the zero-runtime-dependency promise — new dependencies must be `optional` or `test`-scoped
- Add Javadoc to all public classes, methods, and interfaces
- Use `@FunctionalInterface` for single-method interfaces

## Testing

- Write unit tests for all new functionality
- Use Mockito for transport-layer mocking
- Use `@Nested` classes to organize related tests
- Use `@DisplayName` for descriptive test names
- Aim for meaningful coverage, not just high percentages

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
