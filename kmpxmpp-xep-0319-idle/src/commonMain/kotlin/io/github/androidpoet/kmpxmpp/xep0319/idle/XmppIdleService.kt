package io.github.androidpoet.kmpxmpp.xep0319.idle

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val IDLE_NAMESPACE: String = "urn:xmpp:idle:1"

public data class XmppIdlePresence(
    val sinceIso8601: String,
)

public interface XmppIdleService {
    public suspend fun advertiseIdleSince(sinceIso8601: String): XmppResult<Unit>

    public fun parseIdlePresence(xml: String): XmppResult<XmppIdlePresence>

    public fun validateIdlePresence(xml: String): XmppResult<Unit>
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

    override fun parseIdlePresence(xml: String): XmppResult<XmppIdlePresence> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Idle presence XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        if (tags.none { it.name == "presence" }) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Idle payload must contain <presence/> stanza.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val idleTag = tags.firstOrNull { tag ->
            tag.name == "idle" && tag.attributes["xmlns"] == IDLE_NAMESPACE
        }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Idle presence missing <idle xmlns='urn:xmpp:idle:1'/>.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val since = idleTag.attributes["since"]?.trim()?.takeIf { it.isNotBlank() }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Idle presence missing non-blank since attribute.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        return XmppResult.Success(XmppIdlePresence(sinceIso8601 = since))
    }

    override fun validateIdlePresence(xml: String): XmppResult<Unit> =
        when (val parsed = parseIdlePresence(xml)) {
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
