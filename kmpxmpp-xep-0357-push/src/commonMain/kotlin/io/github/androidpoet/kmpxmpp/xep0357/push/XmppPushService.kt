package io.github.androidpoet.kmpxmpp.xep0357.push

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

private const val PUSH_NAMESPACE: String = "urn:xmpp:push:0"
private const val XDATA_NAMESPACE: String = "jabber:x:data"

public data class XmppPushFormField(
    val variable: String,
    val value: String,
)

public interface XmppPushService {
    public suspend fun enablePush(
        pushService: Jid,
        node: String,
        requestId: String,
        publishOptions: List<XmppPushFormField> = emptyList(),
    ): XmppResult<Unit>

    public suspend fun disablePush(pushService: Jid, node: String, requestId: String): XmppResult<Unit>
}

public class DefaultXmppPushService(
    private val client: KmpXmppClient,
) : XmppPushService {

    override suspend fun enablePush(
        pushService: Jid,
        node: String,
        requestId: String,
        publishOptions: List<XmppPushFormField>,
    ): XmppResult<Unit> {
        val inputError = validateCommonInput(pushService, node, requestId)
        if (inputError != null) return XmppResult.Failure(inputError)
        if (publishOptions.any { it.variable.isBlank() || it.value.isBlank() }) {
            return XmppResult.Failure(
                xmppErrorInvalidInput("Publish option variable and value cannot be blank.", XmppErrorStage.Messaging, true),
            )
        }

        val publishOptionsNode = if (publishOptions.isEmpty()) {
            ""
        } else {
            val fields = publishOptions.joinToString(separator = "") { field ->
                "<field var='${escapeXml(field.variable)}'><value>${escapeXml(field.value)}</value></field>"
            }
            "<x xmlns='$XDATA_NAMESPACE' type='submit'>$fields</x>"
        }

        val stanza = "<iq type='set' id='${escapeXml(requestId)}'><enable xmlns='$PUSH_NAMESPACE' jid='${escapeXml(pushService.toString())}' node='${escapeXml(node)}'>$publishOptionsNode</enable></iq>"
        return client.sendStanza(stanza)
    }

    override suspend fun disablePush(pushService: Jid, node: String, requestId: String): XmppResult<Unit> {
        val inputError = validateCommonInput(pushService, node, requestId)
        if (inputError != null) return XmppResult.Failure(inputError)

        val stanza = "<iq type='set' id='${escapeXml(requestId)}'><disable xmlns='$PUSH_NAMESPACE' jid='${escapeXml(pushService.toString())}' node='${escapeXml(node)}'/></iq>"
        return client.sendStanza(stanza)
    }

    private fun validateCommonInput(pushService: Jid, node: String, requestId: String) = when {
        pushService.domain.isBlank() -> xmppErrorInvalidInput("Push service JID domain cannot be blank.", XmppErrorStage.Messaging, false)
        node.isBlank() -> xmppErrorInvalidInput("Push node cannot be blank.", XmppErrorStage.Messaging, true)
        requestId.isBlank() -> xmppErrorInvalidInput("Request id cannot be blank.", XmppErrorStage.Messaging, true)
        else -> null
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
