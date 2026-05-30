package io.github.androidpoet.kmpxmpp.xep0184.receipts

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

private const val RECEIPTS_NAMESPACE: String = "urn:xmpp:receipts"

public interface XmppReceiptService {
    public suspend fun sendMessageWithReceiptRequest(to: Jid, body: String, messageId: String): XmppResult<Unit>

    public suspend fun sendReceivedReceipt(to: Jid, receiptId: String): XmppResult<Unit>
}

public class DefaultXmppReceiptService(
    private val client: KmpXmppClient,
) : XmppReceiptService {

    override suspend fun sendMessageWithReceiptRequest(
        to: Jid,
        body: String,
        messageId: String,
    ): XmppResult<Unit> {
        val error = validateChatInput(to, body, messageId)
        if (error != null) {
            return XmppResult.Failure(error)
        }

        val escapedTo = escapeXml(to.toString())
        val escapedBody = escapeXml(body)
        val escapedMessageId = escapeXml(messageId)
        val stanza = buildString {
            append("<message to='")
            append(escapedTo)
            append("' type='chat' id='")
            append(escapedMessageId)
            append("'><body>")
            append(escapedBody)
            append("</body><request xmlns='")
            append(RECEIPTS_NAMESPACE)
            append("'/></message>")
        }
        return client.sendStanza(stanza)
    }

    override suspend fun sendReceivedReceipt(to: Jid, receiptId: String): XmppResult<Unit> {
        if (to.domain.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Recipient JID domain cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = false,
                ),
            )
        }
        if (receiptId.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Receipt id cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        val stanza = "<message to='${escapeXml(to.toString())}'><received xmlns='$RECEIPTS_NAMESPACE' id='${escapeXml(receiptId)}'/></message>"
        return client.sendStanza(stanza)
    }

    private fun validateChatInput(to: Jid, body: String, messageId: String) =
        when {
            to.domain.isBlank() -> xmppErrorInvalidInput(
                message = "Recipient JID domain cannot be blank.",
                stage = XmppErrorStage.Messaging,
                recoverable = false,
            )
            body.isBlank() -> xmppErrorInvalidInput(
                message = "Message body cannot be blank.",
                stage = XmppErrorStage.Messaging,
                recoverable = true,
            )
            messageId.isBlank() -> xmppErrorInvalidInput(
                message = "Message id cannot be blank.",
                stage = XmppErrorStage.Messaging,
                recoverable = true,
            )
            else -> null
        }

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
