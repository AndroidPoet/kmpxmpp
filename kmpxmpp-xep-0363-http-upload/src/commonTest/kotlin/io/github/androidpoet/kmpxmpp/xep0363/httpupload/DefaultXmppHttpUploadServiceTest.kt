package io.github.androidpoet.kmpxmpp.xep0363.httpupload

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppHttpUploadServiceTest {
    @Test
    fun test_requestUploadSlot_whenValidInput_sendsUploadRequestIq() = runTest {
        val client = FakeUploadClient(XmppResult.Success(Unit))
        val service = DefaultXmppHttpUploadService(client)

        val result = service.requestUploadSlot(
            uploadService = Jid(local = null, domain = "upload.example.com"),
            fileName = "photo.jpg",
            sizeBytes = 1024,
            contentType = "image/jpeg",
            requestId = "up-1",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("urn:xmpp:http:upload:0"))
        assertTrue(client.lastStanza!!.contains("filename='photo.jpg'"))
    }

    @Test
    fun test_requestUploadSlot_whenSizeInvalid_returnsFailure() = runTest {
        val service = DefaultXmppHttpUploadService(FakeUploadClient(XmppResult.Success(Unit)))

        val result = service.requestUploadSlot(
            uploadService = Jid(local = null, domain = "upload.example.com"),
            fileName = "photo.jpg",
            sizeBytes = 0,
            contentType = null,
            requestId = "up-2",
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
    }
}

private class FakeUploadClient(private val sendResult: XmppResult<Unit>) : KmpXmppClient {
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
