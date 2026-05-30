# KmpXMPP Implementation Audit

Last updated: 2026-05-31

## Executive Status
- Overall completion (engineering estimate): **~68%**
- Core scaffolding and module layout: **done**
- JVM transport + smoke interop: **partially done**
- Production-grade protocol correctness and security hardening: **not done**
- Real Docker server E2E (Prosody/Openfire/ejabberd): **not done**

## Verified Done
- `XmppResult` pattern implemented across modules.
- XEP module directories + service implementations added.
- JVM TCP transport implemented.
- JVM WebSocket transport implemented.
- Stream feature bootstrap now uses real transport I/O.
- JVM smoke interop test exists (`kmpxmpp-interop-tests`) and passes.
- Current targeted JVM tests pass:
  - `:kmpxmpp-client:jvmTest`
  - `:kmpxmpp-stream:jvmTest`
  - `:kmpxmpp-interop-tests:jvmTest`

## Critical Gaps (Must Fix)
1. **SASL wire flow incomplete**
- `SCRAM-SHA-256` and `SCRAM-SHA-1` are not implemented on wire.
- Evidence: `kmpxmpp-client/src/commonMain/.../DefaultKmpXmppClient.kt` throws `SASL <mechanism> wire exchange is not implemented yet.`

2. **XML parsing is still string-matching**
- Auth/bind success checks rely on `contains(...)` instead of strict stanza parsing.
- Risk: malformed/edge-case stanzas can be accepted or rejected incorrectly.

3. **No real XMPP server integration test in Docker yet**
- Current interop test uses a local fake socket server, not Prosody/Openfire/ejabberd.

4. **TLS/STARTTLS lifecycle not proven E2E**
- Policy checks exist, but full upgrade + certificate validation path is not verified against real server.

5. **Many XEP modules are API-level only**
- Module surface exists, but wire-level behavior, state machines, and compliance coverage are incomplete.

## Security Audit Status
- Good:
  - `PLAIN` without TLS is blocked by policy path.
  - Mechanism selection checks exist.
- Missing for “secure by default” claim:
  - Full SCRAM handshake implementation.
  - STARTTLS upgrade enforcement in real network flow.
  - Certificate pinning / trust customization tests.
  - Replay/downgrade/invalid-stanza negative tests.

## E2E Test Audit
- Passing now: mocked/local-socket JVM tests.
- Missing for real confidence:
  1. Dockerized Prosody bring-up in test harness.
  2. Automated account provisioning.
  3. Two-client login + message exchange assertion.
  4. Receipt/carbon flows over real server.
  5. CI profile to run Docker E2E separately from unit tests.

## Phase Plan To Reach “Complete”

### Phase 1: Auth Correctness (highest priority)
- Implement SCRAM-SHA-256 wire exchange.
- Implement SCRAM-SHA-1 wire exchange.
- Replace auth/bind string checks with strict stanza parsing.
- Add negative tests (failure/challenge/malformed/unexpected order).

### Phase 2: Real Docker E2E
- Add `docker-compose` for Prosody.
- Add scripts for user registration and health waits.
- Add JVM E2E tests (alice <-> bob real chat).
- Add STARTTLS-required variant.

### Phase 3: XEP Production Hardening
- Prioritize `MUC`, `Receipts`, `Carbons`, `MAM`, `Ping`.
- Add stanza-level conformance tests per prioritized XEP.
- Add interoperability matrix notes.

### Phase 4: Platform/CI Completion
- Run JVM + Docker E2E in CI.
- Add Android/iOS/macOS target smoke coverage.
- Add release readiness checklist.

## Concrete “How Much Left”
- Remaining high-risk core work: **~4–6 days** focused engineering time.
- Remaining full-library hardening/compliance work: **~2–4 weeks** depending on XEP depth and platform targets.

## Definition of Done (strict)
Library is “complete” only when all are true:
- SCRAM + PLAIN + TLS paths implemented and tested against real server.
- Docker E2E chat tests pass reliably in CI.
- Prioritized XEPs have real wire behavior + tests.
- No `not implemented` in authentication/transport critical path.
- Security defaults are enforced and documented.
