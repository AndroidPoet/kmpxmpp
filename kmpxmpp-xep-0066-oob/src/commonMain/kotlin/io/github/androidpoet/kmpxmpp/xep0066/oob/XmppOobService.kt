package io.github.androidpoet.kmpxmpp.xep0066.oob

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

private const val OOB_NAMESPACE: String = "jabber:x:oob"

public interface XmppOobService {
    public suspend fun sendOobUrl(to: Jid, url: String, description: String? = null): XmppResult<Unit>
}

public class DefaultXmppOobService(
    private val client: KmpXmppClient,
) : XmppOobService {

    override suspend fun sendOobUrl(to: Jid, url: String, description: String?): XmppResult<Unit> {
        if (to.domain.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Recipient JID domain cannot be blank.", XmppErrorStage.Messaging, false))
        }
        if (url.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("OOB URL cannot be blank.", XmppErrorStage.Messaging, true))
        }
        if (description != null && description.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Description cannot be blank when provided.", XmppErrorStage.Messaging, true))
        }

        val descNode = description?.let { "<desc>${escapeXml(it)}</desc>" } ?: ""
        val stanza = "<message to='${escapeXml(to.toString())}'><x xmlns='$OOB_NAMESPACE'><url>${escapeXml(url)}</url>$descNode</x></message>"
        return client.sendStanza(stanza)
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
