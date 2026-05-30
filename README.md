# KmpXMPP

A Kotlin Multiplatform XMPP library focused on RFC/XEP correctness, secure defaults, and modular architecture.

## Vision

KmpXMPP is built as a standards-first foundation for modern chat products (WhatsApp-style UX, team chat, customer messaging, IoT messaging) on top of open XMPP servers.

It targets:
- Android
- iOS
- JVM/Desktop
- macOS
- Linux
- Windows (mingw)
- Web (WASM)

## Design Principles

- Secure-by-default behavior.
- Explicit protocol state machine.
- Result-based API surface (`KmpXmppResult`) instead of leaked exceptions.
- Transport/protocol separation.
- Extension modules for optional XEP features.

## Standards Strategy

Core standards to implement first:
- RFC 6120 (XMPP Core)
- RFC 6121 (IM & Presence)
- RFC 7622 (JID)
- RFC 7590 (TLS usage in XMPP)
- RFC 7395 (XMPP over WebSocket)

Planned extension standards:
- XEP-0198 Stream Management
- XEP-0184 Delivery Receipts
- XEP-0280 Message Carbons
- XEP-0045 Multi-User Chat
- XEP-0384 OMEMO

## Module Architecture (Current Base)

- `kmpxmpp-core`
  - Result/error primitives
  - JID model
  - Shared domain types

- `kmpxmpp-security`
  - Security policy model
  - TLS and auth policy contracts

- `kmpxmpp-sasl`
  - SASL mechanism modeling and negotiation contracts

- `kmpxmpp-transport`
  - Transport abstraction (`connect/write/read/close`)

- `kmpxmpp-stream`
  - RFC 6120 state machine contracts

- `kmpxmpp-client`
  - Public client facade contracts

- `kmpxmpp-tcp`
  - TCP transport configuration and adapter surface

- `kmpxmpp-websocket`
  - RFC 7395 WebSocket transport configuration and adapter surface

## Security Defaults

- TLS is required by default.
- Plain authentication without TLS is disabled by default.
- Insecure modes must be explicit opt-in.
- Secret material must never be logged.

## Result-Based API

KmpXMPP APIs return `KmpXmppResult<T>`:
- `Success<T>(value)` for success
- `Failure(error)` for controlled failures

This keeps error handling explicit and deterministic across platforms.

## Build

```bash
./gradlew build
```

## Publish Coordinates (planned)

- `io.github.androidpoet:kmpxmpp-core`
- `io.github.androidpoet:kmpxmpp-client`
- `io.github.androidpoet:kmpxmpp-security`
- `io.github.androidpoet:kmpxmpp-sasl`
- `io.github.androidpoet:kmpxmpp-stream`
- `io.github.androidpoet:kmpxmpp-transport`
- `io.github.androidpoet:kmpxmpp-tcp`
- `io.github.androidpoet:kmpxmpp-websocket`

## Status

Base architecture scaffold is in place.
Protocol-complete implementation is intentionally not done yet.

Next implementation milestone:
1. RFC 6120 stream open/features parsing
2. STARTTLS flow
3. SASL authentication flow
4. Resource bind and ready state
