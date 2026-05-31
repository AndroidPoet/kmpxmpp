package io.github.androidpoet.kmpxmpp.xep0359.stanzaids

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.parseJidOrNull
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val STANZA_IDS_NAMESPACE: String = "urn:xmpp:sid:0"

public data class OriginId(
    val id: String,
)

public data class StanzaId(
    val id: String,
    val by: Jid?,
)

public data class ParsedStanzaIds(
    val originId: OriginId?,
    val stanzaIds: List<StanzaId>,
)

public interface XmppStanzaIdsService {
    public suspend fun sendOriginIdMessage(to: Jid, body: String, originId: String): XmppResult<Unit>

    public suspend fun sendMessageWithOriginAndStanzaId(
        to: Jid,
        body: String,
        originId: String,
        stanzaId: String,
        stanzaBy: Jid,
    ): XmppResult<Unit>

    public fun parseStanzaIds(xml: String): XmppResult<ParsedStanzaIds>
}

public class DefaultXmppStanzaIdsService(
    private val client: KmpXmppClient,
) : XmppStanzaIdsService {

    override suspend fun sendOriginIdMessage(to: Jid, body: String, originId: String): XmppResult<Unit> {
        if (to.domain.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput("Recipient JID domain cannot be blank.", XmppErrorStage.Messaging, recoverable = false),
            )
        }
        if (body.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput("Message body cannot be blank.", XmppErrorStage.Messaging, recoverable = true),
            )
        }
        if (originId.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput("Origin id cannot be blank.", XmppErrorStage.Messaging, recoverable = true),
            )
        }

        val stanza = "<message to='${escapeXml(to.toString())}' type='chat'><body>${escapeXml(body)}</body><origin-id xmlns='$STANZA_IDS_NAMESPACE' id='${escapeXml(originId)}'/></message>"
        return client.sendStanza(stanza)
    }

    override suspend fun sendMessageWithOriginAndStanzaId(
        to: Jid,
        body: String,
        originId: String,
        stanzaId: String,
        stanzaBy: Jid,
    ): XmppResult<Unit> {
        if (to.domain.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput("Recipient JID domain cannot be blank.", XmppErrorStage.Messaging, recoverable = false),
            )
        }
        if (body.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput("Message body cannot be blank.", XmppErrorStage.Messaging, recoverable = true),
            )
        }
        if (originId.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput("Origin id cannot be blank.", XmppErrorStage.Messaging, recoverable = true),
            )
        }
        if (stanzaId.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput("Stanza id cannot be blank.", XmppErrorStage.Messaging, recoverable = true),
            )
        }
        if (stanzaBy.domain.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput("Stanza by JID domain cannot be blank.", XmppErrorStage.Messaging, recoverable = false),
            )
        }

        val stanza = buildString {
            append("<message to='")
            append(escapeXml(to.toString()))
            append("' type='chat'><body>")
            append(escapeXml(body))
            append("</body><origin-id xmlns='")
            append(STANZA_IDS_NAMESPACE)
            append("' id='")
            append(escapeXml(originId))
            append("'/><stanza-id xmlns='")
            append(STANZA_IDS_NAMESPACE)
            append("' id='")
            append(escapeXml(stanzaId))
            append("' by='")
            append(escapeXml(stanzaBy.toString()))
            append("'/></message>")
        }
        return client.sendStanza(stanza)
    }

    override fun parseStanzaIds(xml: String): XmppResult<ParsedStanzaIds> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput("Stanza XML cannot be blank.", XmppErrorStage.Messaging, recoverable = true),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        val sidTags = tags.filter { it.attributes["xmlns"] == STANZA_IDS_NAMESPACE }
        if (sidTags.isEmpty()) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Stanza is missing urn:xmpp:sid:0 namespace.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        val originIdTag = sidTags.firstOrNull { it.name == "origin-id" }
        val originId = originIdTag?.let { tag ->
            val id = tag.attributes["id"]
                ?: return XmppResult.Failure(
                    xmppErrorParsing(
                        message = "origin-id element missing id attribute.",
                        stage = XmppErrorStage.Messaging,
                        recoverable = true,
                    ),
                )
            OriginId(id)
        }

        val stanzaIds = mutableListOf<StanzaId>()
        sidTags
            .filter { it.name == "stanza-id" }
            .forEach { tag ->
                val id = tag.attributes["id"]
                ?: return XmppResult.Failure(
                    xmppErrorParsing(
                        message = "stanza-id element missing id attribute.",
                        stage = XmppErrorStage.Messaging,
                        recoverable = true,
                    ),
                )
                val by = tag.attributes["by"]?.let { parseJidOrNull(it) }
                stanzaIds += StanzaId(id = id, by = by)
            }

        return XmppResult.Success(
            ParsedStanzaIds(
                originId = originId,
                stanzaIds = stanzaIds,
            ),
        )
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
