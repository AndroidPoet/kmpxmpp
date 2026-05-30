package io.github.androidpoet.kmpxmpp.stream

import io.github.androidpoet.kmpxmpp.core.XmppResult

public interface XmppStreamEngine {
    public val state: XmppStreamState
    public val sessionContext: XmppSessionContext?
    public suspend fun start(): XmppResult<Unit>
    public suspend fun stop(): XmppResult<Unit>
}
