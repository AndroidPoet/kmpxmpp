package io.github.androidpoet.kmpxmpp.reconnect

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.XmppRetryPolicy
import io.github.androidpoet.kmpxmpp.core.retryXmppResult

public interface XmppReconnectManager {
    public suspend fun reconnect(): XmppResult<Unit>
}

public class DefaultXmppReconnectManager(
    private val client: KmpXmppClient,
    private val retryPolicy: XmppRetryPolicy = XmppRetryPolicy(maxAttempts = 3),
) : XmppReconnectManager {

    override suspend fun reconnect(): XmppResult<Unit> =
        retryXmppResult(policy = retryPolicy) {
            client.connect()
        }
}
