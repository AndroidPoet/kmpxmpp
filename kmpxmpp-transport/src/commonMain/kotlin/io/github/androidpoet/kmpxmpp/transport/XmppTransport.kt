package io.github.androidpoet.kmpxmpp.transport

import io.github.androidpoet.kmpxmpp.core.XmppResult

public interface XmppTransport {
    public suspend fun connect(host: String, port: Int): XmppResult<Unit>
    public suspend fun write(data: String): XmppResult<Unit>
    public suspend fun read(): XmppResult<String>
    public suspend fun close(): XmppResult<Unit>
}
