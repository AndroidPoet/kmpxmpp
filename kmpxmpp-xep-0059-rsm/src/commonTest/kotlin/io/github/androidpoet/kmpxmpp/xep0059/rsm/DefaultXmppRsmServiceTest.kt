package io.github.androidpoet.kmpxmpp.xep0059.rsm

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppRsmServiceTest {
    @Test
    fun test_requestPage_whenValidInput_injectsRsmSetNode() = runTest {
        val client = FakeRsmClient(XmppResult.Success(Unit))
        val service = DefaultXmppRsmService(client)

        val result = service.requestPage(
            queryXml = "<iq><query xmlns='urn:xmpp:mam:2'></query></iq>",
            page = XmppRsmPage(max = 20, after = "msg-1"),
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("protocol/rsm"))
        assertTrue(client.lastStanza!!.contains("<max>20</max>"))
    }

    @Test
    fun test_requestPage_whenAfterAndBeforeBothSet_returnsFailure() = runTest {
        val service = DefaultXmppRsmService(FakeRsmClient(XmppResult.Success(Unit)))

        val result = service.requestPage(
            queryXml = "<iq><query xmlns='urn:xmpp:mam:2'></query></iq>",
            page = XmppRsmPage(after = "a", before = "b"),
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
    }
}

private class FakeRsmClient(private val sendResult: XmppResult<Unit>) : KmpXmppClient {
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
