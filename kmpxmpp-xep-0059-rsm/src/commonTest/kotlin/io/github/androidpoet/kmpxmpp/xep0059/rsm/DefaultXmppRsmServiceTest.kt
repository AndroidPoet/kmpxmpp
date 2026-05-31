package io.github.androidpoet.kmpxmpp.xep0059.rsm

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppRsmServiceTest {
    @Test
    fun test_requestPage_whenValidInput_injectsRsmSetNode() = runTest {
        val client = FakeRsmClient(XmppResult.Success(Unit))
        val service = DefaultXmppRsmService(client)

        val result = service.requestPage(
            queryXml = "<iq><query xmlns='urn:xmpp:mam:2'></query></iq>",
            page = XmppRsmPage(max = 20, after = "msg-1"),
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("protocol/rsm"))
        assertTrue(client.lastStanza!!.contains("<max>20</max>"))
    }

    @Test
    fun test_requestPage_whenAfterAndBeforeBothSet_returnsFailure() = runTest {
        val service = DefaultXmppRsmService(FakeRsmClient(XmppResult.Success(Unit)))

        val result = service.requestPage(
            queryXml = "<iq><query xmlns='urn:xmpp:mam:2'></query></iq>",
            page = XmppRsmPage(after = "a", before = "b"),
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
    }

    @Test
    fun test_parseRsmResult_whenValidResult_returnsParsedPage() {
        val service = DefaultXmppRsmService(FakeRsmClient(XmppResult.Success(Unit)))
        val xml = """
            <iq type='result' id='mam-1'>
              <fin xmlns='urn:xmpp:mam:2'>
                <set xmlns='http://jabber.org/protocol/rsm'>
                  <first>msg-1</first>
                  <last>msg-20</last>
                  <count>133</count>
                </set>
              </fin>
            </iq>
        """.trimIndent()

        val parsed = service.parseRsmResult(xml)

        assertIs<XmppResult.Success<XmppRsmResultPage>>(parsed)
        assertEquals("msg-1", parsed.value.first)
        assertEquals("msg-20", parsed.value.last)
        assertEquals(133, parsed.value.count)
    }

    @Test
    fun test_parseRsmResult_whenMissingSet_returnsFailure() {
        val service = DefaultXmppRsmService(FakeRsmClient(XmppResult.Success(Unit)))
        val xml = "<iq type='result'><fin xmlns='urn:xmpp:mam:2'/></iq>"

        val parsed = service.parseRsmResult(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals(XmppErrorCode.ParsingFailed, parsed.error.code)
    }

    @Test
    fun test_validateRsmRequest_whenValidSet_returnsSuccess() {
        val service = DefaultXmppRsmService(FakeRsmClient(XmppResult.Success(Unit)))
        val xml = "<query xmlns='urn:xmpp:mam:2'><set xmlns='http://jabber.org/protocol/rsm'><max>20</max><after>msg-1</after></set></query>"

        val validated = service.validateRsmRequest(xml)

        assertIs<XmppResult.Success<Unit>>(validated)
    }

    @Test
    fun test_validateRsmRequest_whenBothAfterAndBefore_returnsFailure() {
        val service = DefaultXmppRsmService(FakeRsmClient(XmppResult.Success(Unit)))
        val xml = "<query><set xmlns='http://jabber.org/protocol/rsm'><after>a</after><before>b</before></set></query>"

        val validated = service.validateRsmRequest(xml)

        assertIs<XmppResult.Failure>(validated)
        assertEquals(XmppErrorCode.ParsingFailed, validated.error.code)
    }

    @Test
    fun test_parseRsmResult_whenPrefixedSetAndValues_returnsParsedPage() {
        val service = DefaultXmppRsmService(FakeRsmClient(XmppResult.Success(Unit)))
        val xml = """
            <iq type='result' id='mam-1'>
              <fin xmlns='urn:xmpp:mam:2'>
                <rsm:set xmlns:rsm='http://jabber.org/protocol/rsm' xmlns='http://jabber.org/protocol/rsm'>
                  <rsm:first>p1</rsm:first>
                  <rsm:last>p9</rsm:last>
                  <rsm:count>9</rsm:count>
                </rsm:set>
              </fin>
            </iq>
        """.trimIndent()

        val parsed = service.parseRsmResult(xml)

        assertIs<XmppResult.Success<XmppRsmResultPage>>(parsed)
        assertEquals("p1", parsed.value.first)
        assertEquals("p9", parsed.value.last)
        assertEquals(9, parsed.value.count)
    }
}

private class FakeRsmClient(private val sendResult: XmppResult<Unit>) : KmpXmppClient {
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
