package io.github.androidpoet.kmpxmpp.xep0059.rsm

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val RSM_NAMESPACE: String = "http://jabber.org/protocol/rsm"

public data class XmppRsmPage(
    val max: Int? = null,
    val after: String? = null,
    val before: String? = null,
)

public data class XmppRsmResultPage(
    val first: String?,
    val last: String?,
    val count: Int?,
)

public interface XmppRsmService {
    public suspend fun requestPage(queryXml: String, page: XmppRsmPage): XmppResult<Unit>

    public fun parseRsmResult(xml: String): XmppResult<XmppRsmResultPage>

    public fun validateRsmRequest(xml: String): XmppResult<Unit>
}

public class DefaultXmppRsmService(
    private val client: KmpXmppClient,
) : XmppRsmService {

    override suspend fun requestPage(queryXml: String, page: XmppRsmPage): XmppResult<Unit> {
        if (queryXml.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Query XML cannot be blank.", XmppErrorStage.Messaging, true))
        }
        if (page.max != null && page.max <= 0) {
            return XmppResult.Failure(xmppErrorInvalidInput("RSM max must be greater than zero when provided.", XmppErrorStage.Messaging, true))
        }
        if (!page.after.isNullOrBlank() && !page.before.isNullOrBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("RSM after and before cannot both be set.", XmppErrorStage.Messaging, true))
        }

        val setNode = buildString {
            append("<set xmlns='$RSM_NAMESPACE'>")
            page.max?.let { append("<max>$it</max>") }
            page.after?.takeIf { it.isNotBlank() }?.let { append("<after>${escapeXml(it)}</after>") }
            page.before?.takeIf { it.isNotBlank() }?.let { append("<before>${escapeXml(it)}</before>") }
            append("</set>")
        }
        val stanza = queryXml.replace("</query>", "$setNode</query>")
        return client.sendStanza(stanza)
    }

    override fun parseRsmResult(xml: String): XmppResult<XmppRsmResultPage> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "RSM result XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        val hasResultIq = tags.any { it.name == "iq" && it.attributes["type"]?.equals("result", ignoreCase = true) == true }
        if (!hasResultIq) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "RSM response must be IQ type='result'.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val setBlock = findElementBlock(xml, "set", requiredXmlns = RSM_NAMESPACE)
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "RSM response missing <set xmlns='http://jabber.org/protocol/rsm'>.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )

        val first = XmppXmlMiniParser.textForTag(setBlock, "first")?.trim()?.ifBlank { null }
        val last = XmppXmlMiniParser.textForTag(setBlock, "last")?.trim()?.ifBlank { null }
        val count = XmppXmlMiniParser.textForTag(setBlock, "count")?.trim()?.toIntOrNull()

        return XmppResult.Success(XmppRsmResultPage(first = first, last = last, count = count))
    }

    override fun validateRsmRequest(xml: String): XmppResult<Unit> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "RSM request XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val setBlock = findElementBlock(xml, "set", requiredXmlns = RSM_NAMESPACE)
        if (setBlock == null) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "RSM request missing <set xmlns='http://jabber.org/protocol/rsm'>.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val hasAfter = XmppXmlMiniParser.textForTag(setBlock, "after") != null
        val hasBefore = XmppXmlMiniParser.textForTag(setBlock, "before") != null
        if (hasAfter && hasBefore) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "RSM request cannot contain both <after> and <before>.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        return XmppResult.Success(Unit)
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private fun findElementBlock(xml: String, tagName: String, requiredXmlns: String? = null): String? {
        val target = tagName.lowercase()
        val startTag = XmppXmlMiniParser.parseStartTags(xml).firstOrNull { tag ->
            tag.name == target && (requiredXmlns == null || tag.attributes["xmlns"] == requiredXmlns)
        }
            ?: return null
        return XmppXmlMiniParser.tagInnerXml(xml, startTag)
    }
}
