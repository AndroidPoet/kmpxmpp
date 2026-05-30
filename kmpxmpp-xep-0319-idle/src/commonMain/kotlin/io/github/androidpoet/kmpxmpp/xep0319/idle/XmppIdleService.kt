package io.github.androidpoet.kmpxmpp.xep0319.idle

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

private const val IDLE_NAMESPACE: String = "urn:xmpp:idle:1"

public interface XmppIdleService {
    public suspend fun advertiseIdleSince(sinceIso8601: String): XmppResult<Unit>
}

public class DefaultXmppIdleService(
    private val client: KmpXmppClient,
) : XmppIdleService {

    override suspend fun advertiseIdleSince(sinceIso8601: String): XmppResult<Unit> {
        if (sinceIso8601.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Idle since timestamp cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        val stanza = "<presence><idle xmlns='$IDLE_NAMESPACE' since='${escapeXml(sinceIso8601)}'/></presence>"
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
