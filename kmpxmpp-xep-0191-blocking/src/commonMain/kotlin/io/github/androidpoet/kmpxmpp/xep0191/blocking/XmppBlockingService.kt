package io.github.androidpoet.kmpxmpp.xep0191.blocking

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.parseJidOrNull
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val BLOCKING_NAMESPACE: String = "urn:xmpp:blocking"

public data class XmppBlockListItem(
    val jid: Jid,
)

public interface XmppBlockingService {
    public suspend fun block(jids: List<Jid>, requestId: String): XmppResult<Unit>

    public suspend fun unblock(jids: List<Jid>, requestId: String): XmppResult<Unit>

    public suspend fun requestBlockList(requestId: String): XmppResult<Unit>

    public fun parseBlockListResult(xml: String): XmppResult<List<XmppBlockListItem>>

    public fun validateBlockingCommandRequest(xml: String, command: String): XmppResult<String>

    public fun validateBlockingCommandResult(xml: String, requestId: String): XmppResult<Unit>
}

public class DefaultXmppBlockingService(
    private val client: KmpXmppClient,
) : XmppBlockingService {

    override suspend fun block(jids: List<Jid>, requestId: String): XmppResult<Unit> =
        sendBlockCommand(tag = "block", jids = jids, requestId = requestId)

    override suspend fun unblock(jids: List<Jid>, requestId: String): XmppResult<Unit> =
        sendBlockCommand(tag = "unblock", jids = jids, requestId = requestId)

    override suspend fun requestBlockList(requestId: String): XmppResult<Unit> {
        if (requestId.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Request id cannot be blank.", XmppErrorStage.Messaging, true))
        }
        val stanza = "<iq type='get' id='${escapeXml(requestId)}'><blocklist xmlns='$BLOCKING_NAMESPACE'/></iq>"
        return client.sendStanza(stanza)
    }

    private suspend fun sendBlockCommand(tag: String, jids: List<Jid>, requestId: String): XmppResult<Unit> {
        if (requestId.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Request id cannot be blank.", XmppErrorStage.Messaging, true))
        }
        if (jids.isEmpty()) {
            return XmppResult.Failure(xmppErrorInvalidInput("JID list cannot be empty.", XmppErrorStage.Messaging, true))
        }
        if (jids.any { it.domain.isBlank() }) {
            return XmppResult.Failure(xmppErrorInvalidInput("Every JID must include a domain.", XmppErrorStage.Messaging, false))
        }

        val items = jids.joinToString(separator = "") { "<item jid='${escapeXml(it.toString())}'/>" }
        val stanza = "<iq type='set' id='${escapeXml(requestId)}'><$tag xmlns='$BLOCKING_NAMESPACE'>$items</$tag></iq>"
        return client.sendStanza(stanza)
    }

    override fun parseBlockListResult(xml: String): XmppResult<List<XmppBlockListItem>> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Block list result XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        val iqTag = tags.firstOrNull { it.name == "iq" }
        if (!iqTag?.attributes?.get("type").equals("result", ignoreCase = true)) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Block list response must be IQ type='result'.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val blocklistTag = tags.firstOrNull { it.name == "blocklist" && it.attributes["xmlns"] == BLOCKING_NAMESPACE }
        val listBody = blocklistTag?.let { XmppXmlMiniParser.tagInnerXml(xml, it) }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Block list response missing <blocklist xmlns='urn:xmpp:blocking'>.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )

        val items = XmppXmlMiniParser.parseStartTags(listBody)
            .filter { it.name == "item" }
            .mapNotNull { tag ->
                parseJidOrNull(tag.attributes["jid"] ?: "")?.let(::XmppBlockListItem)
            }
            .toList()

        return XmppResult.Success(items)
    }

    override fun validateBlockingCommandRequest(xml: String, command: String): XmppResult<String> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Blocking command request XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        if (command != "block" && command != "unblock") {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Blocking command must be 'block' or 'unblock'.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = false,
                ),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        val iqTag = tags.firstOrNull { it.name == "iq" }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Blocking command request must contain <iq/>.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val type = iqTag.attributes["type"]
        if (!type.equals("set", ignoreCase = true)) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Blocking command request IQ must be type='set'.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val requestId = iqTag.attributes["id"]
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Blocking command request IQ missing id attribute.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val commandTag = tags.firstOrNull { it.name == command }
        if (commandTag?.attributes?.get("xmlns") != BLOCKING_NAMESPACE) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Blocking command request missing <$command xmlns='urn:xmpp:blocking'>.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        return XmppResult.Success(requestId)
    }

    override fun validateBlockingCommandResult(xml: String, requestId: String): XmppResult<Unit> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Blocking command result XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val iqTag = XmppXmlMiniParser.parseStartTags(xml).firstOrNull { it.name == "iq" }
        if (!iqTag?.attributes?.get("type").equals("result", ignoreCase = true)) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Blocking command response must be IQ type='result'.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        if (iqTag?.attributes?.get("id") != requestId) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Blocking command response id does not match request id.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        return XmppResult.Success(Unit)
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

}
