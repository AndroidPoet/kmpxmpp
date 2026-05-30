package io.github.androidpoet.kmpxmpp.xep0199.ping

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppPingServiceTest {
    @Test
    fun test_pingServer_whenValidRequestId_sendsPingIq() = runTest {
        val client = FakePingClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppPingService(client)

        val result = service.pingServer(requestId = "ping-1")

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("urn:xmpp:ping"))
        assertTrue(client.lastStanza!!.contains("id='ping-1'"))
    }

    @Test
    fun test_pingEntity_whenDomainBlank_returnsInvalidInputFailure() = runTest {
        val service = DefaultXmppPingService(FakePingClient(sendResult = XmppResult.Success(Unit)))

        val result = service.pingEntity(
            to = Jid(local = "a", domain = " "),
            requestId = "ping-2",
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
        assertEquals("Target JID domain cannot be blank.", result.error.message)
    }
}

private class FakePingClient(
    private val sendResult: XmppResult<Unit>,
) : KmpXmppClient {
    var sendCalls: Int = 0
    var lastStanza: String? = null

    override suspend fun connect(): XmppResult<Unit> = XmppResult.Success(Unit)

    override suspend fun authenticate(jid: Jid, password: String): XmppResult<Unit> = XmppResult.Success(Unit)

    override suspend fun sendStanza(rawXml: String): XmppResult<Unit> {
        sendCalls += 1
        lastStanza = rawXml
        return sendResult
    }

    override suspend fun disconnect(): XmppResult<Unit> = XmppResult.Success(Unit)
}
