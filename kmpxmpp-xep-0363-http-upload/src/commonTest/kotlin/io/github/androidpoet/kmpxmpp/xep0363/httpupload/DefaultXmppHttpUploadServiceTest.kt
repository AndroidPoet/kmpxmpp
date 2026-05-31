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

    @Test
    fun test_parseUploadSlotResult_whenValidResult_returnsParsedSlot() {
        val service = DefaultXmppHttpUploadService(FakeUploadClient(XmppResult.Success(Unit)))
        val xml = """
            <iq type='result' id='up-1'>
              <slot xmlns='urn:xmpp:http:upload:0'>
                <put url='https://upload.example.com/put/abc'>
                  <header name='Authorization'>Bearer token</header>
                </put>
                <get url='https://cdn.example.com/get/abc'/>
              </slot>
            </iq>
        """.trimIndent()

        val parsed = service.parseUploadSlotResult(xml)

        assertIs<XmppResult.Success<HttpUploadSlot>>(parsed)
        assertEquals("https://upload.example.com/put/abc", parsed.value.putUrl)
        assertEquals("https://cdn.example.com/get/abc", parsed.value.getUrl)
        assertEquals(1, parsed.value.headers.size)
        assertEquals("Authorization", parsed.value.headers.first().name)
    }

    @Test
    fun test_parseUploadSlotResult_whenWrongIqType_returnsFailure() {
        val service = DefaultXmppHttpUploadService(FakeUploadClient(XmppResult.Success(Unit)))
        val xml = "<iq type='error'><slot xmlns='urn:xmpp:http:upload:0'/></iq>"

        val parsed = service.parseUploadSlotResult(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals(XmppErrorCode.ParsingFailed, parsed.error.code)
    }

    @Test
    fun test_validateUploadSlotRequest_whenValidRequest_returnsId() {
        val service = DefaultXmppHttpUploadService(FakeUploadClient(XmppResult.Success(Unit)))
        val xml = """
            <iq type='get' id='req-1'>
              <request xmlns='urn:xmpp:http:upload:0' filename='photo.jpg' size='1024'/>
            </iq>
        """.trimIndent()

        val validated = service.validateUploadSlotRequest(xml)

        assertIs<XmppResult.Success<String>>(validated)
        assertEquals("req-1", validated.value)
    }

    @Test
    fun test_validateUploadSlotRequest_whenMissingNamespace_returnsFailure() {
        val service = DefaultXmppHttpUploadService(FakeUploadClient(XmppResult.Success(Unit)))
        val xml = "<iq type='get' id='req-1'><request/></iq>"

        val validated = service.validateUploadSlotRequest(xml)

        assertIs<XmppResult.Failure>(validated)
        assertEquals(XmppErrorCode.ParsingFailed, validated.error.code)
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
