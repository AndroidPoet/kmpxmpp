# Security Roadmap (Spec Currency)

Last updated: 2026-05-31

This roadmap tracks security/spec-currency work required for production claims.

## Current Baseline

- Compliance profiles aligned to:
  - CS2023 core lineage
  - XEP-0479 self-labeling profile
- SASL policy includes mechanism controls and channel-binding policy checks.

## SASL2 / Channel-Binding Hardening Track

1. **SASL2-first negotiation hardening**
   - Prefer SASL2 where advertised and policy-allowed.
   - Keep explicit failure modes when SASL2 is advertised without required channel-binding types.

2. **Channel-binding enforcement profile**
   - Default policy remains strict for SASL2 channel-binding advertisement.
   - Add backend-interop matrix coverage for mixed server capabilities (SASL1-only, SASL2-no-CB, SASL2-with-CB).

3. **Mechanism/profile observability**
   - Capture selected SASL profile/mechanism outcome in integration test assertions.
   - Expand CI evidence to include at least one channel-binding-positive and one policy-rejection case.

4. **Release-claim guardrail**
   - Production readiness docs must keep explicit statement that OMEMO lifecycle is not yet fully audited ratchet-grade E2EE complete.
   - Security roadmap and readiness checklist must both exist and be kept in sync.

## Not Yet Claimed

- Full audited Signal/Double-Ratchet-equivalent OMEMO lifecycle completeness.
- Broad multi-server formal interop certification for all SASL2/channel-binding combinations.
