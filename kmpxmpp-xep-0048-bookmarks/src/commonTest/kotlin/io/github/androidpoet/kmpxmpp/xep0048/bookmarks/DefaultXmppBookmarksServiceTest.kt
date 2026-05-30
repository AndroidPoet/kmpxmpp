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
