# WhatsApp-Like Chat Sample (JVM)

This module provides a runnable end-to-end chat flow sample on top of `kmpxmpp` using a Dockerized XMPP backend (Prosody).

## What it demonstrates

- Connect + authenticate `alice@localhost` and `bob@localhost`
- Presence updates
- 1:1 chat messages
- Chat state (`composing`)
- Delivery receipts (XEP-0184)
- Chat markers (XEP-0333)
- OOB media link (XEP-0066)
- Upload slot request (XEP-0363)
- Direct MUC invite + basic group send (XEP-0249 / XEP-0045)

## Run with Docker backend

From repo root:

```bash
bash kmpxmpp-sample-whatsapp-jvm/scripts/run-whatsapp-docker-sample.sh
```

The script:
1. Starts Prosody in Docker
2. Registers test users (`alice`, `bob`)
3. Runs `:kmpxmpp-sample-whatsapp-jvm:run`
4. Shuts down Docker stack

## Run sample only (backend already running)

```bash
./gradlew :kmpxmpp-sample-whatsapp-jvm:run --no-daemon
```

## Run as two chat clients (two terminals)

Start backend first:

```bash
bash kmpxmpp-interop-tests/scripts/start-prosody-e2e.sh
```

Terminal 1:

```bash
CHAT_USER=alice CHAT_PEER=bob ./gradlew :kmpxmpp-sample-whatsapp-jvm:run --no-daemon
```

Terminal 2:

```bash
CHAT_USER=bob CHAT_PEER=alice ./gradlew :kmpxmpp-sample-whatsapp-jvm:run --no-daemon
```

Type messages in each terminal. Use `/quit` to exit.

Stop backend:

```bash
bash kmpxmpp-interop-tests/scripts/stop-prosody-e2e.sh
```

For scripted smoke runs (non-interactive CI/local):

```bash
CHAT_USER=alice CHAT_PEER=bob CHAT_AUTO_SEND='hello|ping' CHAT_KEEP_ALIVE_SEC=3 ./gradlew :kmpxmpp-sample-whatsapp-jvm:run --no-daemon
```
