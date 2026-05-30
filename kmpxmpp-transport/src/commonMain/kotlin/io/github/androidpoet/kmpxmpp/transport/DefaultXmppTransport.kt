package io.github.androidpoet.kmpxmpp.transport

import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorTransport

public class DefaultXmppTransport(
    private val socket: TransportSocket,
) : XmppTransport {

    override suspend fun connect(host: String, port: Int): XmppResult<Unit> =
        runTransport(stage = XmppErrorStage.Connect) {
            socket.connect(host, port)
        }

    override suspend fun write(data: String): XmppResult<Unit> =
        runTransport(stage = XmppErrorStage.Messaging) {
            socket.write(data)
        }

    override suspend fun read(): XmppResult<String> =
        try {
            XmppResult.Success(socket.read())
        } catch (throwable: Throwable) {
            XmppResult.Failure(
                xmppErrorTransport(
                    message = throwable.message ?: "Transport read failed.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                    cause = throwable,
                ),
            )
        }

    override suspend fun close(): XmppResult<Unit> =
        runTransport(stage = XmppErrorStage.Disconnect) {
            socket.close()
        }

    private suspend fun runTransport(
        stage: XmppErrorStage,
        block: suspend () -> Unit,
    ): XmppResult<Unit> =
        try {
            block()
            XmppResult.Success(Unit)
        } catch (throwable: Throwable) {
            XmppResult.Failure(
                xmppErrorTransport(
                    message = throwable.message ?: "Transport operation failed.",
                    stage = stage,
                    recoverable = true,
                    cause = throwable,
                ),
            )
        }
}
