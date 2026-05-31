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

    @Test
    fun test_parseInvite_whenValidInvite_returnsParsedFields() {
        val service = DefaultXmppDirectMucInviteService(FakeInviteClient(XmppResult.Success(Unit)))
        val xml = """
            <message from='owner@example.com' to='alice@example.com'>
              <x xmlns='jabber:x:conference' jid='team@conference.example.com'
                 reason='Join us'
                 password='secret'
                 thread='t-1'
                 continue='true'/>
            </message>
        """.trimIndent()

        val parsed = service.parseInvite(xml)

        assertIs<XmppResult.Success<ParsedDirectMucInvite>>(parsed)
        assertEquals("owner", parsed.value.inviter?.local)
        assertEquals("alice", parsed.value.invitee?.local)
        assertEquals("team", parsed.value.room.local)
        assertEquals("conference.example.com", parsed.value.room.domain)
        assertEquals("Join us", parsed.value.reason)
        assertEquals("secret", parsed.value.password)
        assertEquals("t-1", parsed.value.thread)
        assertEquals(true, parsed.value.continueFlag)
    }

    @Test
    fun test_parseInvite_whenConferenceElementMissing_returnsFailure() {
        val service = DefaultXmppDirectMucInviteService(FakeInviteClient(XmppResult.Success(Unit)))
        val xml = "<message from='owner@example.com' to='alice@example.com'/>"

        val parsed = service.parseInvite(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals(
            "Direct MUC invite stanza missing <x xmlns='jabber:x:conference'/> element.",
            parsed.error.message,
        )
    }

    @Test
    fun test_parseInvite_whenRoomJidInvalid_returnsFailure() {
        val service = DefaultXmppDirectMucInviteService(FakeInviteClient(XmppResult.Success(Unit)))
        val xml = "<message><x xmlns='jabber:x:conference' jid='conference.example.com'/></message>"

        val parsed = service.parseInvite(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals("Direct MUC invite room jid is invalid.", parsed.error.message)
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
