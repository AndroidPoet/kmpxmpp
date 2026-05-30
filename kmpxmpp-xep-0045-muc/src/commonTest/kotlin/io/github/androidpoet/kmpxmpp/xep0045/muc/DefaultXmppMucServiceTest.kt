package io.github.androidpoet.kmpxmpp.xep0045.muc

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppMucServiceTest {
    @Test
    fun test_joinRoom_whenValidInput_sendsMucJoinPresence() = runTest {
        val client = FakeMucClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppMucService(client)

        val result = service.joinRoom(
            room = Jid(local = "room", domain = "conference.example.com"),
            nickname = "alice",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("http://jabber.org/protocol/muc"))
    }

    @Test
    fun test_leaveRoom_whenValidInput_sendsUnavailablePresence() = runTest {
        val client = FakeMucClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppMucService(client)

        val result = service.leaveRoom(
            room = Jid(local = "room", domain = "conference.example.com"),
            nickname = "alice",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("type='unavailable'"))
    }

    @Test
    fun test_sendGroupMessage_whenBodyBlank_returnsFailure() = runTest {
        val service = DefaultXmppMucService(FakeMucClient(sendResult = XmppResult.Success(Unit)))

        val result = service.sendGroupMessage(
            room = Jid(local = "room", domain = "conference.example.com"),
            body = " ",
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
        assertEquals("Group message body cannot be blank.", result.error.message)
    }
}

private class FakeMucClient(
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
