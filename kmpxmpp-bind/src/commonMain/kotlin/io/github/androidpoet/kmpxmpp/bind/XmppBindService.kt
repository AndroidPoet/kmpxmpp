package io.github.androidpoet.kmpxmpp.bind

import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.parseJidOrNull
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val BIND_NAMESPACE: String = "urn:ietf:params:xml:ns:xmpp-bind"

public data class XmppBindResult(
    val requestId: String,
    val jid: Jid?,
    val resource: String?,
)

public interface XmppBindService {
    public fun buildBindRequest(requestId: String, resource: String? = null): XmppResult<String>

    public fun parseBindResult(xml: String, expectedRequestId: String? = null): XmppResult<XmppBindResult>
}

public class DefaultXmppBindService : XmppBindService {
    override fun buildBindRequest(requestId: String, resource: String?): XmppResult<String> {
        if (requestId.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Bind request id cannot be blank.",
                    stage = XmppErrorStage.Authentication,
                    recoverable = true,
                ),
            )
        }
        if (resource != null && resource.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Bind resource cannot be blank when provided.",
                    stage = XmppErrorStage.Authentication,
                    recoverable = true,
                ),
            )
        }
        val resourceNode = resource?.let { "<resource>${escapeXml(it)}</resource>" } ?: ""
        val stanza = "<iq type='set' id='${escapeXml(requestId)}'><bind xmlns='$BIND_NAMESPACE'>$resourceNode</bind></iq>"
        return XmppResult.Success(stanza)
    }

    override fun parseBindResult(xml: String, expectedRequestId: String?): XmppResult<XmppBindResult> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Bind result XML cannot be blank.",
                    stage = XmppErrorStage.Authentication,
                    recoverable = true,
                ),
            )
        }

        val tags = XmppXmlMiniParser.parseStartTags(xml)
        if (tags.any { it.name == "iq" && it.attributes["type"].equals("error", ignoreCase = true) }) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Bind result stanza indicates server error.",
                    stage = XmppErrorStage.Authentication,
                    recoverable = true,
                ),
            )
        }

        val iqResult = tags.firstOrNull { it.name == "iq" && it.attributes["type"].equals("result", ignoreCase = true) }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Bind response must be IQ type='result'.",
                    stage = XmppErrorStage.Authentication,
                    recoverable = true,
                ),
            )
        val requestId = iqResult.attributes["id"]
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Bind response missing id attribute.",
                    stage = XmppErrorStage.Authentication,
                    recoverable = true,
                ),
            )
        if (expectedRequestId != null && requestId != expectedRequestId) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Bind response id mismatch.",
                    stage = XmppErrorStage.Authentication,
                    recoverable = true,
                ),
            )
        }

        val hasBindNamespace = tags.any { it.name == "bind" && it.attributes["xmlns"] == BIND_NAMESPACE }
        if (!hasBindNamespace) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Bind response missing bind namespace.",
                    stage = XmppErrorStage.Authentication,
                    recoverable = true,
                ),
            )
        }

        val jid = XmppXmlMiniParser.textForTag(xml, "jid")?.let { parseJidOrNull(it) }
        val resource = XmppXmlMiniParser.textForTag(xml, "resource")?.takeIf { it.isNotBlank() }
        return XmppResult.Success(
            XmppBindResult(
                requestId = requestId,
                jid = jid,
                resource = resource,
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
}
