package io.github.androidpoet.kmpxmpp.xep0319.idle

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppIdleServiceTest {
    @Test
    fun test_advertiseIdleSince_whenValidValue_sendsIdlePresence() = runTest {
        val client = FakeIdleClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppIdleService(client)

        val result = service.advertiseIdleSince("2026-05-31T10:00:00Z")

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("urn:xmpp:idle:1"))
        assertTrue(client.lastStanza!!.contains("since='2026-05-31T10:00:00Z'"))
    }

    @Test
    fun test_advertiseIdleSince_whenBlankValue_returnsFailure() = runTest {
        val service = DefaultXmppIdleService(FakeIdleClient(sendResult = XmppResult.Success(Unit)))

        val result = service.advertiseIdleSince(" ")

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
        assertEquals("Idle since timestamp cannot be blank.", result.error.message)
    }

    @Test
    fun test_parseIdlePresence_whenValidPresence_returnsParsedIdle() {
        val service = DefaultXmppIdleService(FakeIdleClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<presence><idle xmlns='urn:xmpp:idle:1' since='2026-05-31T10:00:00Z'/></presence>"

        val parsed = service.parseIdlePresence(xml)

        assertIs<XmppResult.Success<XmppIdlePresence>>(parsed)
        assertEquals("2026-05-31T10:00:00Z", parsed.value.sinceIso8601)
    }

    @Test
    fun test_parseIdlePresence_whenMissingSince_returnsFailure() {
        val service = DefaultXmppIdleService(FakeIdleClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<presence><idle xmlns='urn:xmpp:idle:1'/></presence>"

        val parsed = service.parseIdlePresence(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals(XmppErrorCode.ParsingFailed, parsed.error.code)
    }

    @Test
    fun test_validateIdlePresence_whenValidPresence_returnsSuccess() {
        val service = DefaultXmppIdleService(FakeIdleClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<presence><idle xmlns='urn:xmpp:idle:1' since='2026-05-31T10:00:00Z'/></presence>"

        val validated = service.validateIdlePresence(xml)

        assertIs<XmppResult.Success<Unit>>(validated)
    }
}

private class FakeIdleClient(
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
