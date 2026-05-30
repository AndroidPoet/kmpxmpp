package io.github.androidpoet.kmpxmpp.xep0384.omemo

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

private const val OMEMO_NAMESPACE: String = "eu.siacs.conversations.axolotl"

public interface XmppOmemoService {
    public suspend fun sendEncryptedMessage(
        to: Jid,
        encryptedPayloadBase64: String,
        sid: Int,
        ivBase64: String,
        payloadBase64: String,
    ): XmppResult<Unit>
}

public class DefaultXmppOmemoService(
    private val client: KmpXmppClient,
) : XmppOmemoService {

    override suspend fun sendEncryptedMessage(
        to: Jid,
        encryptedPayloadBase64: String,
        sid: Int,
        ivBase64: String,
        payloadBase64: String,
    ): XmppResult<Unit> {
        if (to.domain.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Recipient JID domain cannot be blank.", XmppErrorStage.Messaging, false))
        }
        if (encryptedPayloadBase64.isBlank() || ivBase64.isBlank() || payloadBase64.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("OMEMO encrypted fields cannot be blank.", XmppErrorStage.Messaging, true))
        }
        if (sid <= 0) {
            return XmppResult.Failure(xmppErrorInvalidInput("OMEMO sid must be greater than zero.", XmppErrorStage.Messaging, true))
        }

        val stanza = "<message to='${escapeXml(to.toString())}' type='chat'><encrypted xmlns='$OMEMO_NAMESPACE'><header sid='$sid'><key rid='$sid'>${escapeXml(encryptedPayloadBase64)}</key><iv>${escapeXml(ivBase64)}</iv></header><payload>${escapeXml(payloadBase64)}</payload></encrypted></message>"
        return client.sendStanza(stanza)
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
