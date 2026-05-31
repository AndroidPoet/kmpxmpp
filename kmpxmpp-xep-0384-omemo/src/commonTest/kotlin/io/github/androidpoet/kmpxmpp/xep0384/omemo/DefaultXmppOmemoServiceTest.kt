package io.github.androidpoet.kmpxmpp.xep0384.omemo

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppOmemoServiceTest {
    @Test
    fun test_sendEncryptedMessage_whenValidInput_sendsOmemoMessage() = runTest {
        val client = FakeOmemoClient(XmppResult.Success(Unit))
        val service = DefaultXmppOmemoService(client)

        val result = service.sendEncryptedMessage(
            to = Jid(local = "alice", domain = "example.com"),
            encryptedPayloadBase64 = "a2V5",
            sid = 123,
            ivBase64 = "aXY=",
            payloadBase64 = "cGF5bG9hZA==",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("axolotl"))
        assertTrue(client.lastStanza!!.contains("<payload>"))
    }

    @Test
    fun test_sendEncryptedMessage_whenSidInvalid_returnsFailure() = runTest {
        val service = DefaultXmppOmemoService(FakeOmemoClient(XmppResult.Success(Unit)))

        val result = service.sendEncryptedMessage(
            to = Jid(local = "alice", domain = "example.com"),
            encryptedPayloadBase64 = "a2V5",
            sid = 0,
            ivBase64 = "aXY=",
            payloadBase64 = "cGF5bG9hZA==",
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
    }

    @Test
    fun test_parseOmemoEnvelope_whenValidEnvelope_returnsParsedFields() {
        val service = DefaultXmppOmemoService(FakeOmemoClient(XmppResult.Success(Unit)))
        val xml = """
            <message type='chat'>
              <encrypted xmlns='eu.siacs.conversations.axolotl'>
                <header sid='123'>
                  <key rid='123'>a2V5</key>
                  <iv>aXY=</iv>
                </header>
                <payload>cGF5bG9hZA==</payload>
              </encrypted>
            </message>
        """.trimIndent()

        val parsed = service.parseOmemoEnvelope(xml)

        assertIs<XmppResult.Success<ParsedOmemoEnvelope>>(parsed)
        assertEquals(123, parsed.value.sid)
        assertEquals("a2V5", parsed.value.encryptedKeyBase64)
        assertEquals("aXY=", parsed.value.ivBase64)
        assertEquals("cGF5bG9hZA==", parsed.value.payloadBase64)
    }

    @Test
    fun test_parseOmemoEnvelope_whenMissingHeader_returnsFailure() {
        val service = DefaultXmppOmemoService(FakeOmemoClient(XmppResult.Success(Unit)))
        val xml = "<message><encrypted xmlns='eu.siacs.conversations.axolotl'><payload>x</payload></encrypted></message>"

        val parsed = service.parseOmemoEnvelope(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals(XmppErrorCode.ParsingFailed, parsed.error.code)
    }

    @Test
    fun test_parseOmemoEnvelope_whenNamespaceMissing_returnsFailure() {
        val service = DefaultXmppOmemoService(FakeOmemoClient(XmppResult.Success(Unit)))
        val xml = "<message><encrypted><header sid='1'><key rid='1'>a2V5</key><iv>aXY=</iv></header><payload>cA==</payload></encrypted></message>"

        val parsed = service.parseOmemoEnvelope(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals(XmppErrorCode.ParsingFailed, parsed.error.code)
    }

    @Test
    fun test_parseOmemoEnvelope_whenPrefixedTags_returnsParsedFields() {
        val service = DefaultXmppOmemoService(FakeOmemoClient(XmppResult.Success(Unit)))
        val xml = """
            <message type='chat'>
              <ax:encrypted xmlns:ax='eu.siacs.conversations.axolotl' xmlns='eu.siacs.conversations.axolotl'>
                <ax:header sid='42'>
                  <ax:key rid='42'>a2V5</ax:key>
                  <ax:iv>aXY=</ax:iv>
                </ax:header>
                <ax:payload>cA==</ax:payload>
              </ax:encrypted>
            </message>
        """.trimIndent()

        val parsed = service.parseOmemoEnvelope(xml)

        assertIs<XmppResult.Success<ParsedOmemoEnvelope>>(parsed)
        assertEquals(42, parsed.value.sid)
        assertEquals("a2V5", parsed.value.encryptedKeyBase64)
        assertEquals("aXY=", parsed.value.ivBase64)
        assertEquals("cA==", parsed.value.payloadBase64)
    }

    @Test
    fun test_validateOmemoEnvelope_whenValidEnvelope_returnsSuccess() {
        val service = DefaultXmppOmemoService(FakeOmemoClient(XmppResult.Success(Unit)))
        val xml = "<message><encrypted xmlns='eu.siacs.conversations.axolotl'><header sid='1'><key rid='1'>a2V5</key><iv>aXY=</iv></header><payload>cA==</payload></encrypted></message>"

        val validated = service.validateOmemoEnvelope(xml)

        assertIs<XmppResult.Success<Unit>>(validated)
    }
}

private class FakeOmemoClient(private val sendResult: XmppResult<Unit>) : KmpXmppClient {
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
