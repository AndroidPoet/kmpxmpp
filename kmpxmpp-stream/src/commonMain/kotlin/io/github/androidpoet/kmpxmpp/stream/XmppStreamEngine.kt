package io.github.androidpoet.kmpxmpp.stream

import io.github.androidpoet.kmpxmpp.core.KmpXmppResult

public interface XmppStreamEngine {
    public val state: XmppStreamState
    public suspend fun start(): KmpXmppResult<Unit>
    public suspend fun stop(): KmpXmppResult<Unit>
}
