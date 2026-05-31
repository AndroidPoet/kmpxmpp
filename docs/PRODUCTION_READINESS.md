# Production Readiness Checklist

This checklist defines the minimum release gate for `kmpxmpp`.

## Current Truth (2026-05-31)

- `kmpxmpp` is production-capable for baseline XMPP chat flows when the verification gates below pass.
- `kmpxmpp` must **not** be marketed as full audited OMEMO E2EE lifecycle complete yet.
- Correct label: **Partial OMEMO lifecycle implementation, not full audited E2EE lifecycle**.
- Baseline production claim excludes deprecated/deferred/experimental XEP modules from guaranteed behavior.
- `kmpxmpp-xep-0048-bookmarks` is deprecated by upstream XSF status and should be migrated toward `XEP-0402` native bookmarks for forward-looking deployments.
- OMEMO persistence supports real SQLite-backed storage via KMP `SqlDriver`, and legacy unauthenticated payload fallback has been removed.
- Crypto lifecycle completeness still requires additional hardening work before full audited E2EE lifecycle claims.
- SASL2 / channel-binding hardening is tracked explicitly in docs/SECURITY_ROADMAP.md.

## 1) Verification Gates

- Run:
  - `./gradlew productionVerify --no-daemon --stacktrace`
- This includes:
  - JVM compilation (`compileKotlinJvm`)
  - JVM unit/integration tests (`jvmTest`)
  - Android compile verification (`compileDebugKotlinAndroid` / `compileReleaseKotlinAndroid` where available)
  - iOS compile verification (`compileKotlinIosArm64` / `compileKotlinIosSimulatorArm64` where available)
  - Dockerized Prosody interop test (`:kmpxmpp-interop-tests:jvmDockerE2e`)
  - Dockerized WhatsApp-style sample run (`productionDockerSample`)
  - Release preflight checks (`productionReleasePreflight`)

## 1.1) Strict Publish-Secret Preflight (optional, CI/release)

- Run with strict secret validation:
  - `KMPXMPP_ENFORCE_PUBLISH_SECRETS=true ./gradlew productionReleasePreflight --no-daemon --stacktrace`

## 2) CI Requirements

- `Build / JVM Verify` workflow passes.
- `Docker E2E / Prosody JVM E2E` workflow passes.
- Both workflows are configured as required checks for `main`.

## 3) Backend Compatibility

- Validate with your target XMPP backend policy:
  - TLS requirements
  - SASL mechanisms (SCRAM/PLAIN policy)
  - SASL2 + channel-binding advertisement behavior
  - MUC behavior (`conference.<domain>`)
  - Upload and OOB behavior

## 4) Release Hygiene

- Publish workflow credentials are configured in repository secrets.
- `publishAndReleaseToMavenCentral` dry-run or staging verification is successful.
- Release notes include:
  - Supported features
  - Known limitations
  - Migration notes (if API changes)

## 5) Operational Confidence

- Failure modes tested:
  - Auth failure
  - Socket disconnect/reconnect
  - Retry policy behavior
- On-call/runbook notes include:
  - How to run `productionVerify`
  - How to run/inspect Docker E2E logs
