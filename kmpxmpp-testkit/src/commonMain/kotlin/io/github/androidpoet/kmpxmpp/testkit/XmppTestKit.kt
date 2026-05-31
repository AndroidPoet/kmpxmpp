package io.github.androidpoet.kmpxmpp.testkit

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidState
import io.github.androidpoet.kmpxmpp.transport.XmppTransport
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public class FakeKmpXmppClient(
    private val connectResult: XmppResult<Unit> = XmppResult.Success(Unit),
    private val authenticateResult: XmppResult<Unit> = XmppResult.Success(Unit),
    private val disconnectResult: XmppResult<Unit> = XmppResult.Success(Unit),
) : KmpXmppClient {
    private val mutex = Mutex()
    private val sent = mutableListOf<String>()

    public var connectCalls: Int = 0
        private set
    public var authenticateCalls: Int = 0
        private set
    public var disconnectCalls: Int = 0
        private set
    public var lastAuthentication: Pair<Jid, String>? = null
        private set

    override suspend fun connect(): XmppResult<Unit> {
        connectCalls += 1
        return connectResult
    }

    override suspend fun authenticate(jid: Jid, password: String): XmppResult<Unit> {
        authenticateCalls += 1
        lastAuthentication = jid to password
        return authenticateResult
    }

    override suspend fun sendStanza(rawXml: String): XmppResult<Unit> {
        if (rawXml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidState(
                    message = "Fake client does not accept blank stanzas.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        mutex.withLock { sent += rawXml }
        return XmppResult.Success(Unit)
    }

    override suspend fun disconnect(): XmppResult<Unit> {
        disconnectCalls += 1
        return disconnectResult
    }

    public suspend fun sentStanzas(): List<String> = mutex.withLock { sent.toList() }
}

public class FakeXmppTransport(
    readQueue: List<XmppResult<String>> = emptyList(),
    private val connectResult: XmppResult<Unit> = XmppResult.Success(Unit),
    private val closeResult: XmppResult<Unit> = XmppResult.Success(Unit),
) : XmppTransport {
    private val mutex = Mutex()
    private val writes = mutableListOf<String>()
    private val reads = ArrayDeque(readQueue)

    public var connectCalls: Int = 0
        private set
    public var closeCalls: Int = 0
        private set
    public var lastEndpoint: Pair<String, Int>? = null
        private set

    override suspend fun connect(host: String, port: Int): XmppResult<Unit> {
        connectCalls += 1
        lastEndpoint = host to port
        return connectResult
    }

    override suspend fun write(data: String): XmppResult<Unit> {
        if (data.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidState(
                    message = "Fake transport does not accept blank writes.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        mutex.withLock { writes += data }
        return XmppResult.Success(Unit)
    }

    override suspend fun read(): XmppResult<String> = mutex.withLock {
        if (reads.isEmpty()) {
            return XmppResult.Failure(
                xmppErrorInvalidState(
                    message = "No queued fake transport read result.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        reads.removeFirst()
    }

    override suspend fun close(): XmppResult<Unit> {
        closeCalls += 1
        return closeResult
    }

    public suspend fun writtenStanzas(): List<String> = mutex.withLock { writes.toList() }

    public suspend fun enqueueRead(result: XmppResult<String>): Unit = mutex.withLock {
        reads.addLast(result)
    }
}
