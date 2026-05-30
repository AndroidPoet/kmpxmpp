package io.github.androidpoet.kmpxmpp.xep0280.carbons

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppCarbonsServiceTest {
    @Test
    fun test_enableCarbons_whenCalled_sendsEnableIq() = runTest {
        val client = FakeCarbonsClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppCarbonsService(client)

        val result = service.enableCarbons()

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("<enable"))
        assertTrue(client.lastStanza!!.contains("urn:xmpp:carbons:2"))
    }

    @Test
    fun test_disableCarbons_whenCalled_sendsDisableIq() = runTest {
        val client = FakeCarbonsClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppCarbonsService(client)

        val result = service.disableCarbons()

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("<disable"))
    }
}

private class FakeCarbonsClient(
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
