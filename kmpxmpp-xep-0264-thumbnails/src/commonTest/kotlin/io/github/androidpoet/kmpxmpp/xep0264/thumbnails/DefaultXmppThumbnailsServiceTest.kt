package io.github.androidpoet.kmpxmpp.xep0264.thumbnails

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppThumbnailsServiceTest {
    @Test
    fun test_sendThumbnailReference_whenValidInput_sendsThumbnailStanza() = runTest {
        val client = FakeThumbnailsClient(XmppResult.Success(Unit))
        val service = DefaultXmppThumbnailsService(client)

        val result = service.sendThumbnailReference(
            to = Jid(local = "bob", domain = "example.com"),
            mediaCid = "cid:media1",
            thumbnailCid = "cid:thumb1",
            mimeType = "image/jpeg",
            width = 120,
            height = 90,
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("urn:xmpp:thumbs:1"))
        assertTrue(client.lastStanza!!.contains("width='120'"))
    }

    @Test
    fun test_sendThumbnailReference_whenInvalidSize_returnsFailure() = runTest {
        val service = DefaultXmppThumbnailsService(FakeThumbnailsClient(XmppResult.Success(Unit)))

        val result = service.sendThumbnailReference(
            to = Jid(local = "bob", domain = "example.com"),
            mediaCid = "cid:media1",
            thumbnailCid = "cid:thumb1",
            mimeType = "image/jpeg",
            width = 0,
            height = 90,
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
    }
}

private class FakeThumbnailsClient(private val sendResult: XmppResult<Unit>) : KmpXmppClient {
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
