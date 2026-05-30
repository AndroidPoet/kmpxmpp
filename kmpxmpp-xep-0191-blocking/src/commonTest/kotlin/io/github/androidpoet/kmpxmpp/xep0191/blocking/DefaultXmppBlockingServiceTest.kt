package io.github.androidpoet.kmpxmpp.xep0191.blocking

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppBlockingServiceTest {
    @Test
    fun test_block_whenValidList_sendsBlockCommand() = runTest {
        val client = FakeBlockingClient(XmppResult.Success(Unit))
        val service = DefaultXmppBlockingService(client)

        val result = service.block(
            jids = listOf(Jid(local = "spam", domain = "example.com")),
            requestId = "blk-1",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("urn:xmpp:blocking"))
        assertTrue(client.lastStanza!!.contains("<block"))
    }

    @Test
    fun test_unblock_whenEmptyList_returnsFailure() = runTest {
        val service = DefaultXmppBlockingService(FakeBlockingClient(XmppResult.Success(Unit)))

        val result = service.unblock(jids = emptyList(), requestId = "ub-1")

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
    }
}

private class FakeBlockingClient(private val sendResult: XmppResult<Unit>) : KmpXmppClient {
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
