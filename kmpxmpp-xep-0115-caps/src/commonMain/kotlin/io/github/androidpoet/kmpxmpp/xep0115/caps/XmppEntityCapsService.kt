package io.github.androidpoet.kmpxmpp.xep0115.caps

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val CAPS_NAMESPACE: String = "http://jabber.org/protocol/caps"

public data class XmppEntityCaps(
    val node: String,
    val hash: String,
    val ver: String,
)

public interface XmppEntityCapsService {
    public suspend fun advertiseCaps(caps: XmppEntityCaps): XmppResult<Unit>

    public fun parsePresenceCaps(xml: String): XmppResult<XmppEntityCaps>

    public fun validateCapsPresence(xml: String): XmppResult<Unit>
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

    override fun parsePresenceCaps(xml: String): XmppResult<XmppEntityCaps> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Caps presence XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        if (tags.none { it.name == "presence" }) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Caps payload must contain <presence/> stanza.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val cTag = tags.firstOrNull { it.name == "c" && it.attributes["xmlns"] == CAPS_NAMESPACE }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Caps presence missing <c xmlns='http://jabber.org/protocol/caps'/>.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )

        val node = cTag.attributes["node"]?.trim()?.takeIf { it.isNotBlank() }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Caps presence missing non-blank node.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val hash = cTag.attributes["hash"]?.trim()?.takeIf { it.isNotBlank() }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Caps presence missing non-blank hash.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val ver = cTag.attributes["ver"]?.trim()?.takeIf { it.isNotBlank() }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Caps presence missing non-blank ver.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )

        return XmppResult.Success(XmppEntityCaps(node = node, hash = hash, ver = ver))
    }

    override fun validateCapsPresence(xml: String): XmppResult<Unit> =
        when (val parsed = parsePresenceCaps(xml)) {
            is XmppResult.Success -> XmppResult.Success(Unit)
            is XmppResult.Failure -> parsed
        }

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
