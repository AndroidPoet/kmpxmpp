package io.github.androidpoet.kmpxmpp.xep0085.chatstates

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.parseJidOrNull
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val CHAT_STATES_NAMESPACE: String = "http://jabber.org/protocol/chatstates"

public enum class XmppChatState(public val xmlElement: String) {
    Active("active"),
    Composing("composing"),
    Paused("paused"),
    Inactive("inactive"),
    Gone("gone"),
}

public data class ParsedChatState(
    val from: Jid?,
    val to: Jid?,
    val state: XmppChatState,
)

public interface XmppChatStateService {
    public suspend fun sendState(to: Jid, state: XmppChatState): XmppResult<Unit>

    public fun parseState(xml: String): XmppResult<ParsedChatState>
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

    override fun parseState(xml: String): XmppResult<ParsedChatState> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Chat state XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        if (tags.none { it.attributes["xmlns"] == CHAT_STATES_NAMESPACE }) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Chat state stanza missing chat states namespace.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        val state = parseStateElement(tags)
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Chat state stanza missing valid chat state element.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val messageTag = tags.firstOrNull { it.name == "message" }
        val from = messageTag?.attributes?.get("from")?.let { parseJidOrNull(it) }
        val to = messageTag?.attributes?.get("to")?.let { parseJidOrNull(it) }

        return XmppResult.Success(
            ParsedChatState(
                from = from,
                to = to,
                state = state,
            ),
        )
    }

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun parseStateElement(tags: List<io.github.androidpoet.kmpxmpp.core.XmlStartTag>): XmppChatState? {
        val values = listOf(
            XmppChatState.Active,
            XmppChatState.Composing,
            XmppChatState.Paused,
            XmppChatState.Inactive,
            XmppChatState.Gone,
        )
        for (value in values) {
            if (tags.any { it.name == value.xmlElement && it.attributes["xmlns"] == CHAT_STATES_NAMESPACE }) return value
        }
        return null
    }

}
