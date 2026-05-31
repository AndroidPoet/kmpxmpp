package io.github.androidpoet.kmpxmpp.xep0357.push

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val PUSH_NAMESPACE: String = "urn:xmpp:push:0"
private const val XDATA_NAMESPACE: String = "jabber:x:data"

public data class XmppPushFormField(
    val variable: String,
    val value: String,
)

public data class ParsedPushDirective(
    val action: String,
    val serviceJid: String,
    val node: String,
)

public interface XmppPushService {
    public suspend fun enablePush(
        pushService: Jid,
        node: String,
        requestId: String,
        publishOptions: List<XmppPushFormField> = emptyList(),
    ): XmppResult<Unit>

    public suspend fun disablePush(pushService: Jid, node: String, requestId: String): XmppResult<Unit>

    public fun parsePushDirective(xml: String): XmppResult<ParsedPushDirective>

    public fun validatePushIqResult(xml: String, requestId: String): XmppResult<Unit>
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

    override fun parsePushDirective(xml: String): XmppResult<ParsedPushDirective> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput("Push directive XML cannot be blank.", XmppErrorStage.Messaging, true),
            )
        }
        val directiveTag = XmppXmlMiniParser.parseStartTags(xml).firstOrNull { tag ->
            (tag.name == "enable" || tag.name == "disable") && tag.attributes["xmlns"] == PUSH_NAMESPACE
        }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Push directive must contain <enable/> or <disable/> in urn:xmpp:push:0 namespace.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val action = directiveTag.name
        val jid = directiveTag.attributes["jid"]?.trim()?.takeIf { it.isNotBlank() }
            ?: return XmppResult.Failure(
                xmppErrorParsing("Push directive missing non-blank jid attribute.", XmppErrorStage.Messaging, true),
            )
        val node = directiveTag.attributes["node"]?.trim()?.takeIf { it.isNotBlank() }
            ?: return XmppResult.Failure(
                xmppErrorParsing("Push directive missing non-blank node attribute.", XmppErrorStage.Messaging, true),
            )
        return XmppResult.Success(ParsedPushDirective(action = action, serviceJid = jid, node = node))
    }

    override fun validatePushIqResult(xml: String, requestId: String): XmppResult<Unit> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput("Push IQ result XML cannot be blank.", XmppErrorStage.Messaging, true),
            )
        }
        val iqTag = XmppXmlMiniParser.parseStartTags(xml).firstOrNull { it.name == "iq" }
        if (!iqTag?.attributes?.get("type").equals("result", ignoreCase = true)) {
            return XmppResult.Failure(
                xmppErrorParsing("Push response must be IQ type='result'.", XmppErrorStage.Messaging, true),
            )
        }
        if (iqTag?.attributes?.get("id") != requestId) {
            return XmppResult.Failure(
                xmppErrorParsing("Push response id does not match request id.", XmppErrorStage.Messaging, true),
            )
        }
        return XmppResult.Success(Unit)
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
