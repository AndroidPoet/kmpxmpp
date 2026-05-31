package io.github.androidpoet.kmpxmpp.xep0199.ping

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.parseJidOrNull
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val PING_NAMESPACE: String = "urn:xmpp:ping"

public data class XmppPingResponse(
    val requestId: String,
    val from: Jid?,
)

public interface XmppPingService {
    public suspend fun pingServer(requestId: String): XmppResult<Unit>

    public suspend fun pingEntity(to: Jid, requestId: String): XmppResult<Unit>

    public fun parsePingResult(xml: String): XmppResult<XmppPingResponse>

    public fun isPingTimeoutOrServiceUnavailable(xml: String, requestId: String): XmppResult<Boolean>
}

public class DefaultXmppPingService(
    private val client: KmpXmppClient,
) : XmppPingService {

    override suspend fun pingServer(requestId: String): XmppResult<Unit> {
        val idValidation = validateRequestId(requestId)
        if (idValidation != null) return XmppResult.Failure(idValidation)
        return client.sendStanza(
            "<iq type='get' id='${escapeXml(requestId)}'><ping xmlns='$PING_NAMESPACE'/></iq>",
        )
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
        val idValidation = validateRequestId(requestId)
        if (idValidation != null) return XmppResult.Failure(idValidation)

        val stanza = "<iq type='get' to='${escapeXml(to.toString())}' id='${escapeXml(requestId)}'><ping xmlns='$PING_NAMESPACE'/></iq>"
        return client.sendStanza(stanza)
    }

    override fun parsePingResult(xml: String): XmppResult<XmppPingResponse> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Ping result XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val iqTag = XmppXmlMiniParser.parseStartTags(xml).firstOrNull { tag ->
            tag.name == "iq" && tag.attributes["type"].equals("result", ignoreCase = true)
        }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Ping result stanza must be <iq type='result'/>.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val requestId = iqTag.attributes["id"]
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Ping result stanza missing id attribute.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val from = iqTag.attributes["from"]?.let { parseJidOrNull(it) }
        return XmppResult.Success(XmppPingResponse(requestId = requestId, from = from))
    }

    override fun isPingTimeoutOrServiceUnavailable(xml: String, requestId: String): XmppResult<Boolean> {
        val idValidation = validateRequestId(requestId)
        if (idValidation != null) return XmppResult.Failure(idValidation)
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Ping error XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        val tags = XmppXmlMiniParser.parseStartTags(xml)
        val errorIq = tags.firstOrNull { tag ->
            tag.name == "iq" &&
                tag.attributes["type"].equals("error", ignoreCase = true) &&
                tag.attributes["id"] == requestId
        }
        if (errorIq == null) {
            return XmppResult.Success(false)
        }

        val timeout = tags.any { it.name == "remote-server-timeout" }
        val serviceUnavailable = tags.any { it.name == "service-unavailable" }
        return XmppResult.Success(timeout || serviceUnavailable)
    }

    private fun validateRequestId(requestId: String) =
        if (requestId.isBlank()) {
            xmppErrorInvalidInput(
                message = "Request id cannot be blank.",
                stage = XmppErrorStage.Messaging,
                recoverable = true,
            )
        } else {
            null
        }

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
