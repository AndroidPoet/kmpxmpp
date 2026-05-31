package io.github.androidpoet.kmpxmpp.xep0066.oob

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppOobServiceTest {
    @Test
    fun test_sendOobUrl_whenValidInput_sendsOobStanza() = runTest {
        val client = FakeOobClient(XmppResult.Success(Unit))
        val service = DefaultXmppOobService(client)

        val result = service.sendOobUrl(
            to = Jid(local = "alice", domain = "example.com"),
            url = "https://example.com/file.png",
            description = "Image",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("jabber:x:oob"))
        assertTrue(client.lastStanza!!.contains("https://example.com/file.png"))
    }

    @Test
    fun test_sendOobUrl_whenUrlBlank_returnsFailure() = runTest {
        val service = DefaultXmppOobService(FakeOobClient(XmppResult.Success(Unit)))

        val result = service.sendOobUrl(
            to = Jid(local = "alice", domain = "example.com"),
            url = " ",
            description = null,
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
    }

    @Test
    fun test_parseOob_whenValidMessage_returnsParsedPayload() {
        val service = DefaultXmppOobService(FakeOobClient(XmppResult.Success(Unit)))
        val xml = """
            <message from='alice@example.com' to='bob@example.com'>
              <x xmlns='jabber:x:oob'>
                <url>https://example.com/file.png</url>
                <desc>Image file</desc>
              </x>
            </message>
        """.trimIndent()

        val parsed = service.parseOob(xml)

        assertIs<XmppResult.Success<ParsedOobPayload>>(parsed)
        assertEquals("alice", parsed.value.from?.local)
        assertEquals("bob", parsed.value.to?.local)
        assertEquals("https://example.com/file.png", parsed.value.url)
        assertEquals("Image file", parsed.value.description)
    }

    @Test
    fun test_parseOob_whenNamespaceMissing_returnsFailure() {
        val service = DefaultXmppOobService(FakeOobClient(XmppResult.Success(Unit)))
        val xml = "<message><x><url>https://example.com/file.png</url></x></message>"

        val parsed = service.parseOob(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals("OOB stanza missing jabber:x:oob namespace.", parsed.error.message)
    }

    @Test
    fun test_parseOob_whenUrlMissingOrUnsupported_returnsFailure() {
        val service = DefaultXmppOobService(FakeOobClient(XmppResult.Success(Unit)))
        val xml = "<message><x xmlns='jabber:x:oob'><url>ftp://example.com/file.png</url></x></message>"

        val parsed = service.parseOob(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals("OOB URL must use http or https scheme.", parsed.error.message)
    }

    @Test
    fun test_parseOob_whenPrefixedTagsPresent_returnsParsedPayload() {
        val service = DefaultXmppOobService(FakeOobClient(XmppResult.Success(Unit)))
        val xml = """
            <m:message xmlns:m='jabber:client' from='alice@example.com' to='bob@example.com'>
              <o:x xmlns:o='jabber:x:oob' xmlns='jabber:x:oob'>
                <o:url>https://example.com/prefix.png</o:url>
                <o:desc>prefixed payload</o:desc>
              </o:x>
            </m:message>
        """.trimIndent()

        val parsed = service.parseOob(xml)

        assertIs<XmppResult.Success<ParsedOobPayload>>(parsed)
        assertEquals("https://example.com/prefix.png", parsed.value.url)
        assertEquals("prefixed payload", parsed.value.description)
        assertEquals("alice", parsed.value.from?.local)
        assertEquals("bob", parsed.value.to?.local)
    }
}

private class FakeOobClient(private val sendResult: XmppResult<Unit>) : KmpXmppClient {
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
