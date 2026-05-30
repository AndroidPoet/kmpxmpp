package io.github.androidpoet.kmpxmpp.xep0313.mam

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

private const val MAM_NAMESPACE: String = "urn:xmpp:mam:2"
private const val XDATA_NAMESPACE: String = "jabber:x:data"
private const val RSM_NAMESPACE: String = "http://jabber.org/protocol/rsm"

public data class MamQuery(
    val withJid: Jid? = null,
    val max: Int? = null,
    val after: String? = null,
)

public interface XmppMamService {
    public suspend fun queryArchive(queryId: String, query: MamQuery = MamQuery()): XmppResult<Unit>
}

public class DefaultXmppMamService(
    private val client: KmpXmppClient,
) : XmppMamService {

    override suspend fun queryArchive(queryId: String, query: MamQuery): XmppResult<Unit> {
        if (queryId.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Query id cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        if (query.max != null && query.max <= 0) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "RSM max must be greater than zero when provided.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        if (query.withJid != null && query.withJid.domain.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "with JID domain cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        val escapedQueryId = escapeXml(queryId)
        val withField = query.withJid?.let {
            "<field var='with'><value>${escapeXml(it.toString())}</value></field>"
        } ?: ""
        val rsm = buildString {
            if (query.max != null || !query.after.isNullOrBlank()) {
                append("<set xmlns='")
                append(RSM_NAMESPACE)
                append("'>")
                query.max?.let { append("<max>$it</max>") }
                query.after?.takeIf { it.isNotBlank() }?.let { append("<after>${escapeXml(it)}</after>") }
                append("</set>")
            }
        }

        val stanza = "<iq type='set' id='$escapedQueryId'><query xmlns='$MAM_NAMESPACE' queryid='$escapedQueryId'><x xmlns='$XDATA_NAMESPACE' type='submit'><field var='FORM_TYPE'><value>$MAM_NAMESPACE</value></field>$withField</x>$rsm</query></iq>"
        return client.sendStanza(stanza)
    }

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
