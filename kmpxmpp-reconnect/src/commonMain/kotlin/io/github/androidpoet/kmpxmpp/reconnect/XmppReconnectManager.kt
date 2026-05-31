package io.github.androidpoet.kmpxmpp.reconnect

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.XmppRetryPolicy
import io.github.androidpoet.kmpxmpp.core.flatMap
import io.github.androidpoet.kmpxmpp.core.retryXmppResult

public data class XmppReconnectCredentials(
    val jid: Jid,
    val password: String,
)

public interface XmppReconnectManager {
    public suspend fun reconnect(): XmppResult<Unit>

    public suspend fun reconnectWithAuthentication(credentials: XmppReconnectCredentials): XmppResult<Unit>
}

public class DefaultXmppReconnectManager(
    private val client: KmpXmppClient,
    private val retryPolicy: XmppRetryPolicy = XmppRetryPolicy(maxAttempts = 3),
) : XmppReconnectManager {

    override suspend fun reconnect(): XmppResult<Unit> =
        retryXmppResult(policy = retryPolicy) {
            client.connect()
        }

    override suspend fun reconnectWithAuthentication(credentials: XmppReconnectCredentials): XmppResult<Unit> =
        retryXmppResult(policy = retryPolicy) {
            client.connect().flatMap {
                client.authenticate(jid = credentials.jid, password = credentials.password)
            }
        }
}
