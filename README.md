![KmpXMPP](docs/images/kmpxmpp-header.png)

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.2.0-blue.svg?logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/Ktor-3.2.2-blue.svg" alt="Ktor">
  <img src="https://img.shields.io/badge/Platform-Android%20%7C%20iOS%20%7C%20JVM%20%7C%20macOS%20%7C%20Linux%20%7C%20Windows%20%7C%20WasmJs-green.svg" alt="Platforms">
  <img src="https://img.shields.io/github/actions/workflow/status/AndroidPoet/kmpxmpp/build.yml?branch=main&label=Build" alt="Build">
  <img src="https://img.shields.io/github/actions/workflow/status/AndroidPoet/kmpxmpp/publish.yml?branch=main&label=Publish" alt="Publish">
  <img src="https://img.shields.io/badge/Maven%20Central-0.1.0--alpha01-blue.svg" alt="Maven Central">
  <img src="https://img.shields.io/badge/License-MIT-orange.svg" alt="License">
</p>

# KmpXMPP

Kotlin Multiplatform XMPP SDK for Android, iOS, and JVM/Desktop, with modular RFC/XEP support and Docker-backed interop tests.

## Install (No BOM)

KmpXMPP is published as separate modules. Add only what you use.

```kotlin
dependencies {
    implementation("io.github.androidpoet:kmpxmpp-core:0.1.0-alpha01")
    implementation("io.github.androidpoet:kmpxmpp-client:0.1.0-alpha01")
    implementation("io.github.androidpoet:kmpxmpp-im:0.1.0-alpha01")
    implementation("io.github.androidpoet:kmpxmpp-websocket:0.1.0-alpha01")

    // Optional XEPs:
    implementation("io.github.androidpoet:kmpxmpp-xep-0184-receipts:0.1.0-alpha01")
    implementation("io.github.androidpoet:kmpxmpp-xep-0333-chat-markers:0.1.0-alpha01")
    implementation("io.github.androidpoet:kmpxmpp-xep-0085-chat-states:0.1.0-alpha01")
}
```

## Module Structure

- Core/runtime: `kmpxmpp-core`, `kmpxmpp-client`, `kmpxmpp-stream`, `kmpxmpp-transport`, `kmpxmpp-xml`
- Security/auth: `kmpxmpp-security`, `kmpxmpp-sasl`, `kmpxmpp-sm`, `kmpxmpp-reconnect`
- Transports: `kmpxmpp-websocket` (KMP/Ktor), `kmpxmpp-tcp` (JVM TCP adapter)
- IM/features: `kmpxmpp-im`, `kmpxmpp-xep-*` extension modules
- OMEMO track: `kmpxmpp-omemo-core`, `kmpxmpp-omemo-persistence-sqlite`, `kmpxmpp-xep-0384-omemo`
- Testing/sample: `kmpxmpp-interop-tests`, `kmpxmpp-sample-whatsapp-jvm`

## Quick Start

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
    val me = Jid(local = "alice", domain = host)
    val peer = Jid(local = "bob", domain = host)

    val transport = createWebSocketXmppTransport(path = "/xmpp-websocket", secure = false)
    val orchestrator = XmppSessionOrchestrator(
        config = XmppSessionConfig(host = host, port = port, tlsInitiallyActive = false),
        transport = transport,
    )
    val client = DefaultKmpXmppClient(streamEngine = orchestrator, transport = transport)
    val chat = DefaultXmppMessageService(client)

    require(client.connect() is XmppResult.Success)
    require(client.authenticate(me, "strong-password") is XmppResult.Success)
    require(chat.sendChatMessage(peer, "hello from kmpxmpp") is XmppResult.Success)
    require(client.disconnect() is XmppResult.Success)
}
```

## Run + Verify

Build:

```bash
./gradlew build
```

Docker-backed interop:

```bash
KMPXMPP_RUN_DOCKER_E2E=true ./gradlew :kmpxmpp-interop-tests:jvmDockerE2e
```

Full release gate:

```bash
./gradlew productionVerify --no-daemon --stacktrace
```

Sample WhatsApp-style desktop flow:

```bash
./gradlew :kmpxmpp-sample-whatsapp-jvm:run
```

## Platform Notes

- Android/iOS should use `kmpxmpp-websocket` (Ktor-based KMP transport).
- `kmpxmpp-tcp` is a JVM transport adapter and not required for mobile-only apps.
- TLS is required by default; plain auth without TLS is blocked unless explicitly enabled.

## Typed Feature Policy

`kmpxmpp-core` includes typed feature IDs and policy gates:

- `XmppFeatureId`
- `XmppFeaturePolicy` (`StableOnly`, `AllowExperimental`, `AllowDeferred`, `AllowDeprecated`, `AllowAll`)
- `XmppCapabilityRegistry`
- `XmppFeatureCatalog`

Use `StableOnly` in production to avoid accidental activation of non-stable modules.

## Production Readiness (Current Boundary)

- Ready baseline: core chat flows, presence, receipts/markers/states, Docker interop verification.
- Not yet complete: fully audited OMEMO end-to-end crypto lifecycle/trust/session backup and restore claim.
- Recommendation: publish as `alpha`, keep OMEMO marked as evolving, and keep `productionVerify` required in CI.

Detailed checklist: `docs/PRODUCTION_READINESS.md`.
