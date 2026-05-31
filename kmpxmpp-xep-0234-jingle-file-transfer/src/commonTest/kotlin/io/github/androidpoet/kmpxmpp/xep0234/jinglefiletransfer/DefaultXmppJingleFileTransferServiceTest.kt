package io.github.androidpoet.kmpxmpp.xep0234.jinglefiletransfer

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppJingleFileTransferServiceTest {
    @Test
    fun test_initiateFileTransfer_whenValidInput_sendsJingleIq() = runTest {
        val client = FakeJingleClient(XmppResult.Success(Unit))
        val service = DefaultXmppJingleFileTransferService(client)

        val result = service.initiateFileTransfer(
            to = Jid(local = "bob", domain = "example.com"),
            sid = "sid-1",
            fileName = "photo.jpg",
            fileSize = 1024,
            requestId = "j-1",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("urn:xmpp:jingle:1"))
        assertTrue(client.lastStanza!!.contains("file-transfer:5"))
    }

    @Test
    fun test_initiateFileTransfer_whenFileSizeInvalid_returnsFailure() = runTest {
        val service = DefaultXmppJingleFileTransferService(FakeJingleClient(XmppResult.Success(Unit)))

        val result = service.initiateFileTransfer(
            to = Jid(local = "bob", domain = "example.com"),
            sid = "sid-1",
            fileName = "photo.jpg",
            fileSize = 0,
            requestId = "j-1",
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
    }

    @Test
    fun test_parseFileTransferInitiation_whenValidStanza_returnsParsedDetails() {
        val service = DefaultXmppJingleFileTransferService(FakeJingleClient(XmppResult.Success(Unit)))
        val xml = """
            <iq type='set' id='j-1'>
              <jingle xmlns='urn:xmpp:jingle:1' action='session-initiate' sid='sid-1'>
                <content creator='initiator' name='file'>
                  <description xmlns='urn:xmpp:jingle:apps:file-transfer:5'>
                    <file><name>photo.jpg</name><size>1024</size></file>
                  </description>
                </content>
              </jingle>
            </iq>
        """.trimIndent()

        val parsed = service.parseFileTransferInitiation(xml)

        assertIs<XmppResult.Success<ParsedJingleFileTransfer>>(parsed)
        assertEquals("sid-1", parsed.value.sid)
        assertEquals("photo.jpg", parsed.value.fileName)
        assertEquals(1024L, parsed.value.fileSize)
    }

    @Test
    fun test_parseFileTransferInitiation_whenMissingSid_returnsFailure() {
        val service = DefaultXmppJingleFileTransferService(FakeJingleClient(XmppResult.Success(Unit)))
        val xml = "<jingle xmlns='urn:xmpp:jingle:1'><description xmlns='urn:xmpp:jingle:apps:file-transfer:5'><file><name>x</name><size>1</size></file></description></jingle>"

        val parsed = service.parseFileTransferInitiation(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals(XmppErrorCode.ParsingFailed, parsed.error.code)
    }

    @Test
    fun test_validateFileTransferResponse_whenMatchingId_returnsSuccess() {
        val service = DefaultXmppJingleFileTransferService(FakeJingleClient(XmppResult.Success(Unit)))
        val xml = "<iq type='result' id='j-1'/>"

        val validated = service.validateFileTransferResponse(xml, requestId = "j-1")

        assertIs<XmppResult.Success<Unit>>(validated)
    }
}

private class FakeJingleClient(private val sendResult: XmppResult<Unit>) : KmpXmppClient {
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
