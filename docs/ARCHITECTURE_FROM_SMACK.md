# Architecture Notes Inspired by Smack

This project is **not** a Smack port. It is a Kotlin Multiplatform XMPP library aligned to xmpp.org specs.

## What to learn from Smack

Smack succeeds because of clear module boundaries and protocol-first layering.

- `smack-core`: shared connection/state/protocol primitives.
- `smack-tcp` and `smack-websocket`: transport adapters separated from core APIs.
- `smack-im`: higher-level IM behaviors over base stanza/session layers.
- `smack-extensions`: XEP implementations as optional modules.
- `smack-streammanagement`: stream ACK/resume as isolated reliability layer.

## KMP equivalent module plan

- `xmpp-core`: JID, stanza model, errors/results, XML element model.
- `xmpp-xml` (next): streaming XML parser/writer abstraction.
- `xmpp-sasl` (next): SASL mechanism negotiation and auth flows.
- `xmpp-stream` (next): RFC 6120 session orchestration (open/features/starttls/bind/session).
- `xmpp-client`: public facade for app developers.
- `xmpp-tcp` (next): TCP socket transport adapter.
- `xmpp-websocket` (next): RFC 7395 WebSocket transport adapter.
- `xmpp-sm` (next): XEP-0198 stream management.
- `xmpp-im` (next): RFC 6121 IM/presence APIs.
- `xmpp-xep-*` (later): optional XEP modules (MUC, disco, carbons, receipts, etc.).

## Design rules we should keep

- Keep transport concerns out of stanza/domain APIs.
- Keep each RFC/XEP feature in a focused module.
- Avoid singleton global managers; prefer instance-scoped components.
- Define protocol state machines explicitly and test them with fixture XML.
- Maintain strict backward compatibility in public API modules.

## Near-term implementation order

1. RFC 6122 JID parsing/validation hardening in `xmpp-core`.
2. RFC 6120 stream open/features parser and feature negotiation.
3. STARTTLS + SASL PLAIN flow.
4. Resource binding and session ready state.
5. Basic message/presence/IQ send-receive routing.
6. XEP-0198 ack/resume.
