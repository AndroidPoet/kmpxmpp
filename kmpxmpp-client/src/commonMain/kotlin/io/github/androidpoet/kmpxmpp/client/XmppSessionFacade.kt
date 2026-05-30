package io.github.androidpoet.kmpxmpp.client

import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.flatMap

public interface XmppSessionFacade {
    public suspend fun openSession(jid: Jid, password: String): XmppResult<Unit>

    public suspend fun closeSession(): XmppResult<Unit>
}

public class DefaultXmppSessionFacade(
    private val client: KmpXmppClient,
) : XmppSessionFacade {

    override suspend fun openSession(jid: Jid, password: String): XmppResult<Unit> =
        client.connect().flatMap {
            client.authenticate(jid, password)
        }

    override suspend fun closeSession(): XmppResult<Unit> = client.disconnect()
}
