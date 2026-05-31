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

    @Test
    fun test_inviteUser_whenValidInput_sendsMucUserInviteMessage() = runTest {
        val client = FakeMucClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppMucService(client)

        val result = service.inviteUser(
            room = Jid(local = "room", domain = "conference.example.com"),
            invitee = Jid(local = "bob", domain = "example.com"),
            reason = "join us",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("http://jabber.org/protocol/muc#user"))
        assertTrue(client.lastStanza!!.contains("<invite"))
        assertTrue(client.lastStanza!!.contains("join us"))
    }

    @Test
    fun test_parseOccupantPresence_whenJoinPresenceValid_returnsParsedPresence() {
        val service = DefaultXmppMucService(FakeMucClient(sendResult = XmppResult.Success(Unit)))
        val xml = """
            <presence from='room@conference.example.com/alice'>
              <x xmlns='http://jabber.org/protocol/muc#user'/>
            </presence>
        """.trimIndent()

        val parsed = service.parseOccupantPresence(xml)

        assertIs<XmppResult.Success<MucOccupantPresence>>(parsed)
        assertEquals("room", parsed.value.room.local)
        assertEquals("conference.example.com", parsed.value.room.domain)
        assertEquals("alice", parsed.value.nickname)
        assertTrue(parsed.value.joined)
    }

    @Test
    fun test_parseOccupantPresence_whenFromMissing_returnsFailure() {
        val service = DefaultXmppMucService(FakeMucClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<presence><x xmlns='http://jabber.org/protocol/muc#user'/></presence>"

        val parsed = service.parseOccupantPresence(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals("MUC presence stanza missing from attribute.", parsed.error.message)
    }

    @Test
    fun test_parseGroupMessage_whenValidGroupchat_returnsParsedMessage() {
        val service = DefaultXmppMucService(FakeMucClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<message from='room@conference.example.com/alice' type='groupchat' id='m-1'><body>Hello team</body></message>"

        val parsed = service.parseGroupMessage(xml)

        assertIs<XmppResult.Success<MucGroupMessage>>(parsed)
        assertEquals("room", parsed.value.room.local)
        assertEquals("alice", parsed.value.fromNickname)
        assertEquals("Hello team", parsed.value.body)
        assertEquals("m-1", parsed.value.messageId)
    }

    @Test
    fun test_parseGroupMessage_whenTypeNotGroupchat_returnsFailure() {
        val service = DefaultXmppMucService(FakeMucClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<message from='room@conference.example.com/alice' type='chat'><body>hello</body></message>"

        val parsed = service.parseGroupMessage(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals("MUC group message must be type='groupchat'.", parsed.error.message)
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
