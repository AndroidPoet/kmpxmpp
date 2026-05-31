package io.github.androidpoet.kmpxmpp.xep0191.blocking

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppBlockingServiceTest {
    @Test
    fun test_block_whenValidList_sendsBlockCommand() = runTest {
        val client = FakeBlockingClient(XmppResult.Success(Unit))
        val service = DefaultXmppBlockingService(client)

        val result = service.block(
            jids = listOf(Jid(local = "spam", domain = "example.com")),
            requestId = "blk-1",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("urn:xmpp:blocking"))
        assertTrue(client.lastStanza!!.contains("<block"))
    }

    @Test
    fun test_unblock_whenEmptyList_returnsFailure() = runTest {
        val service = DefaultXmppBlockingService(FakeBlockingClient(XmppResult.Success(Unit)))

        val result = service.unblock(jids = emptyList(), requestId = "ub-1")

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
    }

    @Test
    fun test_parseBlockListResult_whenValidResult_returnsItems() {
        val service = DefaultXmppBlockingService(FakeBlockingClient(XmppResult.Success(Unit)))
        val xml = """
            <iq type='result' id='bl-1'>
              <blocklist xmlns='urn:xmpp:blocking'>
                <item jid='spam@example.com'/>
                <item jid='ads.example.com'/>
              </blocklist>
            </iq>
        """.trimIndent()

        val parsed = service.parseBlockListResult(xml)

        assertIs<XmppResult.Success<List<XmppBlockListItem>>>(parsed)
        assertEquals(2, parsed.value.size)
        assertEquals("spam", parsed.value[0].jid.local)
        assertEquals("ads.example.com", parsed.value[1].jid.domain)
    }

    @Test
    fun test_parseBlockListResult_whenWrongType_returnsFailure() {
        val service = DefaultXmppBlockingService(FakeBlockingClient(XmppResult.Success(Unit)))
        val xml = "<iq type='error'><blocklist xmlns='urn:xmpp:blocking'/></iq>"

        val parsed = service.parseBlockListResult(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals(XmppErrorCode.ParsingFailed, parsed.error.code)
    }

    @Test
    fun test_validateBlockingCommandRequest_whenValidBlock_returnsRequestId() {
        val service = DefaultXmppBlockingService(FakeBlockingClient(XmppResult.Success(Unit)))
        val xml = "<iq type='set' id='blk-1'><block xmlns='urn:xmpp:blocking'><item jid='spam@example.com'/></block></iq>"

        val validated = service.validateBlockingCommandRequest(xml, "block")

        assertIs<XmppResult.Success<String>>(validated)
        assertEquals("blk-1", validated.value)
    }

    @Test
    fun test_validateBlockingCommandResult_whenIdMismatch_returnsFailure() {
        val service = DefaultXmppBlockingService(FakeBlockingClient(XmppResult.Success(Unit)))
        val xml = "<iq type='result' id='other-id'/>"

        val validated = service.validateBlockingCommandResult(xml, requestId = "blk-1")

        assertIs<XmppResult.Failure>(validated)
        assertEquals(XmppErrorCode.ParsingFailed, validated.error.code)
    }
}

private class FakeBlockingClient(private val sendResult: XmppResult<Unit>) : KmpXmppClient {
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
