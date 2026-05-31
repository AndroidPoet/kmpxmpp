package io.github.androidpoet.kmpxmpp.xep0184.receipts

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.parseJidOrNull
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val RECEIPTS_NAMESPACE: String = "urn:xmpp:receipts"

public data class XmppReceiptRequest(
    val messageId: String?,
    val from: Jid?,
)

public data class XmppReceivedReceipt(
    val receiptId: String,
    val from: Jid?,
)

public interface XmppReceiptService {
    public suspend fun sendMessageWithReceiptRequest(to: Jid, body: String, messageId: String): XmppResult<Unit>

    public suspend fun sendReceivedReceipt(to: Jid, receiptId: String): XmppResult<Unit>

    public fun parseReceiptRequest(xml: String): XmppResult<XmppReceiptRequest>

    public fun parseReceivedReceipt(xml: String): XmppResult<XmppReceivedReceipt>
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

    override fun parseReceiptRequest(xml: String): XmppResult<XmppReceiptRequest> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Receipt XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        if (tags.none { it.attributes["xmlns"] == RECEIPTS_NAMESPACE }) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Receipt stanza missing urn:xmpp:receipts namespace.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        if (tags.none { it.name == "request" && it.attributes["xmlns"] == RECEIPTS_NAMESPACE }) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Receipt request stanza missing <request/> element.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        val messageTag = tags.firstOrNull { it.name == "message" }
        val messageId = messageTag?.attributes?.get("id")
        val from = messageTag?.attributes?.get("from")?.let { parseJidOrNull(it) }

        return XmppResult.Success(
            XmppReceiptRequest(
                messageId = messageId,
                from = from,
            ),
        )
    }

    override fun parseReceivedReceipt(xml: String): XmppResult<XmppReceivedReceipt> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Receipt XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        if (tags.none { it.attributes["xmlns"] == RECEIPTS_NAMESPACE }) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Receipt stanza missing urn:xmpp:receipts namespace.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        val receivedTag = tags.firstOrNull { it.name == "received" && it.attributes["xmlns"] == RECEIPTS_NAMESPACE }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Received receipt stanza missing <received/> element.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val receiptId = receivedTag.attributes["id"]
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Received receipt stanza missing id attribute.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )

        val messageTag = tags.firstOrNull { it.name == "message" }
        val from = messageTag?.attributes?.get("from")?.let { parseJidOrNull(it) }

        return XmppResult.Success(
            XmppReceivedReceipt(
                receiptId = receiptId,
                from = from,
            ),
        )
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
