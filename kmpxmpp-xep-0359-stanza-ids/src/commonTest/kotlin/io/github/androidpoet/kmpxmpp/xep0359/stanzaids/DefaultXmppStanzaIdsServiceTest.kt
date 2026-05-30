package io.github.androidpoet.kmpxmpp.xep0359.stanzaids

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppStanzaIdsServiceTest {
    @Test
    fun test_sendOriginIdMessage_whenValidInput_sendsOriginIdStanza() = runTest {
        val client = FakeStanzaIdsClient(XmppResult.Success(Unit))
        val service = DefaultXmppStanzaIdsService(client)

        val result = service.sendOriginIdMessage(
            to = Jid(local = "a", domain = "example.com"),
            body = "hello",
            originId = "origin-1",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("origin-id"))
        assertTrue(client.lastStanza!!.contains("urn:xmpp:sid:0"))
    }

    @Test
    fun test_sendOriginIdMessage_whenOriginIdBlank_returnsFailure() = runTest {
        val service = DefaultXmppStanzaIdsService(FakeStanzaIdsClient(XmppResult.Success(Unit)))

        val result = service.sendOriginIdMessage(Jid(local = "a", domain = "example.com"), "hello", " ")

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
    }
}

private class FakeStanzaIdsClient(private val sendResult: XmppResult<Unit>) : KmpXmppClient {
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
