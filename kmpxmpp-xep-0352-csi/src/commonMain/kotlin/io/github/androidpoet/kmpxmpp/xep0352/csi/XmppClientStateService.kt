package io.github.androidpoet.kmpxmpp.xep0352.csi

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val CSI_NAMESPACE: String = "urn:xmpp:csi:0"

public enum class XmppClientState {
    Active,
    Inactive,
}

public interface XmppClientStateService {
    public suspend fun markActive(): XmppResult<Unit>

    public suspend fun markInactive(): XmppResult<Unit>

    public fun parseClientState(xml: String): XmppResult<XmppClientState>

    public fun validateClientState(xml: String): XmppResult<Unit>
}

public class DefaultXmppClientStateService(
    private val client: KmpXmppClient,
) : XmppClientStateService {

    override suspend fun markActive(): XmppResult<Unit> =
        client.sendStanza("<active xmlns='$CSI_NAMESPACE'/>")

    override suspend fun markInactive(): XmppResult<Unit> =
        client.sendStanza("<inactive xmlns='$CSI_NAMESPACE'/>")

    override fun parseClientState(xml: String): XmppResult<XmppClientState> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "CSI stanza XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        if (tags.any { it.name == "active" && it.attributes["xmlns"] == CSI_NAMESPACE }) {
            return XmppResult.Success(XmppClientState.Active)
        }
        if (tags.any { it.name == "inactive" && it.attributes["xmlns"] == CSI_NAMESPACE }) {
            return XmppResult.Success(XmppClientState.Inactive)
        }
        return XmppResult.Failure(
            xmppErrorParsing(
                message = "CSI stanza must contain <active/> or <inactive/> in urn:xmpp:csi:0 namespace.",
                stage = XmppErrorStage.Messaging,
                recoverable = true,
            ),
        )
    }

    override fun validateClientState(xml: String): XmppResult<Unit> =
        when (val parsed = parseClientState(xml)) {
            is XmppResult.Success -> XmppResult.Success(Unit)
            is XmppResult.Failure -> parsed
        }
}
