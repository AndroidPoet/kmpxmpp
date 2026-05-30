package io.github.androidpoet.kmpxmpp.xep0359.stanzaids

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

private const val STANZA_IDS_NAMESPACE: String = "urn:xmpp:sid:0"

public interface XmppStanzaIdsService {
    public suspend fun sendOriginIdMessage(to: Jid, body: String, originId: String): XmppResult<Unit>
}

public class DefaultXmppStanzaIdsService(
    private val client: KmpXmppClient,
) : XmppStanzaIdsService {

    override suspend fun sendOriginIdMessage(to: Jid, body: String, originId: String): XmppResult<Unit> {
        if (to.domain.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput("Recipient JID domain cannot be blank.", XmppErrorStage.Messaging, recoverable = false),
            )
        }
        if (body.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput("Message body cannot be blank.", XmppErrorStage.Messaging, recoverable = true),
            )
        }
        if (originId.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput("Origin id cannot be blank.", XmppErrorStage.Messaging, recoverable = true),
            )
        }

        val stanza = "<message to='${escapeXml(to.toString())}' type='chat'><body>${escapeXml(body)}</body><origin-id xmlns='$STANZA_IDS_NAMESPACE' id='${escapeXml(originId)}'/></message>"
        return client.sendStanza(stanza)
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
