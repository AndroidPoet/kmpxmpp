package io.github.androidpoet.kmpxmpp.xep0115.caps

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppEntityCapsServiceTest {
    @Test
    fun test_advertiseCaps_whenValidCaps_sendsPresenceCapsNode() = runTest {
        val client = FakeCapsClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppEntityCapsService(client)

        val result = service.advertiseCaps(
            XmppEntityCaps(
                node = "https://kmpxmpp.dev/caps",
                hash = "sha-1",
                ver = "abc123",
            ),
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("protocol/caps"))
        assertTrue(client.lastStanza!!.contains("ver='abc123'"))
    }

    @Test
    fun test_advertiseCaps_whenNodeBlank_returnsInvalidInputFailure() = runTest {
        val service = DefaultXmppEntityCapsService(FakeCapsClient(sendResult = XmppResult.Success(Unit)))

        val result = service.advertiseCaps(
            XmppEntityCaps(
                node = " ",
                hash = "sha-1",
                ver = "abc123",
            ),
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
        assertEquals("Caps node cannot be blank.", result.error.message)
    }

    @Test
    fun test_parsePresenceCaps_whenValidPayload_returnsCaps() {
        val service = DefaultXmppEntityCapsService(FakeCapsClient(sendResult = XmppResult.Success(Unit)))
        val xml = """
            <presence from='romeo@example.com/home'>
              <c xmlns='http://jabber.org/protocol/caps' node='https://kmpxmpp.dev/caps' hash='sha-1' ver='abc123'/>
            </presence>
        """.trimIndent()

        val parsed = service.parsePresenceCaps(xml)

        assertIs<XmppResult.Success<XmppEntityCaps>>(parsed)
        assertEquals("https://kmpxmpp.dev/caps", parsed.value.node)
        assertEquals("sha-1", parsed.value.hash)
        assertEquals("abc123", parsed.value.ver)
    }

    @Test
    fun test_parsePresenceCaps_whenMissingNamespace_returnsFailure() {
        val service = DefaultXmppEntityCapsService(FakeCapsClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<presence><c node='n' hash='sha-1' ver='v1'/></presence>"

        val parsed = service.parsePresenceCaps(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals(XmppErrorCode.ParsingFailed, parsed.error.code)
    }

    @Test
    fun test_validateCapsPresence_whenValidPayload_returnsSuccess() {
        val service = DefaultXmppEntityCapsService(FakeCapsClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<presence><c xmlns='http://jabber.org/protocol/caps' node='n' hash='sha-1' ver='v1'/></presence>"

        val validated = service.validateCapsPresence(xml)

        assertIs<XmppResult.Success<Unit>>(validated)
    }
}

private class FakeCapsClient(
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
