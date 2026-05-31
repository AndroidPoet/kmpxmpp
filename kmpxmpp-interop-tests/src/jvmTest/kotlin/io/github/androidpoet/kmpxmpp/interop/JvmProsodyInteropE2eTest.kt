package io.github.androidpoet.kmpxmpp.interop

import io.github.androidpoet.kmpxmpp.client.DefaultKmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.stream.XmppSessionConfig
import io.github.androidpoet.kmpxmpp.stream.XmppSessionOrchestrator
import io.github.androidpoet.kmpxmpp.tcp.createTcpXmppTransport
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertIs

class JvmProsodyInteropE2eTest {
    @Test
    fun test_client_overRealProsody_connectAuthenticateSendDisconnect_returnsSuccess() = runBlocking {
        if (System.getenv("KMPXMPP_RUN_DOCKER_E2E") != "true") {
            return@runBlocking
        }

        val transport = createTcpXmppTransport()
        val orchestrator = XmppSessionOrchestrator(
            config = XmppSessionConfig(
                host = "localhost",
                port = 5222,
                tlsInitiallyActive = false,
            ),
            transport = transport,
        )
        val client = DefaultKmpXmppClient(
            streamEngine = orchestrator,
            transport = transport,
        )

        assertIs<XmppResult.Success<Unit>>(client.connect())
        assertIs<XmppResult.Success<Unit>>(
            client.authenticate(
                jid = Jid(local = "alice", domain = "localhost"),
                password = "strong-password",
            ),
        )
        assertIs<XmppResult.Success<Unit>>(
            client.sendStanza("<message to='bob@localhost' type='chat'><body>ping</body></message>"),
        )
        assertIs<XmppResult.Success<Unit>>(client.disconnect())
    }
}
