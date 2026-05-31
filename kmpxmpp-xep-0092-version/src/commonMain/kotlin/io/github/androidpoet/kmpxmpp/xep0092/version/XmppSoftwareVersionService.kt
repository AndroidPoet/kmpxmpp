package io.github.androidpoet.kmpxmpp.xep0092.version

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val VERSION_NAMESPACE: String = "jabber:iq:version"

public data class XmppSoftwareVersion(
    val name: String,
    val version: String,
    val os: String? = null,
)

public interface XmppSoftwareVersionService {
    public suspend fun requestVersion(to: Jid, requestId: String): XmppResult<Unit>

    public suspend fun sendVersionResult(to: Jid, requestId: String, version: XmppSoftwareVersion): XmppResult<Unit>

    public fun parseVersionResult(xml: String): XmppResult<XmppSoftwareVersion>

    public fun validateVersionRequest(xml: String): XmppResult<String>
}

public class DefaultXmppSoftwareVersionService(
    private val client: KmpXmppClient,
) : XmppSoftwareVersionService {

    override suspend fun requestVersion(to: Jid, requestId: String): XmppResult<Unit> {
        if (to.domain.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Target JID domain cannot be blank.", XmppErrorStage.Messaging, false))
        }
        if (requestId.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Request id cannot be blank.", XmppErrorStage.Messaging, true))
        }

        val stanza = "<iq type='get' to='${escapeXml(to.toString())}' id='${escapeXml(requestId)}'><query xmlns='$VERSION_NAMESPACE'/></iq>"
        return client.sendStanza(stanza)
    }

    override suspend fun sendVersionResult(to: Jid, requestId: String, version: XmppSoftwareVersion): XmppResult<Unit> {
        if (to.domain.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Target JID domain cannot be blank.", XmppErrorStage.Messaging, false))
        }
        if (requestId.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Request id cannot be blank.", XmppErrorStage.Messaging, true))
        }
        if (version.name.isBlank() || version.version.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Version name and version cannot be blank.", XmppErrorStage.Messaging, true))
        }

        val osNode = version.os?.takeIf { it.isNotBlank() }?.let { "<os>${escapeXml(it)}</os>" } ?: ""
        val stanza = "<iq type='result' to='${escapeXml(to.toString())}' id='${escapeXml(requestId)}'><query xmlns='$VERSION_NAMESPACE'><name>${escapeXml(version.name)}</name><version>${escapeXml(version.version)}</version>$osNode</query></iq>"
        return client.sendStanza(stanza)
    }

    override fun parseVersionResult(xml: String): XmppResult<XmppSoftwareVersion> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Version result XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        val iqResult = tags.firstOrNull { it.name == "iq" && it.attributes["type"]?.equals("result", ignoreCase = true) == true }
        if (iqResult == null) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Version response must be IQ type='result'.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val queryTag = tags.firstOrNull { it.name == "query" && it.attributes["xmlns"] == VERSION_NAMESPACE }
        if (queryTag == null) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Version response missing jabber:iq:version namespace.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        val name = XmppXmlMiniParser.textForTag(xml, "name")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Version response missing non-blank <name>.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )

        val versionValue = XmppXmlMiniParser.textForTag(xml, "version")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Version response missing non-blank <version>.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )

        val os = XmppXmlMiniParser.textForTag(xml, "os")
            ?.trim()
            ?.ifBlank { null }

        return XmppResult.Success(XmppSoftwareVersion(name = name, version = versionValue, os = os))
    }

    override fun validateVersionRequest(xml: String): XmppResult<String> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Version request XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        val iqTag = tags.firstOrNull { it.name == "iq" } ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Version request must contain <iq/>.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val type = iqTag.attributes["type"]
        if (!type.equals("get", ignoreCase = true)) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Version request IQ must be type='get'.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val requestId = iqTag.attributes["id"]
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Version request IQ missing id attribute.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val hasVersionNamespace = tags.any { it.name == "query" && it.attributes["xmlns"] == VERSION_NAMESPACE }
        if (!hasVersionNamespace) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Version request missing jabber:iq:version namespace.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        return XmppResult.Success(requestId)
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

}
