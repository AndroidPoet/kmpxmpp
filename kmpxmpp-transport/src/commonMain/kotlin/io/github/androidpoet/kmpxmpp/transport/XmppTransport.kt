package io.github.androidpoet.kmpxmpp.transport

import io.github.androidpoet.kmpxmpp.core.KmpXmppResult

public interface XmppTransport {
    public suspend fun connect(host: String, port: Int): KmpXmppResult<Unit>
    public suspend fun write(data: String): KmpXmppResult<Unit>
    public suspend fun read(): KmpXmppResult<String>
    public suspend fun close(): KmpXmppResult<Unit>
}
