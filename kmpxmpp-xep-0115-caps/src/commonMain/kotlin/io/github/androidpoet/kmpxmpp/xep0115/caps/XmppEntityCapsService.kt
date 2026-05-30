package io.github.androidpoet.kmpxmpp.xep0115.caps

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

private const val CAPS_NAMESPACE: String = "http://jabber.org/protocol/caps"

public data class XmppEntityCaps(
    val node: String,
    val hash: String,
    val ver: String,
)

public interface XmppEntityCapsService {
    public suspend fun advertiseCaps(caps: XmppEntityCaps): XmppResult<Unit>
}

public class DefaultXmppEntityCapsService(
    private val client: KmpXmppClient,
) : XmppEntityCapsService {

    override suspend fun advertiseCaps(caps: XmppEntityCaps): XmppResult<Unit> {
        if (caps.node.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Caps node cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = false,
                ),
            )
        }
        if (caps.hash.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Caps hash cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = false,
                ),
            )
        }
        if (caps.ver.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Caps ver cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = false,
                ),
            )
        }

        val stanza = "<presence><c xmlns='$CAPS_NAMESPACE' node='${escapeXml(caps.node)}' hash='${escapeXml(caps.hash)}' ver='${escapeXml(caps.ver)}'/></presence>"
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
