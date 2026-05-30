package io.github.androidpoet.kmpxmpp.xep0191.blocking

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

private const val BLOCKING_NAMESPACE: String = "urn:xmpp:blocking"

public interface XmppBlockingService {
    public suspend fun block(jids: List<Jid>, requestId: String): XmppResult<Unit>

    public suspend fun unblock(jids: List<Jid>, requestId: String): XmppResult<Unit>

    public suspend fun requestBlockList(requestId: String): XmppResult<Unit>
}

public class DefaultXmppBlockingService(
    private val client: KmpXmppClient,
) : XmppBlockingService {

    override suspend fun block(jids: List<Jid>, requestId: String): XmppResult<Unit> =
        sendBlockCommand(tag = "block", jids = jids, requestId = requestId)

    override suspend fun unblock(jids: List<Jid>, requestId: String): XmppResult<Unit> =
        sendBlockCommand(tag = "unblock", jids = jids, requestId = requestId)

    override suspend fun requestBlockList(requestId: String): XmppResult<Unit> {
        if (requestId.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Request id cannot be blank.", XmppErrorStage.Messaging, true))
        }
        val stanza = "<iq type='get' id='${escapeXml(requestId)}'><blocklist xmlns='$BLOCKING_NAMESPACE'/></iq>"
        return client.sendStanza(stanza)
    }

    private suspend fun sendBlockCommand(tag: String, jids: List<Jid>, requestId: String): XmppResult<Unit> {
        if (requestId.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Request id cannot be blank.", XmppErrorStage.Messaging, true))
        }
        if (jids.isEmpty()) {
            return XmppResult.Failure(xmppErrorInvalidInput("JID list cannot be empty.", XmppErrorStage.Messaging, true))
        }
        if (jids.any { it.domain.isBlank() }) {
            return XmppResult.Failure(xmppErrorInvalidInput("Every JID must include a domain.", XmppErrorStage.Messaging, false))
        }

        val items = jids.joinToString(separator = "") { "<item jid='${escapeXml(it.toString())}'/>" }
        val stanza = "<iq type='set' id='${escapeXml(requestId)}'><$tag xmlns='$BLOCKING_NAMESPACE'>$items</$tag></iq>"
        return client.sendStanza(stanza)
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
