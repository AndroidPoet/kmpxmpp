package io.github.androidpoet.kmpxmpp.xep0313.mam

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.parseJidOrNull
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing
import kotlinx.datetime.Instant

private const val MAM_NAMESPACE: String = "urn:xmpp:mam:2"
private const val XDATA_NAMESPACE: String = "jabber:x:data"
private const val RSM_NAMESPACE: String = "http://jabber.org/protocol/rsm"

public data class MamQuery(
    val withJid: Jid? = null,
    val max: Int? = null,
    val after: String? = null,
    val before: String? = null,
    val start: Instant? = null,
    val end: Instant? = null,
)

public data class MamArchivedMessage(
    val resultId: String,
    val queryId: String?,
    val from: Jid?,
    val body: String?,
)

public data class MamPageInfo(
    val complete: Boolean,
    val stable: Boolean,
    val first: String?,
    val last: String?,
    val count: Int?,
)

public data class MamQueryResult(
    val messages: List<MamArchivedMessage>,
    val pageInfo: MamPageInfo,
)

public interface XmppMamService {
    public suspend fun queryArchive(queryId: String, query: MamQuery = MamQuery()): XmppResult<Unit>

    public fun parseQueryResult(xml: String): XmppResult<MamQueryResult>
}

public class DefaultXmppMamService(
    private val client: KmpXmppClient,
) : XmppMamService {

    override suspend fun queryArchive(queryId: String, query: MamQuery): XmppResult<Unit> {
        val validationError = validateQuery(queryId, query)
        if (validationError != null) return XmppResult.Failure(validationError)

        val escapedQueryId = escapeXml(queryId)
        val withField = query.withJid?.let { "<field var='with'><value>${escapeXml(it.toString())}</value></field>" } ?: ""
        val startField = query.start?.let { "<field var='start'><value>${it.toString()}</value></field>" } ?: ""
        val endField = query.end?.let { "<field var='end'><value>${it.toString()}</value></field>" } ?: ""
        val rsm = buildString {
            if (query.max != null || !query.after.isNullOrBlank() || !query.before.isNullOrBlank()) {
                append("<set xmlns='")
                append(RSM_NAMESPACE)
                append("'>")
                query.max?.let { append("<max>$it</max>") }
                query.after?.takeIf { it.isNotBlank() }?.let { append("<after>${escapeXml(it)}</after>") }
                query.before?.takeIf { it.isNotBlank() }?.let { append("<before>${escapeXml(it)}</before>") }
                append("</set>")
            }
        }

        val stanza = "<iq type='set' id='$escapedQueryId'><query xmlns='$MAM_NAMESPACE' queryid='$escapedQueryId'><x xmlns='$XDATA_NAMESPACE' type='submit'><field var='FORM_TYPE'><value>$MAM_NAMESPACE</value></field>$withField$startField$endField</x>$rsm</query></iq>"
        return client.sendStanza(stanza)
    }

    override fun parseQueryResult(xml: String): XmppResult<MamQueryResult> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "MAM XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val startTags = XmppXmlMiniParser.parseStartTags(xml)
        val hasMamNamespace = startTags.any { tag ->
            (tag.name == "result" || tag.name == "fin") && tag.attributes["xmlns"] == MAM_NAMESPACE
        }
        if (!hasMamNamespace) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "MAM response missing urn:xmpp:mam:2 namespace.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        val messages = startTags
            .filter { tag -> tag.name == "result" && tag.attributes["xmlns"] == MAM_NAMESPACE }
            .mapNotNull { resultTag ->
                val resultId = resultTag.attributes["id"] ?: return@mapNotNull null
                val queryId = resultTag.attributes["queryid"]
                val resultXml = XmppXmlMiniParser.tagInnerXml(xml, resultTag) ?: ""
                val body = XmppXmlMiniParser.textForTag(resultXml, "body")?.trim()?.ifBlank { null }
                val messageStartTag = XmppXmlMiniParser.parseStartTags(resultXml).firstOrNull { it.name == "message" }
                val from = messageStartTag?.attributes?.get("from")?.let { parseJidOrNull(it) }

                MamArchivedMessage(
                    resultId = resultId,
                    queryId = queryId,
                    from = from,
                    body = body,
                )
            }

        val finTag = startTags.firstOrNull { tag -> tag.name == "fin" && tag.attributes["xmlns"] == MAM_NAMESPACE }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "MAM response missing <fin/> stanza.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val finXml = XmppXmlMiniParser.tagInnerXml(xml, finTag) ?: ""
        val complete = finTag.attributes["complete"] == "true"
        val stable = finTag.attributes["stable"] != "false"
        val first = XmppXmlMiniParser.textForTag(finXml, "first")?.trim()?.ifBlank { null }
        val last = XmppXmlMiniParser.textForTag(finXml, "last")?.trim()?.ifBlank { null }
        val count = XmppXmlMiniParser.textForTag(finXml, "count")?.trim()?.toIntOrNull()

        return XmppResult.Success(
            MamQueryResult(
                messages = messages,
                pageInfo = MamPageInfo(
                    complete = complete,
                    stable = stable,
                    first = first,
                    last = last,
                    count = count,
                ),
            ),
        )
    }

    private fun validateQuery(queryId: String, query: MamQuery) =
        when {
            queryId.isBlank() -> xmppErrorInvalidInput(
                message = "Query id cannot be blank.",
                stage = XmppErrorStage.Messaging,
                recoverable = true,
            )
            query.max != null && query.max <= 0 -> xmppErrorInvalidInput(
                message = "RSM max must be greater than zero when provided.",
                stage = XmppErrorStage.Messaging,
                recoverable = true,
            )
            !query.after.isNullOrBlank() && !query.before.isNullOrBlank() -> xmppErrorInvalidInput(
                message = "RSM after and before cannot both be set.",
                stage = XmppErrorStage.Messaging,
                recoverable = true,
            )
            query.withJid != null && query.withJid.domain.isBlank() -> xmppErrorInvalidInput(
                message = "with JID domain cannot be blank.",
                stage = XmppErrorStage.Messaging,
                recoverable = true,
            )
            query.start != null && query.end != null && query.start >= query.end -> xmppErrorInvalidInput(
                message = "MAM start must be earlier than end.",
                stage = XmppErrorStage.Messaging,
                recoverable = true,
            )
            else -> null
        }

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
