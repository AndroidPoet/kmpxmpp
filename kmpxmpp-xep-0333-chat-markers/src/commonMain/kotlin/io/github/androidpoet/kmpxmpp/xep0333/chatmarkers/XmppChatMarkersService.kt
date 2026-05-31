package io.github.androidpoet.kmpxmpp.xep0333.chatmarkers

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.parseJidOrNull
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val CHAT_MARKERS_NAMESPACE: String = "urn:xmpp:chat-markers:0"

public enum class ChatMarkerType {
    Received,
    Displayed,
    Acknowledged,
}

public data class ParsedChatMarker(
    val type: ChatMarkerType,
    val messageId: String,
    val from: Jid?,
)

public interface XmppChatMarkersService {
    public suspend fun markReceived(to: Jid, messageId: String): XmppResult<Unit>

    public suspend fun markDisplayed(to: Jid, messageId: String): XmppResult<Unit>

    public suspend fun markAcknowledged(to: Jid, messageId: String): XmppResult<Unit>

    public fun parseChatMarker(xml: String): XmppResult<ParsedChatMarker>
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

    override fun parseChatMarker(xml: String): XmppResult<ParsedChatMarker> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Chat marker XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        if (tags.none { it.attributes["xmlns"] == CHAT_MARKERS_NAMESPACE }) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Chat marker stanza missing urn:xmpp:chat-markers:0 namespace.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        val typeAndTag = markerTag(tags)
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Chat marker stanza must contain received/displayed/acknowledged marker.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val markerId = typeAndTag.second.attributes["id"]
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Chat marker element missing id attribute.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val messageTag = tags.firstOrNull { it.name == "message" }
        val from = messageTag?.attributes?.get("from")?.let { parseJidOrNull(it) }
        return XmppResult.Success(
            ParsedChatMarker(
                type = typeAndTag.first,
                messageId = markerId,
                from = from,
            ),
        )
    }

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

    private fun markerTag(tags: List<io.github.androidpoet.kmpxmpp.core.XmlStartTag>): Pair<ChatMarkerType, io.github.androidpoet.kmpxmpp.core.XmlStartTag>? {
        val mappings = listOf(
            ChatMarkerType.Received to "received",
            ChatMarkerType.Displayed to "displayed",
            ChatMarkerType.Acknowledged to "acknowledged",
        )
        for ((type, name) in mappings) {
            val match = tags.firstOrNull { tag -> tag.name == name && tag.attributes["xmlns"] == CHAT_MARKERS_NAMESPACE }
            if (match != null) return type to match
        }
        return null
    }

}
