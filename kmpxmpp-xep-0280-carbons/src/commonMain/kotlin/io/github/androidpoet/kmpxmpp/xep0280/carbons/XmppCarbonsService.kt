package io.github.androidpoet.kmpxmpp.xep0280.carbons

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.parseJidOrNull
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val CARBONS_NAMESPACE: String = "urn:xmpp:carbons:2"
private const val FORWARD_NAMESPACE: String = "urn:xmpp:forward:0"

public enum class CarbonDirection {
    Sent,
    Received,
}

public data class XmppCarbonMessage(
    val direction: CarbonDirection,
    val from: Jid?,
    val to: Jid?,
    val body: String?,
)

public interface XmppCarbonsService {
    public suspend fun enableCarbons(): XmppResult<Unit>

    public suspend fun disableCarbons(): XmppResult<Unit>

    public fun parseForwardedCarbon(xml: String): XmppResult<XmppCarbonMessage>

    public fun validateCarbonsIqResult(xml: String, requestId: String): XmppResult<Unit>
}

public class DefaultXmppCarbonsService(
    private val client: KmpXmppClient,
) : XmppCarbonsService {

    override suspend fun enableCarbons(): XmppResult<Unit> =
        sendToggleStanza(
            action = "enable",
            requestId = "carbons-enable",
        )

    override suspend fun disableCarbons(): XmppResult<Unit> =
        sendToggleStanza(
            action = "disable",
            requestId = "carbons-disable",
        )

    override fun parseForwardedCarbon(xml: String): XmppResult<XmppCarbonMessage> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Carbon XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        val startTags = XmppXmlMiniParser.parseStartTags(xml)
        val direction = startTags.firstNotNullOfOrNull { tag ->
            val namespace = tag.attributes["xmlns"] ?: return@firstNotNullOfOrNull null
            if (namespace != CARBONS_NAMESPACE) return@firstNotNullOfOrNull null
            when (tag.name) {
                "sent" -> CarbonDirection.Sent
                "received" -> CarbonDirection.Received
                else -> null
            }
        } ?: return XmppResult.Failure(
            xmppErrorParsing(
                message = "Carbon stanza missing <sent/> or <received/> with urn:xmpp:carbons:2 namespace.",
                stage = XmppErrorStage.Messaging,
                recoverable = true,
            ),
        )

        val forwardedTag = startTags.firstOrNull { tag ->
            tag.name == "forwarded" && tag.attributes["xmlns"] == FORWARD_NAMESPACE
        }
        if (forwardedTag == null) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Carbon stanza missing forwarded payload.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        val forwardedXml = XmppXmlMiniParser.tagInnerXml(xml, forwardedTag) ?: ""
        val forwardedMessageTag = XmppXmlMiniParser.parseStartTags(forwardedXml).firstOrNull { it.name == "message" }
        val from = forwardedMessageTag?.attributes?.get("from")?.let { parseJidOrNull(it) }
        val to = forwardedMessageTag?.attributes?.get("to")?.let { parseJidOrNull(it) }
        val body = XmppXmlMiniParser.textForTag(forwardedXml, "body")?.trim()?.ifBlank { null }

        return XmppResult.Success(
            XmppCarbonMessage(
                direction = direction,
                from = from,
                to = to,
                body = body,
            ),
        )
    }

    override fun validateCarbonsIqResult(xml: String, requestId: String): XmppResult<Unit> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Carbons IQ result XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
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

        val matchingIq = XmppXmlMiniParser.parseStartTags(xml).firstOrNull { tag ->
            tag.name == "iq" &&
                tag.attributes["type"] == "result" &&
                tag.attributes["id"] == requestId
        }
        return if (matchingIq != null) {
            XmppResult.Success(Unit)
        } else {
            XmppResult.Failure(
                xmppErrorParsing(
                    message = "Carbons IQ result stanza missing matching id/type result.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
    }

    private suspend fun sendToggleStanza(action: String, requestId: String): XmppResult<Unit> {
        if (requestId.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Request id cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val escapedId = escapeXml(requestId)
        val stanza = "<iq type='set' id='$escapedId'><$action xmlns='$CARBONS_NAMESPACE'/></iq>"
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
