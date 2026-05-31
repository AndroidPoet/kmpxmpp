package io.github.androidpoet.kmpxmpp.xep0030.disco

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.parseJidOrNull
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val DISCO_INFO_NAMESPACE: String = "http://jabber.org/protocol/disco#info"
private const val DISCO_ITEMS_NAMESPACE: String = "http://jabber.org/protocol/disco#items"

public data class DiscoIdentity(
    val category: String,
    val type: String,
    val name: String?,
)

public data class DiscoItem(
    val jid: Jid?,
    val node: String?,
    val name: String?,
)

public data class DiscoInfoResult(
    val identities: List<DiscoIdentity>,
    val features: List<String>,
)

public data class DiscoItemsResult(
    val items: List<DiscoItem>,
)

public interface XmppDiscoService {
    public suspend fun requestInfo(to: Jid, node: String? = null, requestId: String): XmppResult<Unit>

    public suspend fun requestItems(to: Jid, node: String? = null, requestId: String): XmppResult<Unit>

    public fun parseInfoResult(xml: String): XmppResult<DiscoInfoResult>

    public fun parseItemsResult(xml: String): XmppResult<DiscoItemsResult>

    public fun validateDiscoRequest(xml: String, expectedNamespace: String): XmppResult<String>
}

public class DefaultXmppDiscoService(
    private val client: KmpXmppClient,
) : XmppDiscoService {

    override suspend fun requestInfo(to: Jid, node: String?, requestId: String): XmppResult<Unit> =
        requestDisco(
            to = to,
            node = node,
            requestId = requestId,
            queryNamespace = DISCO_INFO_NAMESPACE,
        )

    override suspend fun requestItems(to: Jid, node: String?, requestId: String): XmppResult<Unit> =
        requestDisco(
            to = to,
            node = node,
            requestId = requestId,
            queryNamespace = DISCO_ITEMS_NAMESPACE,
        )

    private suspend fun requestDisco(
        to: Jid,
        node: String?,
        requestId: String,
        queryNamespace: String,
    ): XmppResult<Unit> {
        if (to.domain.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Target JID domain cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = false,
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

        val nodeAttribute = node?.takeIf { it.isNotBlank() }?.let {
            " node='${escapeXml(it)}'"
        } ?: ""
        val stanza = "<iq type='get' to='${escapeXml(to.toString())}' id='${escapeXml(requestId)}'><query xmlns='$queryNamespace'$nodeAttribute/></iq>"
        return client.sendStanza(stanza)
    }

    override fun parseInfoResult(xml: String): XmppResult<DiscoInfoResult> {
        val queryValidation = validateResultQuery(xml, DISCO_INFO_NAMESPACE)
        if (queryValidation is XmppResult.Failure) return queryValidation

        val startTags = XmppXmlMiniParser.parseStartTags(xml)
        val identities = startTags.mapNotNull { tag ->
            if (tag.name != "identity") return@mapNotNull null
            val category = tag.attributes["category"] ?: return@mapNotNull null
            val type = tag.attributes["type"] ?: return@mapNotNull null
            val name = tag.attributes["name"]
            DiscoIdentity(category = category, type = type, name = name)
        }

        val features = startTags
            .filter { it.name == "feature" }
            .mapNotNull { it.attributes["var"] }

        return XmppResult.Success(
            DiscoInfoResult(
                identities = identities,
                features = features,
            ),
        )
    }

    override fun parseItemsResult(xml: String): XmppResult<DiscoItemsResult> {
        val queryValidation = validateResultQuery(xml, DISCO_ITEMS_NAMESPACE)
        if (queryValidation is XmppResult.Failure) return queryValidation

        val items = XmppXmlMiniParser.parseStartTags(xml).mapNotNull { tag ->
            if (tag.name != "item") return@mapNotNull null
            val jidRaw = tag.attributes["jid"]
            val jid = jidRaw?.let { parseJidOrNull(it) }
            val node = tag.attributes["node"]
            val name = tag.attributes["name"]
            DiscoItem(jid = jid, node = node, name = name)
        }

        return XmppResult.Success(DiscoItemsResult(items = items))
    }

    override fun validateDiscoRequest(xml: String, expectedNamespace: String): XmppResult<String> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Disco request XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        val iqTag = tags.firstOrNull { it.name == "iq" }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Disco request missing <iq/> stanza.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val type = iqTag.attributes["type"]
        if (!type.equals("get", ignoreCase = true)) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Disco request IQ must be type='get'.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val requestId = iqTag.attributes["id"]
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Disco request IQ missing id attribute.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val queryTag = tags.firstOrNull { it.name == "query" }
        if (queryTag?.attributes?.get("xmlns") != expectedNamespace) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Disco request missing expected namespace: $expectedNamespace.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        return XmppResult.Success(requestId)
    }

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun validateResultQuery(xml: String, namespace: String): XmppResult<Unit> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Disco result XML cannot be blank.",
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
                    message = "Disco result must be IQ type='result'.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val queryTag = tags.firstOrNull { it.name == "query" }
        if (queryTag?.attributes?.get("xmlns") != namespace) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Disco result missing expected namespace: $namespace.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        return XmppResult.Success(Unit)
    }

}
