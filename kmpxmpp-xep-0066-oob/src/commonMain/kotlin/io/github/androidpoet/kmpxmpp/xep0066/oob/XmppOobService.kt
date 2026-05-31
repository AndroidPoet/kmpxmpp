package io.github.androidpoet.kmpxmpp.xep0066.oob

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.parseJidOrNull
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val OOB_NAMESPACE: String = "jabber:x:oob"

public data class ParsedOobPayload(
    val from: Jid?,
    val to: Jid?,
    val url: String,
    val description: String?,
)

public interface XmppOobService {
    public suspend fun sendOobUrl(to: Jid, url: String, description: String? = null): XmppResult<Unit>

    public fun parseOob(xml: String): XmppResult<ParsedOobPayload>
}

public class DefaultXmppOobService(
    private val client: KmpXmppClient,
) : XmppOobService {

    override suspend fun sendOobUrl(to: Jid, url: String, description: String?): XmppResult<Unit> {
        if (to.domain.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Recipient JID domain cannot be blank.", XmppErrorStage.Messaging, false))
        }
        if (url.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("OOB URL cannot be blank.", XmppErrorStage.Messaging, true))
        }
        if (description != null && description.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Description cannot be blank when provided.", XmppErrorStage.Messaging, true))
        }

        val descNode = description?.let { "<desc>${escapeXml(it)}</desc>" } ?: ""
        val stanza = "<message to='${escapeXml(to.toString())}'><x xmlns='$OOB_NAMESPACE'><url>${escapeXml(url)}</url>$descNode</x></message>"
        return client.sendStanza(stanza)
    }

    override fun parseOob(xml: String): XmppResult<ParsedOobPayload> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "OOB XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        val hasOobNamespace = tags.any { it.attributes["xmlns"] == OOB_NAMESPACE }
        if (!hasOobNamespace) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "OOB stanza missing jabber:x:oob namespace.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        val url = XmppXmlMiniParser.textForTag(xml, "url")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "OOB stanza missing non-blank <url> value.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        if (!isHttpUrl(url)) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "OOB URL must use http or https scheme.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        val desc = XmppXmlMiniParser.textForTag(xml, "desc")
            ?.trim()
            ?.ifBlank { null }

        val messageTag = tags.firstOrNull { it.name == "message" }
        val from = messageTag?.attributes?.get("from")?.let { parseJidOrNull(it) }
        val to = messageTag?.attributes?.get("to")?.let { parseJidOrNull(it) }

        return XmppResult.Success(
            ParsedOobPayload(
                from = from,
                to = to,
                url = url,
                description = desc,
            ),
        )
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private fun isHttpUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
}
