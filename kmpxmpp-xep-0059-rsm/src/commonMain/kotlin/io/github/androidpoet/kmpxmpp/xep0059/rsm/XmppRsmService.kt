package io.github.androidpoet.kmpxmpp.xep0059.rsm

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

private const val RSM_NAMESPACE: String = "http://jabber.org/protocol/rsm"

public data class XmppRsmPage(
    val max: Int? = null,
    val after: String? = null,
    val before: String? = null,
)

public interface XmppRsmService {
    public suspend fun requestPage(queryXml: String, page: XmppRsmPage): XmppResult<Unit>
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

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
