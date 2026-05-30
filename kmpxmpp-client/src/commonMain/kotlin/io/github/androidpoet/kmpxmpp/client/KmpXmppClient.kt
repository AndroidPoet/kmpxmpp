package io.github.androidpoet.kmpxmpp.client

import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.KmpXmppResult

public interface KmpXmppClient {
    public suspend fun connect(): KmpXmppResult<Unit>
    public suspend fun authenticate(jid: Jid, password: String): KmpXmppResult<Unit>
    public suspend fun sendStanza(rawXml: String): KmpXmppResult<Unit>
    public suspend fun disconnect(): KmpXmppResult<Unit>
}
