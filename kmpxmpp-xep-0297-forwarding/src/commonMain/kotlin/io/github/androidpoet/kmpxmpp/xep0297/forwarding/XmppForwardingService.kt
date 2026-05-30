package io.github.androidpoet.kmpxmpp.xep0297.forwarding

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

private const val FORWARD_NAMESPACE: String = "urn:xmpp:forward:0"

public interface XmppForwardingService {
    public suspend fun forwardMessage(to: Jid, forwardedMessageXml: String): XmppResult<Unit>
}

public class DefaultXmppForwardingService(
    private val client: KmpXmppClient,
) : XmppForwardingService {

    override suspend fun forwardMessage(to: Jid, forwardedMessageXml: String): XmppResult<Unit> {
        if (to.domain.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput("Recipient JID domain cannot be blank.", XmppErrorStage.Messaging, recoverable = false),
            )
        }
        if (forwardedMessageXml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput("Forwarded message payload cannot be blank.", XmppErrorStage.Messaging, recoverable = true),
            )
        }

        val stanza = "<message to='${escapeXml(to.toString())}'><forwarded xmlns='$FORWARD_NAMESPACE'>$forwardedMessageXml</forwarded></message>"
        return client.sendStanza(stanza)
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
