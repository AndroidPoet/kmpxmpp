package io.github.androidpoet.kmpxmpp.im

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppError
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppPresenceServiceTest {
    @Test
    fun test_sendAvailable_whenNoStatus_sendsPresenceStanza() = runTest {
        val client = FakePresenceClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppPresenceService(client)

        val result = service.sendAvailable()

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals("<presence></presence>", client.lastStanza)
    }

    @Test
    fun test_sendUnavailable_whenStatusProvided_sendsEscapedStatus() = runTest {
        val client = FakePresenceClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppPresenceService(client)

        val result = service.sendUnavailable("Busy <now> & later")

        assertIs<XmppResult.Success<Unit>>(result)
        assertTrue(client.lastStanza!!.contains("type='unavailable'"))
        assertTrue(client.lastStanza!!.contains("Busy &lt;now&gt; &amp; later"))
    }

    @Test
    fun test_sendPresence_whenBlankStatus_returnsInvalidInputFailure() = runTest {
        val service = DefaultXmppPresenceService(FakePresenceClient(sendResult = XmppResult.Success(Unit)))

        val result = service.sendAvailable("   ")

        assertIs<XmppResult.Failure>(result)
        assertEquals("Presence status cannot be blank when provided.", result.error.message)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
        assertEquals(XmppErrorStage.Messaging, result.error.stage)
    }

    @Test
    fun test_sendPresence_whenClientSendFails_propagatesFailure() = runTest {
        val client = FakePresenceClient(
            sendResult = XmppResult.Failure(
                XmppError(
                    message = "presence-send-failed",
                    code = XmppErrorCode.TransportFailure,
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            ),
        )
        val service = DefaultXmppPresenceService(client)

        val result = service.sendUnavailable("Away")

        assertIs<XmppResult.Failure>(result)
        assertEquals("presence-send-failed", result.error.message)
    }
}

private class FakePresenceClient(
    private val sendResult: XmppResult<Unit>,
) : KmpXmppClient {
    var lastStanza: String? = null

    override suspend fun connect(): XmppResult<Unit> = XmppResult.Success(Unit)

    override suspend fun authenticate(jid: Jid, password: String): XmppResult<Unit> = XmppResult.Success(Unit)

    override suspend fun sendStanza(rawXml: String): XmppResult<Unit> {
        lastStanza = rawXml
        return sendResult
    }

    override suspend fun disconnect(): XmppResult<Unit> = XmppResult.Success(Unit)
}
