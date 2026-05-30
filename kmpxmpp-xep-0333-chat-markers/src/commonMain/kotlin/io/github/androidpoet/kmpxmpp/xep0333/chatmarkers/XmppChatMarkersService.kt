package io.github.androidpoet.kmpxmpp.xep0333.chatmarkers

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

private const val CHAT_MARKERS_NAMESPACE: String = "urn:xmpp:chat-markers:0"

public interface XmppChatMarkersService {
    public suspend fun markReceived(to: Jid, messageId: String): XmppResult<Unit>

    public suspend fun markDisplayed(to: Jid, messageId: String): XmppResult<Unit>

    public suspend fun markAcknowledged(to: Jid, messageId: String): XmppResult<Unit>
}

public class DefaultXmppChatMarkersService(
    private val client: KmpXmppClient,
) : XmppChatMarkersService {

    override suspend fun markReceived(to: Jid, messageId: String): XmppResult<Unit> =
        sendMarker(to = to, messageId = messageId, marker = "received")

    override suspend fun markDisplayed(to: Jid, messageId: String): XmppResult<Unit> =
        sendMarker(to = to, messageId = messageId, marker = "displayed")

    override suspend fun markAcknowledged(to: Jid, messageId: String): XmppResult<Unit> =
        sendMarker(to = to, messageId = messageId, marker = "acknowledged")

    private suspend fun sendMarker(
        to: Jid,
        messageId: String,
        marker: String,
    ): XmppResult<Unit> {
        if (to.domain.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Recipient JID domain cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = false,
                ),
            )
        }
        if (messageId.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Message id cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        val stanza = "<message to='${escapeXml(to.toString())}' type='chat'><$marker xmlns='$CHAT_MARKERS_NAMESPACE' id='${escapeXml(messageId)}'/></message>"
        return client.sendStanza(stanza)
    }

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
