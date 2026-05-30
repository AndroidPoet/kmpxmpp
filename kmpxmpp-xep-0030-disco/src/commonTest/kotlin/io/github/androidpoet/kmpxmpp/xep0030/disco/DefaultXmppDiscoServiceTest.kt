package io.github.androidpoet.kmpxmpp.xep0030.disco

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppDiscoServiceTest {
    @Test
    fun test_requestInfo_whenValidInput_sendsDiscoInfoIq() = runTest {
        val client = FakeDiscoClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppDiscoService(client)

        val result = service.requestInfo(
            to = Jid(local = null, domain = "example.com"),
            node = "urn:xmpp:mam:2",
            requestId = "disco-1",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("disco#info"))
        assertTrue(client.lastStanza!!.contains("node='urn:xmpp:mam:2'"))
    }

    @Test
    fun test_requestItems_whenRequestIdBlank_returnsFailure() = runTest {
        val service = DefaultXmppDiscoService(FakeDiscoClient(sendResult = XmppResult.Success(Unit)))

        val result = service.requestItems(
            to = Jid(local = null, domain = "example.com"),
            node = null,
            requestId = " ",
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
        assertEquals("Request id cannot be blank.", result.error.message)
    }
}

private class FakeDiscoClient(
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
