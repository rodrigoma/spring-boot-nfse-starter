# Contributing to spring-boot-nfse-starter

Thank you for your interest in contributing! This document explains how to get started,
the standards we follow, and the process for submitting changes.

---

## Prerequisites

| Tool | Minimum version |
|------|----------------|
| JDK  | 21 (Temurin recommended) |
| Git  | 2.40+ |

Gradle is provided via the Gradle Wrapper (`./gradlew`); you do not need to install it separately.

---

## Cloning and building locally

```bash
git clone https://github.com/rodrigoma/spring-boot-nfse-starter.git
cd spring-boot-nfse-starter
./gradlew build
```

A successful build compiles all modules, runs all tests, checks code formatting, runs static analysis, and verifies test coverage.

---

## Running tests

Run the full test suite across all modules:

```bash
./gradlew test
```

Generate JaCoCo HTML coverage reports (output in each module's `build/reports/jacoco/test/html/`):

```bash
./gradlew jacocoTestReport
```

---

## Code style

This project uses [Ktlint](https://pinterest.github.io/ktlint/) via [Spotless](https://github.com/diffplug/spotless) to enforce consistent Kotlin formatting.

Check for formatting issues:

```bash
./gradlew spotlessCheck
```

Auto-fix all formatting issues:

```bash
./gradlew spotlessApply
```

Run [Detekt](https://detekt.dev/) static analysis:

```bash
./gradlew detekt
```

All three checks run automatically in CI. Pull requests with formatting violations or static analysis failures will not be merged.

---

## Commit message convention

This project follows [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/).

Format: `<type>(<optional scope>): <short summary>`

| Type | When to use |
|------|-------------|
| `feat` | A new feature |
| `fix` | A bug fix |
| `docs` | Documentation changes only |
| `refactor` | Code change that is neither a fix nor a feature |
| `test` | Adding or updating tests |
| `chore` | Build tooling, dependency updates, CI changes |
| `perf` | Performance improvement |

Examples:
```
feat: add support for the NFS-e substitution flow
fix: handle 401 response without rethrowing raw exception
docs: add usage example for NfseClient.cancel
chore: bump Spring Boot to 4.0.0
```

Keep the summary line under 72 characters. Add a body when the change needs explanation beyond the summary.

---

## Pull request process

1. Fork the repository and create a feature branch from `main`:
   ```bash
   git checkout -b feat/my-feature
   ```
2. Make your changes and commit using Conventional Commits format.
3. Ensure the full build passes locally:
   ```bash
   ./gradlew build jacocoTestCoverageVerification
   ```
4. Push your branch and open a pull request against `main`.
5. Fill in the PR description explaining **what** changed and **why**.
6. Address any review feedback. The PR will be merged once it has at least one approving review and all CI checks pass.

---

## Reporting bugs

Please open a [GitHub Issue](https://github.com/rodrigoma/spring-boot-nfse-starter/issues) and include:

- A clear, descriptive title.
- The version of `nfse-spring-boot-starter` you are using.
- The Spring Boot version of your application.
- A minimal reproducible example (code snippet or test case) that demonstrates the problem.
- The full stack trace or error message if applicable.

For security vulnerabilities, do **not** open a public issue. Contact the maintainer directly.
