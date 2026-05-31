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
  - TCP transport configuration and adapter surface (JVM-only)

- `kmpxmpp-websocket`
  - RFC 7395 WebSocket transport configuration and adapter surface (Ktor multiplatform path for Android/iOS/JVM)

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

## Quickstart (`main`)

Minimal JVM example:

```kotlin
import io.github.androidpoet.kmpxmpp.client.DefaultKmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.im.DefaultXmppMessageService
import io.github.androidpoet.kmpxmpp.stream.XmppSessionConfig
import io.github.androidpoet.kmpxmpp.stream.XmppSessionOrchestrator
import io.github.androidpoet.kmpxmpp.websocket.createWebSocketXmppTransport
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val host = "localhost"
    val port = 5280
    val jid = Jid(local = "alice", domain = host)
    val password = "strong-password"
    val peer = Jid(local = "bob", domain = host)

    val transport = createWebSocketXmppTransport(
        path = "/xmpp-websocket",
        secure = false,
    )
    val orchestrator = XmppSessionOrchestrator(
        config = XmppSessionConfig(
            host = host,
            port = port,
            tlsInitiallyActive = false,
        ),
        transport = transport,
    )
    val client = DefaultKmpXmppClient(
        streamEngine = orchestrator,
        transport = transport,
    )
    val messages = DefaultXmppMessageService(client)

    require(client.connect() is XmppResult.Success) { "connect failed" }
    require(client.authenticate(jid, password) is XmppResult.Success) { "auth failed" }
    require(messages.sendChatMessage(peer, "hello from kmpxmpp") is XmppResult.Success) { "send failed" }
    require(client.disconnect() is XmppResult.Success) { "disconnect failed" }
}
```

Run the full Docker-backed sample flow:

```bash
./gradlew :kmpxmpp-sample-whatsapp-jvm:run
```

## Production Verification

Minimum verification gates before release:

```bash
./gradlew productionVerify --no-daemon --stacktrace
```

CI workflows:
- `.github/workflows/build.yml` (JVM compile + tests)
- `.github/workflows/docker-e2e.yml` (full `productionVerify` gate including Prosody Docker interop + sample run)

Production checklist:
- `docs/PRODUCTION_READINESS.md`
- Optional strict publish-secret validation:
  - `KMPXMPP_ENFORCE_PUBLISH_SECRETS=true ./gradlew productionReleasePreflight --no-daemon --stacktrace`

## Real Server Interop (Prosody, Docker)

```bash
KMPXMPP_RUN_DOCKER_E2E=true ./gradlew :kmpxmpp-interop-tests:jvmDockerE2e
```

This starts a local Prosody container, runs JVM interop tests against it, and tears it down.

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

Core architecture and major client flows are implemented with automated JVM and Docker-backed verification.
For production adoption, keep release gates strict (JVM tests + Docker E2E + sample run) and validate against your target XMPP server policy set (TLS/auth mechanisms, MUC, upload, receipts).
Current claim boundary: production-capable baseline chat workflows, but **not** full audited OMEMO E2EE lifecycle complete yet.
This project is not full audited OMEMO E2EE lifecycle complete yet.
