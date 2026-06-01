# KmpXMPP Implementation Audit

Last updated: 2026-06-01

## Executive Summary

- Architecture direction: **good**
- Production posture: **partially ready**
- Largest remaining risk: **cryptographic correctness scope vs. full OMEMO claim**
- Current estimate:
  - Core SDK maturity: **~86%**
  - Strict production-complete maturity: **~72%**

## What Is Good (Keep)

1. Modular KMP layout is strong (`kmpxmpp-core`, `-client`, `-stream`, `-transport`, per-XEP modules).
2. Result/error model is consistent (`XmppResult` + typed error stages).
3. Docker-backed real chat validation exists in downstream app gate flow.
4. Security baseline checks exist (mechanism policy + TLS-aware auth constraints).
5. OMEMO persistence moved to real SQLite-backed `SqlDriver` repository (no map-only path).
6. OMEMO legacy unauthenticated payload compatibility fallback removed; decrypt now enforces authenticated `v1` payload format only.
7. OMEMO key lifecycle hardening improved:
   - Strict base64 key-material validation for identity/pre/signed/root/chain keys.
   - Session key rotation derivation moved from XOR mixing to HMAC-SHA256 context-bound derivation.
   - Authenticated per-message index bound into AAD to harden ordering/replay semantics within session lifecycle.
8. OMEMO lifecycle state persistence now includes established-at time, operation count, send index, and highest received index in SQLite-backed repository state, with restart-safety tests proving replay/order guards survive service recreation.
9. OMEMO payload authentication now binds chain-key material into message authentication-key derivation, preventing stale-chain decrypt attempts from passing integrity checks.
10. BOM is not part of active module graph (no `:kmpxmpp-bom*` include in `settings.gradle.kts`), so installs are per-module.

## What Is Weak (Fix)

1. **OMEMO claim gap (release-claim blocker)**
   - Current lifecycle logic is hardened but still not equivalent to a fully audited Signal Double Ratchet implementation.
   - Full E2EE claim remains blocked until formal lifecycle assurance (trust transitions, recovery guarantees, interop vectors, and external audit evidence) is complete.

2. **String-based XML construction/parsing still present in multiple XEP modules**
   - Auth/bind parser path has been hardened to structured start-tag parsing for SASL success and bind result checks.
   - Shared JID parsing has been centralized in `kmpxmpp-core` and adopted by multiple messaging modules to reduce parser drift.
   - Shared strict parser primitives are now adopted across core client auth parsing and prioritized XEP modules.
   - Remaining parser hardening is incremental quality work, not a current baseline release blocker. **Status: non-blocking/in progress**.

3. **Spec-currency and interop breadth**
   - Compliance messaging must track current suite lineage (CS2023 / XEP-0479) instead of older suite assumptions.
   - SASL2 / channel-binding hardening track should be explicit in roadmap.
   - Status: roadmap + preflight checks exist, but backend coverage breadth is still limited.

4. **Cross-platform runtime validation depth**
   - JVM and Docker interop are strong.
   - Android/iOS currently have compile verification but still need deeper runtime/integration matrix coverage for production confidence.
   - Status: non-blocking for alpha; blocking for stronger production claim.

## P0 / P1 / P2 Plan

### P0 (claim blocking)
- Keep production gate strict on OMEMO readiness wording and verdict semantics.
- Keep explicit “not full OMEMO crypto lifecycle” language in public docs and release notes.

### P1 (near-term hardening)
- Complete OMEMO lifecycle to ratchet-grade assurance with clear trust/recovery semantics and stronger vector tests.
- Expand strict parser migration for non-auth XEP modules that still use string-heavy handling.
- Expand interop matrix beyond single Prosody Docker path.

### P2 (compliance breadth)
- Expand conformance coverage across prioritized XEPs (MAM, MUC, Carbons, Receipts, Ping).
- Expand Android/iOS integration test coverage for persistence + auth+chat flows.

## Pending Right Now (Actionable)

1. OMEMO full lifecycle claim cannot be raised yet.
2. Android/iOS runtime integration coverage is lighter than JVM/Docker coverage.
3. Some XEP modules still use string-based stanza construction/parsing and should move further to strict parser primitives.
4. Multi-backend interop breadth (beyond Prosody-only primary path) is still pending.

## Fixed Since Last Audit

1. SQLite-backed OMEMO persistence is in place (`SqlDriver`), replacing map-only persistence path.
2. Legacy unauthenticated OMEMO payload fallback removed (`v1` authenticated payload required).
3. Key lifecycle hardening added (base64 validation, HMAC-based rotation derivation, AAD message index binding).
4. Replay/order durability survives restart via persisted lifecycle counters + tests.
5. BOM is no longer part of active project module graph; install flow is per-module.

## Production Verdict (Today)

- Can be open-sourced now: **Yes**.
- Can be called fully production-ready secure E2EE stack: **No (not yet)**.
- Correct label today:
  - **Production-capable for baseline chat workflows with strict gate controls**
  - **Partial OMEMO lifecycle implementation, not full audited E2EE lifecycle**

## Remaining Blocker Summary

Single claim-level blocker remains for "fully production-ready secure E2EE complete":

- Formal cryptographic completeness and audit-grade assurance for full Signal/Double-Ratchet-equivalent OMEMO lifecycle behavior (including broader interop/certification evidence), beyond current hardened lifecycle implementation.
