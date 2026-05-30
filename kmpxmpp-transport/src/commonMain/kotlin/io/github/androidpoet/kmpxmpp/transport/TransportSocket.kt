package io.github.androidpoet.kmpxmpp.transport

public interface TransportSocket {
    public suspend fun connect(host: String, port: Int)
    public suspend fun write(data: String)
    public suspend fun read(): String
    public suspend fun close()
}
