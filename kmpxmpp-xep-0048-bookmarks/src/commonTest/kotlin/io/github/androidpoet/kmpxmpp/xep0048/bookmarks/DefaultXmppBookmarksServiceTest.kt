package io.github.androidpoet.kmpxmpp.xep0048.bookmarks

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppBookmarksServiceTest {
    @Test
    fun test_storeBookmarks_whenValidBookmarks_sendsPrivateStorageIq() = runTest {
        val client = FakeBookmarksClient(XmppResult.Success(Unit))
        val service = DefaultXmppBookmarksService(client)

        val result = service.storeBookmarks(
            bookmarks = listOf(
                XmppBookmark(
                    room = Jid(local = "team", domain = "conference.example.com"),
                    name = "Team Room",
                    autojoin = true,
                    nick = "alice",
                ),
            ),
            requestId = "bm-1",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("jabber:iq:private"))
        assertTrue(client.lastStanza!!.contains("storage:bookmarks"))
    }

    @Test
    fun test_storeBookmarks_whenRequestIdBlank_returnsFailure() = runTest {
        val service = DefaultXmppBookmarksService(FakeBookmarksClient(XmppResult.Success(Unit)))

        val result = service.storeBookmarks(
            bookmarks = emptyList(),
            requestId = " ",
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
    }

    @Test
    fun test_parseStoredBookmarks_whenValidPayload_returnsBookmarks() {
        val service = DefaultXmppBookmarksService(FakeBookmarksClient(XmppResult.Success(Unit)))
        val xml = """
            <iq type='result' id='bm-get-1'>
              <query xmlns='jabber:iq:private'>
                <storage xmlns='storage:bookmarks'>
                  <conference jid='team@conference.example.com' name='Team Room' autojoin='true'>
                    <nick>alice</nick>
                  </conference>
                </storage>
              </query>
            </iq>
        """.trimIndent()

        val parsed = service.parseStoredBookmarks(xml)

        assertIs<XmppResult.Success<List<XmppBookmark>>>(parsed)
        assertEquals(1, parsed.value.size)
        assertEquals("team", parsed.value.first().room.local)
        assertEquals("conference.example.com", parsed.value.first().room.domain)
        assertEquals("Team Room", parsed.value.first().name)
        assertEquals("alice", parsed.value.first().nick)
        assertTrue(parsed.value.first().autojoin)
    }

    @Test
    fun test_parseStoredBookmarks_whenStorageMissing_returnsFailure() {
        val service = DefaultXmppBookmarksService(FakeBookmarksClient(XmppResult.Success(Unit)))
        val xml = "<iq type='result'><query xmlns='jabber:iq:private'/></iq>"

        val parsed = service.parseStoredBookmarks(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals(XmppErrorCode.ParsingFailed, parsed.error.code)
    }

    @Test
    fun test_validateStoreBookmarksRequest_whenValidRequest_returnsId() {
        val service = DefaultXmppBookmarksService(FakeBookmarksClient(XmppResult.Success(Unit)))
        val xml = """
            <iq type='set' id='bm-1'>
              <query xmlns='jabber:iq:private'>
                <storage xmlns='storage:bookmarks'>
                  <conference jid='team@conference.example.com' autojoin='false'/>
                </storage>
              </query>
            </iq>
        """.trimIndent()

        val validated = service.validateStoreBookmarksRequest(xml)

        assertIs<XmppResult.Success<String>>(validated)
        assertEquals("bm-1", validated.value)
    }

    @Test
    fun test_validateStoreBookmarksRequest_whenWrongIqType_returnsFailure() {
        val service = DefaultXmppBookmarksService(FakeBookmarksClient(XmppResult.Success(Unit)))
        val xml = "<iq type='get' id='bm-1'><query xmlns='jabber:iq:private'><storage xmlns='storage:bookmarks'/></query></iq>"

        val validated = service.validateStoreBookmarksRequest(xml)

        assertIs<XmppResult.Failure>(validated)
        assertEquals(XmppErrorCode.ParsingFailed, validated.error.code)
    }
}

private class FakeBookmarksClient(private val sendResult: XmppResult<Unit>) : KmpXmppClient {
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
