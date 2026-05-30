package io.github.androidpoet.kmpxmpp.client

import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppError
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.flatMap
import io.github.androidpoet.kmpxmpp.stream.XmppStreamEngine
import io.github.androidpoet.kmpxmpp.stream.XmppStreamState
import io.github.androidpoet.kmpxmpp.transport.XmppTransport

public class DefaultKmpXmppClient(
    private val streamEngine: XmppStreamEngine,
    private val transport: XmppTransport,
) : KmpXmppClient {

    private var authenticatedJid: Jid? = null

    override suspend fun connect(): XmppResult<Unit> = streamEngine.start()

    override suspend fun authenticate(jid: Jid, password: String): XmppResult<Unit> {
        if (password.isEmpty()) {
            return XmppResult.Failure(XmppError("Password cannot be empty."))
        }
        if (streamEngine.state != XmppStreamState.Ready) {
            return XmppResult.Failure(XmppError("Cannot authenticate before stream is ready."))
        }

        authenticatedJid = jid
        return XmppResult.Success(Unit)
    }

    override suspend fun sendStanza(rawXml: String): XmppResult<Unit> {
        if (authenticatedJid == null) {
            return XmppResult.Failure(XmppError("Cannot send stanza before authentication."))
        }
        if (rawXml.isBlank()) {
            return XmppResult.Failure(XmppError("Stanza payload cannot be blank."))
        }

        return transport.write(rawXml)
    }

    override suspend fun disconnect(): XmppResult<Unit> =
        streamEngine.stop().flatMap {
            authenticatedJid = null
            XmppResult.Success(Unit)
        }
}
