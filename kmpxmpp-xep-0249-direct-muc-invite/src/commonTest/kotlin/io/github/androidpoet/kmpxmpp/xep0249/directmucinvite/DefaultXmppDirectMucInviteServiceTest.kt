package io.github.androidpoet.kmpxmpp.xep0249.directmucinvite

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppDirectMucInviteServiceTest {
    @Test
    fun test_invite_whenValidInput_sendsConferenceInviteStanza() = runTest {
        val client = FakeInviteClient(XmppResult.Success(Unit))
        val service = DefaultXmppDirectMucInviteService(client)

        val result = service.invite(
            to = Jid(local = "alice", domain = "example.com"),
            room = Jid(local = "team", domain = "conference.example.com"),
            reason = "Join us",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("jabber:x:conference"))
        assertTrue(client.lastStanza!!.contains("reason='Join us'"))
    }

    @Test
    fun test_invite_whenReasonBlank_returnsFailure() = runTest {
        val service = DefaultXmppDirectMucInviteService(FakeInviteClient(XmppResult.Success(Unit)))

        val result = service.invite(
            to = Jid(local = "alice", domain = "example.com"),
            room = Jid(local = "team", domain = "conference.example.com"),
            reason = " ",
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
    }
}

private class FakeInviteClient(private val sendResult: XmppResult<Unit>) : KmpXmppClient {
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
