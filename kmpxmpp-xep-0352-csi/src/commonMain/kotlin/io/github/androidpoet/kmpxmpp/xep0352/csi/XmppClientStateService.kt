package io.github.androidpoet.kmpxmpp.xep0352.csi

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.XmppResult

private const val CSI_NAMESPACE: String = "urn:xmpp:csi:0"

public interface XmppClientStateService {
    public suspend fun markActive(): XmppResult<Unit>

    public suspend fun markInactive(): XmppResult<Unit>
}

public class DefaultXmppClientStateService(
    private val client: KmpXmppClient,
) : XmppClientStateService {

    override suspend fun markActive(): XmppResult<Unit> =
        client.sendStanza("<active xmlns='$CSI_NAMESPACE'/>")

    override suspend fun markInactive(): XmppResult<Unit> =
        client.sendStanza("<inactive xmlns='$CSI_NAMESPACE'/>")
}
