package io.github.androidpoet.kmpxmpp.im

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

public interface XmppPresenceService {
    public suspend fun sendAvailable(status: String? = null): XmppResult<Unit>

    public suspend fun sendUnavailable(status: String? = null): XmppResult<Unit>
}

public class DefaultXmppPresenceService(
    private val client: KmpXmppClient,
) : XmppPresenceService {

    override suspend fun sendAvailable(status: String?): XmppResult<Unit> =
        sendPresence(type = null, status = status)

    override suspend fun sendUnavailable(status: String?): XmppResult<Unit> =
        sendPresence(type = "unavailable", status = status)

    private suspend fun sendPresence(type: String?, status: String?): XmppResult<Unit> {
        if (status != null && status.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Presence status cannot be blank when provided.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        val typeAttr = type?.let { " type='${escapeXml(it)}'" } ?: ""
        val statusNode = status?.let { "<status>${escapeXml(it)}</status>" } ?: ""
        val stanza = "<presence$typeAttr>$statusNode</presence>"

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
