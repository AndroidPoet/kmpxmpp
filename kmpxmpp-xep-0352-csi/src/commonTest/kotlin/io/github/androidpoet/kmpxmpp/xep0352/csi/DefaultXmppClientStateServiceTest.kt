package io.github.androidpoet.kmpxmpp.xep0352.csi

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppClientStateServiceTest {
    @Test
    fun test_markActive_whenCalled_sendsActiveStanza() = runTest {
        val client = FakeCsiClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppClientStateService(client)

        val result = service.markActive()

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("<active"))
        assertTrue(client.lastStanza!!.contains("urn:xmpp:csi:0"))
    }

    @Test
    fun test_markInactive_whenCalled_sendsInactiveStanza() = runTest {
        val client = FakeCsiClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppClientStateService(client)

        val result = service.markInactive()

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("<inactive"))
    }
}

private class FakeCsiClient(
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
