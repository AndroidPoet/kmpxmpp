package io.github.androidpoet.kmpxmpp.interop

import io.github.androidpoet.kmpxmpp.client.DefaultKmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.stream.XmppSessionConfig
import io.github.androidpoet.kmpxmpp.stream.XmppSessionOrchestrator
import io.github.androidpoet.kmpxmpp.tcp.createTcpXmppTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JvmTcpInteropSmokeTest {
    @Test
    fun test_client_overJvmTcp_connectAuthenticateSendDisconnect_returnsSuccess() = runBlocking {
        val captured = mutableListOf<String>()
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort

        val featuresXml =
            "<stream:features><mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><mechanism>PLAIN</mechanism></mechanisms></stream:features>"

        val serverJob = async(Dispatchers.IO) {
            serverSocket.use { socketServer ->
                socketServer.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                    val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)

                    val streamOpen = reader.readLine()
                    if (streamOpen != null) {
                        captured += streamOpen
                    }
                    writer.write("$featuresXml\n")
                    writer.flush()

                    try {
                        socket.soTimeout = 5_000
                        val auth = reader.readLine()
                        if (auth != null) {
                            captured += auth
                            writer.write("<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>\n")
                            writer.flush()
                        }

                        val streamReopen = reader.readLine()
                        if (streamReopen != null) {
                            captured += streamReopen
                            writer.write("$featuresXml\n")
                            writer.flush()
                        }

                        val bind = reader.readLine()
                        if (bind != null) {
                            captured += bind
                            writer.write("<iq type='result' id='bind-1'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'><jid>alice@example.com/kmpxmpp</jid></bind></iq>\n")
                            writer.flush()
                        }

                        val message = reader.readLine()
                        if (message != null) {
                            captured += message
                        }
                    } catch (_: SocketTimeoutException) {
                    }
                }
            }
        }

        val transport = createTcpXmppTransport()
        val orchestrator = XmppSessionOrchestrator(
            config = XmppSessionConfig(
                host = "127.0.0.1",
                port = port,
                tlsInitiallyActive = true,
            ),
            transport = transport,
        )
        val client = DefaultKmpXmppClient(
            streamEngine = orchestrator,
            transport = transport,
        )

        val connectResult = client.connect()
        assertIs<XmppResult.Success<Unit>>(connectResult)

        val authResult = client.authenticate(
            jid = Jid(local = "alice", domain = "example.com"),
            password = "strong-password",
        )
        assertIs<XmppResult.Success<Unit>>(authResult)

        val sendResult = client.sendStanza("<message to='bob@example.com' type='chat'><body>ping</body></message>\n")
        assertIs<XmppResult.Success<Unit>>(sendResult)

        val disconnectResult = client.disconnect()
        assertIs<XmppResult.Success<Unit>>(disconnectResult)

        serverJob.await()

        assertEquals(5, captured.size)
        assertTrue(captured.first().contains("<stream:stream"))
        assertTrue(captured[1].contains("<auth"))
        assertTrue(captured[2].contains("<stream:stream"))
        assertTrue(captured[3].contains("<bind"))
        assertTrue(captured[4].contains("<message"))
        assertTrue(captured[4].contains("<body>ping</body>"))
    }
}
