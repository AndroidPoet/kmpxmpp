package io.github.androidpoet.kmpxmpp.sample.whatsapp

import io.github.androidpoet.kmpxmpp.client.DefaultKmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.im.DefaultXmppMessageService
import io.github.androidpoet.kmpxmpp.im.DefaultXmppPresenceService
import io.github.androidpoet.kmpxmpp.stream.XmppSessionConfig
import io.github.androidpoet.kmpxmpp.stream.XmppSessionOrchestrator
import io.github.androidpoet.kmpxmpp.tcp.createTcpXmppTransport
import io.github.androidpoet.kmpxmpp.transport.XmppTransport
import io.github.androidpoet.kmpxmpp.xep0045.muc.DefaultXmppMucService
import io.github.androidpoet.kmpxmpp.xep0066.oob.DefaultXmppOobService
import io.github.androidpoet.kmpxmpp.xep0085.chatstates.DefaultXmppChatStateService
import io.github.androidpoet.kmpxmpp.xep0085.chatstates.XmppChatState
import io.github.androidpoet.kmpxmpp.xep0184.receipts.DefaultXmppReceiptService
import io.github.androidpoet.kmpxmpp.xep0249.directmucinvite.DefaultXmppDirectMucInviteService
import io.github.androidpoet.kmpxmpp.xep0333.chatmarkers.DefaultXmppChatMarkersService
import io.github.androidpoet.kmpxmpp.xep0363.httpupload.DefaultXmppHttpUploadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val HOST: String = "localhost"
private const val PORT: Int = 5222
private const val PASSWORD: String = "strong-password"

private data class Session(
    val jid: Jid,
    val transport: XmppTransport,
    val client: DefaultKmpXmppClient,
    val messageService: DefaultXmppMessageService,
    val presenceService: DefaultXmppPresenceService,
    val chatStateService: DefaultXmppChatStateService,
    val receiptService: DefaultXmppReceiptService,
    val markerService: DefaultXmppChatMarkersService,
    val oobService: DefaultXmppOobService,
    val directInviteService: DefaultXmppDirectMucInviteService,
    val mucService: DefaultXmppMucService,
    val uploadService: DefaultXmppHttpUploadService,
)

fun main(): Unit = runBlocking {
    val userArg = System.getenv("CHAT_USER") ?: ""
    val peerArg = System.getenv("CHAT_PEER") ?: ""
    if (userArg.isNotBlank() && peerArg.isNotBlank()) {
        runInteractiveChat(local = userArg, peerLocal = peerArg)
        return@runBlocking
    }

    println("Starting WhatsApp-like sample against XMPP backend at $HOST:$PORT")

    val alice = createSession(local = "alice")
    val bob = createSession(local = "bob")

    runOrThrow("alice connect", alice.client.connect())
    runOrThrow("bob connect", bob.client.connect())
    runOrThrow("alice auth", alice.client.authenticate(alice.jid, PASSWORD))
    runOrThrow("bob auth", bob.client.authenticate(bob.jid, PASSWORD))

    runOrThrow("alice online", alice.presenceService.sendAvailable("Available"))
    runOrThrow("bob online", bob.presenceService.sendAvailable("Online"))

    val msgId = "msg-001"
    runOrThrow("alice typing", alice.chatStateService.sendState(bob.jid, XmppChatState.Composing))
    runOrThrow("alice chat message", alice.messageService.sendChatMessage(bob.jid, "Hey Bob, let's meet at 7?"))
    runOrThrow("alice receipt request", alice.receiptService.sendMessageWithReceiptRequest(bob.jid, "Please confirm receipt", msgId))
    runOrThrow("bob receipt", bob.receiptService.sendReceivedReceipt(alice.jid, msgId))
    runOrThrow("bob displayed marker", bob.markerService.markDisplayed(alice.jid, msgId))

    runOrThrow(
        "alice send media link",
        alice.oobService.sendOobUrl(
            to = bob.jid,
            url = "https://cdn.example.com/media/photo.jpg",
            description = "Trip photo",
        ),
    )

    runOrThrow(
        "alice request upload slot",
        alice.uploadService.requestUploadSlot(
            uploadService = Jid(local = null, domain = HOST),
            fileName = "photo.jpg",
            sizeBytes = 1024,
            contentType = "image/jpeg",
            requestId = "upload-1",
        ),
    )

    val room = Jid(local = "friends", domain = "conference.$HOST")
    runOrThrow("alice direct room invite", alice.directInviteService.invite(to = bob.jid, room = room, reason = "Group planning"))
    runOrThrow("alice join room", alice.mucService.joinRoom(room = room, nickname = "alice"))
    runOrThrow("alice group message", alice.mucService.sendGroupMessage(room = room, body = "Group message test"))
    runOrThrow("alice leave room", alice.mucService.leaveRoom(room = room, nickname = "alice"))

    runOrThrow("alice offline", alice.presenceService.sendUnavailable("Bye"))
    runOrThrow("bob offline", bob.presenceService.sendUnavailable("Bye"))
    runOrThrow("alice disconnect", alice.client.disconnect())
    runOrThrow("bob disconnect", bob.client.disconnect())

    println("Sample completed successfully.")
}

private suspend fun runInteractiveChat(local: String, peerLocal: String) {
    val self = createSession(local = local)
    val peer = Jid(local = peerLocal, domain = HOST)
    runOrThrow("$local connect", self.client.connect())
    runOrThrow("$local auth", self.client.authenticate(self.jid, PASSWORD))
    runOrThrow("$local online", self.presenceService.sendAvailable("Online"))

    println("[$local] connected. Type messages to send to ${peer.asBareString()}. Type /quit to exit.")
    val autoMessages = (System.getenv("CHAT_AUTO_SEND") ?: "")
        .split("|")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val keepAliveSeconds = (System.getenv("CHAT_KEEP_ALIVE_SEC") ?: "20").toLongOrNull() ?: 20L
    coroutineScope {
        val readerJob = startIncomingPrinter(local = local, transport = self.transport)
        try {
            if (autoMessages.isNotEmpty()) {
                for (message in autoMessages) {
                    runOrThrow("$local send", self.messageService.sendChatMessage(peer, message))
                    delay(400)
                }
                delay(keepAliveSeconds * 1000L)
                return@coroutineScope
            }
            while (true) {
                val line = readlnOrNull() ?: "/quit"
                if (line == "/quit") break
                if (line.isBlank()) continue
                runOrThrow(
                    "$local send",
                    self.messageService.sendChatMessage(peer, line),
                )
            }
        } finally {
            readerJob.cancelAndJoin()
            runOrThrow("$local offline", self.presenceService.sendUnavailable("Bye"))
            runOrThrow("$local disconnect", self.client.disconnect())
        }
    }
}

private fun CoroutineScope.startIncomingPrinter(local: String, transport: XmppTransport): Job =
    launch(Dispatchers.IO) {
        while (isActive) {
            when (val incoming = transport.read()) {
                is XmppResult.Success -> {
                    val raw = incoming.value
                    val from = extractAttr(raw, "from") ?: continue
                    val body = extractBody(raw) ?: continue
                    println()
                    println("[$local] <$from> $body")
                }
                is XmppResult.Failure -> {
                    if (isActive) {
                        println("[$local] receive error: ${incoming.error.message}")
                    }
                    break
                }
            }
        }
    }

private fun createSession(local: String): Session {
    val transport = createTcpXmppTransport()
    val orchestrator = XmppSessionOrchestrator(
        config = XmppSessionConfig(
            host = HOST,
            port = PORT,
            tlsInitiallyActive = false,
        ),
        transport = transport,
    )
    val client = DefaultKmpXmppClient(
        streamEngine = orchestrator,
        transport = transport,
    )
    val jid = Jid(local = local, domain = HOST)
    return Session(
        jid = jid,
        transport = transport,
        client = client,
        messageService = DefaultXmppMessageService(client),
        presenceService = DefaultXmppPresenceService(client),
        chatStateService = DefaultXmppChatStateService(client),
        receiptService = DefaultXmppReceiptService(client),
        markerService = DefaultXmppChatMarkersService(client),
        oobService = DefaultXmppOobService(client),
        directInviteService = DefaultXmppDirectMucInviteService(client),
        mucService = DefaultXmppMucService(client),
        uploadService = DefaultXmppHttpUploadService(client),
    )
}

private fun extractBody(xml: String): String? {
    val match = Regex("<body>([\\s\\S]*?)</body>", RegexOption.IGNORE_CASE).find(xml)
    return match?.groupValues?.get(1)?.trim().takeUnless { it.isNullOrBlank() }
}

private fun extractAttr(xml: String, attr: String): String? {
    val regex = Regex("$attr\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
    return regex.find(xml)?.groupValues?.get(1)
}

private fun Jid.asBareString(): String = if (local == null) domain else "$local@$domain"

private fun runOrThrow(step: String, result: XmppResult<Unit>) {
    when (result) {
        is XmppResult.Success -> println("OK: $step")
        is XmppResult.Failure -> error("FAILED: $step -> ${result.error.message}")
    }
}
