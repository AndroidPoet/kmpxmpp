package io.github.androidpoet.kmpxmpp.im

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.flatMap
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

public interface XmppMessageService {
    public suspend fun sendChatMessage(to: Jid, body: String): XmppResult<Unit>
}

public class DefaultXmppMessageService(
    private val client: KmpXmppClient,
) : XmppMessageService {

    override suspend fun sendChatMessage(to: Jid, body: String): XmppResult<Unit> {
        if (to.domain.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Recipient JID domain cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = false,
                ),
            )
        }
        if (body.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Message body cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        return buildChatStanza(to, body).flatMap { stanza ->
            client.sendStanza(stanza)
        }
    }

    private fun buildChatStanza(to: Jid, body: String): XmppResult<String> {
        val escapedBody = escapeXml(body)
        val escapedTo = escapeXml(to.toString())
        val stanza = "<message to='$escapedTo' type='chat'><body>$escapedBody</body></message>"
        return XmppResult.Success(stanza)
    }

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
