package io.github.androidpoet.kmpxmpp.xep0359.stanzaids

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppStanzaIdsServiceTest {
    @Test
    fun test_sendOriginIdMessage_whenValidInput_sendsOriginIdStanza() = runTest {
        val client = FakeStanzaIdsClient(XmppResult.Success(Unit))
        val service = DefaultXmppStanzaIdsService(client)

        val result = service.sendOriginIdMessage(
            to = Jid(local = "a", domain = "example.com"),
            body = "hello",
            originId = "origin-1",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("origin-id"))
        assertTrue(client.lastStanza!!.contains("urn:xmpp:sid:0"))
    }

    @Test
    fun test_sendOriginIdMessage_whenOriginIdBlank_returnsFailure() = runTest {
        val service = DefaultXmppStanzaIdsService(FakeStanzaIdsClient(XmppResult.Success(Unit)))

        val result = service.sendOriginIdMessage(Jid(local = "a", domain = "example.com"), "hello", " ")

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
    }

    @Test
    fun test_sendMessageWithOriginAndStanzaId_whenValidInput_sendsBothElements() = runTest {
        val client = FakeStanzaIdsClient(XmppResult.Success(Unit))
        val service = DefaultXmppStanzaIdsService(client)

        val result = service.sendMessageWithOriginAndStanzaId(
            to = Jid(local = "b", domain = "example.com"),
            body = "hi",
            originId = "origin-2",
            stanzaId = "stanza-2",
            stanzaBy = Jid(local = null, domain = "archive.example.com"),
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("<origin-id"))
        assertTrue(client.lastStanza!!.contains("<stanza-id"))
        assertTrue(client.lastStanza!!.contains("by='archive.example.com'"))
    }

    @Test
    fun test_parseStanzaIds_whenValidXml_returnsParsedIds() {
        val service = DefaultXmppStanzaIdsService(FakeStanzaIdsClient(XmppResult.Success(Unit)))
        val xml = """
            <message>
              <origin-id xmlns='urn:xmpp:sid:0' id='origin-1'/>
              <stanza-id xmlns='urn:xmpp:sid:0' by='archive.example.com' id='stanza-1'/>
              <stanza-id xmlns='urn:xmpp:sid:0' by='muc.example.com' id='stanza-2'/>
            </message>
        """.trimIndent()

        val parsed = service.parseStanzaIds(xml)

        assertIs<XmppResult.Success<ParsedStanzaIds>>(parsed)
        assertEquals("origin-1", parsed.value.originId?.id)
        assertEquals(2, parsed.value.stanzaIds.size)
        assertEquals("stanza-1", parsed.value.stanzaIds[0].id)
        assertEquals("archive.example.com", parsed.value.stanzaIds[0].by?.domain)
    }

    @Test
    fun test_parseStanzaIds_whenOriginIdMissingId_returnsFailure() {
        val service = DefaultXmppStanzaIdsService(FakeStanzaIdsClient(XmppResult.Success(Unit)))
        val xml = "<message><origin-id xmlns='urn:xmpp:sid:0'/></message>"

        val parsed = service.parseStanzaIds(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals("origin-id element missing id attribute.", parsed.error.message)
    }

    @Test
    fun test_parseStanzaIds_whenNamespaceMissing_returnsFailure() {
        val service = DefaultXmppStanzaIdsService(FakeStanzaIdsClient(XmppResult.Success(Unit)))
        val xml = "<message><origin-id id='x'/></message>"

        val parsed = service.parseStanzaIds(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals("Stanza is missing urn:xmpp:sid:0 namespace.", parsed.error.message)
    }

    @Test
    fun test_parseStanzaIds_whenPrefixedTagsAndNamespacePresent_parsesSuccessfully() {
        val service = DefaultXmppStanzaIdsService(FakeStanzaIdsClient(XmppResult.Success(Unit)))
        val xml = """
            <message>
              <sid:origin-id xmlns:sid='urn:xmpp:sid:0' xmlns='urn:xmpp:sid:0' id='origin-9'/>
              <sid:stanza-id xmlns:sid='urn:xmpp:sid:0' xmlns='urn:xmpp:sid:0' by='archive.example.com' id='stanza-9'/>
            </message>
        """.trimIndent()

        val parsed = service.parseStanzaIds(xml)

        assertIs<XmppResult.Success<ParsedStanzaIds>>(parsed)
        assertEquals("origin-9", parsed.value.originId?.id)
        assertEquals(1, parsed.value.stanzaIds.size)
        assertEquals("stanza-9", parsed.value.stanzaIds.first().id)
    }
}

private class FakeStanzaIdsClient(private val sendResult: XmppResult<Unit>) : KmpXmppClient {
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
