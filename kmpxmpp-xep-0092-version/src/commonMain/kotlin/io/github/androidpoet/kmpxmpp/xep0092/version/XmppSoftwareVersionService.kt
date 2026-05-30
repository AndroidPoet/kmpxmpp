package io.github.androidpoet.kmpxmpp.xep0092.version

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

private const val VERSION_NAMESPACE: String = "jabber:iq:version"

public data class XmppSoftwareVersion(
    val name: String,
    val version: String,
    val os: String? = null,
)

public interface XmppSoftwareVersionService {
    public suspend fun requestVersion(to: Jid, requestId: String): XmppResult<Unit>

    public suspend fun sendVersionResult(to: Jid, requestId: String, version: XmppSoftwareVersion): XmppResult<Unit>
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

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
