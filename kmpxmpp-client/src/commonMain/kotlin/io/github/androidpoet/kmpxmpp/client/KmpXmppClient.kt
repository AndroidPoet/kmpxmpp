package io.github.androidpoet.kmpxmpp.client

import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppFeatureId
import io.github.androidpoet.kmpxmpp.core.XmppResult

public interface KmpXmppClient {
    public suspend fun connect(): XmppResult<Unit>
    public suspend fun authenticate(jid: Jid, password: String): XmppResult<Unit>
    public suspend fun sendStanza(rawXml: String): XmppResult<Unit>
    public suspend fun disconnect(): XmppResult<Unit>
    public fun supportsFeature(featureId: XmppFeatureId): Boolean = false
    public fun advertisedFeatures(): Set<XmppFeatureId> = emptySet()
}
