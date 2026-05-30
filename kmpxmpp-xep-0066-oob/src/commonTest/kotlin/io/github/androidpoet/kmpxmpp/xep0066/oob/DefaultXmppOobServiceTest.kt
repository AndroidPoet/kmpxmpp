package io.github.androidpoet.kmpxmpp.xep0066.oob

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppOobServiceTest {
    @Test
    fun test_sendOobUrl_whenValidInput_sendsOobStanza() = runTest {
        val client = FakeOobClient(XmppResult.Success(Unit))
        val service = DefaultXmppOobService(client)

        val result = service.sendOobUrl(
            to = Jid(local = "alice", domain = "example.com"),
            url = "https://example.com/file.png",
            description = "Image",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("jabber:x:oob"))
        assertTrue(client.lastStanza!!.contains("https://example.com/file.png"))
    }

    @Test
    fun test_sendOobUrl_whenUrlBlank_returnsFailure() = runTest {
        val service = DefaultXmppOobService(FakeOobClient(XmppResult.Success(Unit)))

        val result = service.sendOobUrl(
            to = Jid(local = "alice", domain = "example.com"),
            url = " ",
            description = null,
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
    }
}

private class FakeOobClient(private val sendResult: XmppResult<Unit>) : KmpXmppClient {
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
