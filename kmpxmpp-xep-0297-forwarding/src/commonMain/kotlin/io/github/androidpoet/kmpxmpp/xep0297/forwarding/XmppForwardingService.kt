package io.github.androidpoet.kmpxmpp.xep0297.forwarding

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val FORWARD_NAMESPACE: String = "urn:xmpp:forward:0"

public data class ParsedForwardedMessage(
    val messageXml: String,
)

public interface XmppForwardingService {
    public suspend fun forwardMessage(to: Jid, forwardedMessageXml: String): XmppResult<Unit>

    public fun parseForwardedMessage(xml: String): XmppResult<ParsedForwardedMessage>

    public fun validateForwardedEnvelope(xml: String): XmppResult<Unit>
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

    override fun parseForwardedMessage(xml: String): XmppResult<ParsedForwardedMessage> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Forwarded payload XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val startTags = XmppXmlMiniParser.parseStartTags(xml)
        val forwardedTag = startTags.firstOrNull { tag ->
            tag.name == "forwarded" && tag.attributes["xmlns"] == FORWARD_NAMESPACE
        } ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Payload missing <forwarded xmlns='urn:xmpp:forward:0'>.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val messageBody = (XmppXmlMiniParser.tagInnerXml(xml, forwardedTag) ?: "").trim()
        val hasMessage = XmppXmlMiniParser.parseStartTags(messageBody).any { it.name == "message" }
        if (!hasMessage) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Forwarded payload must contain <message>...</message>.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        return XmppResult.Success(ParsedForwardedMessage(messageXml = messageBody))
    }

    override fun validateForwardedEnvelope(xml: String): XmppResult<Unit> =
        when (val parsed = parseForwardedMessage(xml)) {
            is XmppResult.Success -> XmppResult.Success(Unit)
            is XmppResult.Failure -> parsed
        }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
