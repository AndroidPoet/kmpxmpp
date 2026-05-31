package io.github.androidpoet.kmpxmpp.xep0384.omemo

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val OMEMO_NAMESPACE: String = "eu.siacs.conversations.axolotl"

public data class ParsedOmemoEnvelope(
    val sid: Int,
    val encryptedKeyBase64: String,
    val ivBase64: String,
    val payloadBase64: String,
)

public interface XmppOmemoService {
    public suspend fun sendEncryptedMessage(
        to: Jid,
        encryptedPayloadBase64: String,
        sid: Int,
        ivBase64: String,
        payloadBase64: String,
    ): XmppResult<Unit>

    public fun parseOmemoEnvelope(xml: String): XmppResult<ParsedOmemoEnvelope>

    public fun validateOmemoEnvelope(xml: String): XmppResult<Unit>
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

    override fun parseOmemoEnvelope(xml: String): XmppResult<ParsedOmemoEnvelope> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput("OMEMO envelope XML cannot be blank.", XmppErrorStage.Messaging, true),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        val hasOmemoNamespace = tags.any { it.attributes["xmlns"] == OMEMO_NAMESPACE }
        if (!hasOmemoNamespace) {
            return XmppResult.Failure(
                xmppErrorParsing("OMEMO envelope missing expected namespace.", XmppErrorStage.Messaging, true),
            )
        }
        val headerTag = tags.firstOrNull { it.name == "header" }
            ?: return XmppResult.Failure(
                xmppErrorParsing("OMEMO envelope missing <header sid='...'>.", XmppErrorStage.Messaging, true),
            )
        val sid = headerTag.attributes["sid"]?.toIntOrNull()
            ?: return XmppResult.Failure(
                xmppErrorParsing("OMEMO sid must be numeric.", XmppErrorStage.Messaging, true),
            )
        if (sid <= 0) {
            return XmppResult.Failure(
                xmppErrorParsing("OMEMO sid must be greater than zero.", XmppErrorStage.Messaging, true),
            )
        }
        val key = XmppXmlMiniParser.textForTag(xml, "key")
            ?.takeIf { it.isNotBlank() }
            ?: return XmppResult.Failure(
                xmppErrorParsing("OMEMO envelope missing non-blank <key>.", XmppErrorStage.Messaging, true),
            )
        val iv = XmppXmlMiniParser.textForTag(xml, "iv")
            ?.takeIf { it.isNotBlank() }
            ?: return XmppResult.Failure(
                xmppErrorParsing("OMEMO envelope missing non-blank <iv>.", XmppErrorStage.Messaging, true),
            )
        val payload = XmppXmlMiniParser.textForTag(xml, "payload")
            ?.takeIf { it.isNotBlank() }
            ?: return XmppResult.Failure(
                xmppErrorParsing("OMEMO envelope missing non-blank <payload>.", XmppErrorStage.Messaging, true),
            )
        return XmppResult.Success(
            ParsedOmemoEnvelope(
                sid = sid,
                encryptedKeyBase64 = key,
                ivBase64 = iv,
                payloadBase64 = payload,
            ),
        )
    }

    override fun validateOmemoEnvelope(xml: String): XmppResult<Unit> =
        when (val parsed = parseOmemoEnvelope(xml)) {
            is XmppResult.Success -> XmppResult.Success(Unit)
            is XmppResult.Failure -> parsed
        }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

}
