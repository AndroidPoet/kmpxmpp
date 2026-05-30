package io.github.androidpoet.kmpxmpp.xep0085.chatstates

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

private const val CHAT_STATES_NAMESPACE: String = "http://jabber.org/protocol/chatstates"

public enum class XmppChatState(public val xmlElement: String) {
    Active("active"),
    Composing("composing"),
    Paused("paused"),
    Inactive("inactive"),
    Gone("gone"),
}

public interface XmppChatStateService {
    public suspend fun sendState(to: Jid, state: XmppChatState): XmppResult<Unit>
}

public class DefaultXmppChatStateService(
    private val client: KmpXmppClient,
) : XmppChatStateService {

    override suspend fun sendState(to: Jid, state: XmppChatState): XmppResult<Unit> {
        if (to.domain.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Recipient JID domain cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = false,
                ),
            )
        }

        val stanza = "<message to='${escapeXml(to.toString())}' type='chat'><${state.xmlElement} xmlns='$CHAT_STATES_NAMESPACE'/></message>"
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
