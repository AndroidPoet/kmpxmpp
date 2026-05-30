package io.github.androidpoet.kmpxmpp.xep0030.disco

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

private const val DISCO_INFO_NAMESPACE: String = "http://jabber.org/protocol/disco#info"
private const val DISCO_ITEMS_NAMESPACE: String = "http://jabber.org/protocol/disco#items"

public interface XmppDiscoService {
    public suspend fun requestInfo(to: Jid, node: String? = null, requestId: String): XmppResult<Unit>

    public suspend fun requestItems(to: Jid, node: String? = null, requestId: String): XmppResult<Unit>
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

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
