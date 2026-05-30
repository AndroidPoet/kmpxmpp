package io.github.androidpoet.kmpxmpp.xep0199.ping

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

private const val PING_NAMESPACE: String = "urn:xmpp:ping"

public interface XmppPingService {
    public suspend fun pingServer(requestId: String): XmppResult<Unit>

    public suspend fun pingEntity(to: Jid, requestId: String): XmppResult<Unit>
}

public class DefaultXmppPingService(
    private val client: KmpXmppClient,
) : XmppPingService {

    override suspend fun pingServer(requestId: String): XmppResult<Unit> {
        if (requestId.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Request id cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        val stanza = "<iq type='get' id='${escapeXml(requestId)}'><ping xmlns='$PING_NAMESPACE'/></iq>"
        return client.sendStanza(stanza)
    }

    override suspend fun pingEntity(to: Jid, requestId: String): XmppResult<Unit> {
        if (to.domain.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Target JID domain cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = false,
                ),
            )
        }
        if (requestId.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Request id cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        val stanza = "<iq type='get' to='${escapeXml(to.toString())}' id='${escapeXml(requestId)}'><ping xmlns='$PING_NAMESPACE'/></iq>"
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
