package io.github.androidpoet.kmpxmpp.xep0313.mam

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.datetime.Instant
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultXmppMamServiceTest {
    @Test
    fun test_queryArchive_whenValidInput_sendsMamQueryIq() = runTest {
        val client = FakeMamClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppMamService(client)

        val result = service.queryArchive(
            queryId = "q-1",
            query = MamQuery(
                withJid = Jid(local = "alice", domain = "example.com"),
                max = 50,
                after = "msg-10",
            ),
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("urn:xmpp:mam:2"))
        assertTrue(client.lastStanza!!.contains("<max>50</max>"))
        assertTrue(client.lastStanza!!.contains("<after>msg-10</after>"))
    }

    @Test
    fun test_queryArchive_whenTimeRangeAndBeforeProvided_includesAllFilters() = runTest {
        val client = FakeMamClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppMamService(client)

        val result = service.queryArchive(
            queryId = "q-2",
            query = MamQuery(
                withJid = Jid(local = "bob", domain = "example.com"),
                max = 25,
                before = "msg-500",
                start = Instant.parse("2026-01-01T00:00:00Z"),
                end = Instant.parse("2026-01-31T23:59:59Z"),
            ),
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertTrue(client.lastStanza!!.contains("<before>msg-500</before>"))
        assertTrue(client.lastStanza!!.contains("<field var='start'><value>2026-01-01T00:00:00Z</value></field>"))
        assertTrue(client.lastStanza!!.contains("<field var='end'><value>2026-01-31T23:59:59Z</value></field>"))
    }

    @Test
    fun test_queryArchive_whenMaxInvalid_returnsFailure() = runTest {
        val service = DefaultXmppMamService(FakeMamClient(sendResult = XmppResult.Success(Unit)))

        val result = service.queryArchive(
            queryId = "q-1",
            query = MamQuery(max = 0),
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
        assertEquals(XmppErrorStage.Messaging, result.error.stage)
    }

    @Test
    fun test_queryArchive_whenAfterAndBeforeBothSet_returnsFailure() = runTest {
        val service = DefaultXmppMamService(FakeMamClient(sendResult = XmppResult.Success(Unit)))

        val result = service.queryArchive(
            queryId = "q-3",
            query = MamQuery(after = "a", before = "b"),
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
        assertEquals("RSM after and before cannot both be set.", result.error.message)
    }

    @Test
    fun test_queryArchive_whenStartAfterEnd_returnsFailure() = runTest {
        val service = DefaultXmppMamService(FakeMamClient(sendResult = XmppResult.Success(Unit)))

        val result = service.queryArchive(
            queryId = "q-4",
            query = MamQuery(
                start = Instant.parse("2026-02-01T00:00:00Z"),
                end = Instant.parse("2026-01-01T00:00:00Z"),
            ),
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
        assertEquals("MAM start must be earlier than end.", result.error.message)
    }

    @Test
    fun test_parseQueryResult_whenValidMamFinAndResults_returnsParsedData() {
        val service = DefaultXmppMamService(FakeMamClient(sendResult = XmppResult.Success(Unit)))
        val xml = """
            <message>
              <result xmlns='urn:xmpp:mam:2' queryid='q-7' id='r-1'>
                <forwarded xmlns='urn:xmpp:forward:0'>
                  <message from='alice@example.com'>
                    <body>Hello</body>
                  </message>
                </forwarded>
              </result>
            </message>
            <iq type='result' id='q-7'>
              <fin xmlns='urn:xmpp:mam:2' complete='true' stable='true'>
                <set xmlns='http://jabber.org/protocol/rsm'>
                  <first>r-1</first>
                  <last>r-9</last>
                  <count>9</count>
                </set>
              </fin>
            </iq>
        """.trimIndent()

        val parsed = service.parseQueryResult(xml)

        assertIs<XmppResult.Success<MamQueryResult>>(parsed)
        assertEquals(1, parsed.value.messages.size)
        assertEquals("r-1", parsed.value.messages[0].resultId)
        assertEquals("q-7", parsed.value.messages[0].queryId)
        assertEquals("alice", parsed.value.messages[0].from?.local)
        assertEquals("example.com", parsed.value.messages[0].from?.domain)
        assertEquals("Hello", parsed.value.messages[0].body)
        assertTrue(parsed.value.pageInfo.complete)
        assertTrue(parsed.value.pageInfo.stable)
        assertEquals("r-1", parsed.value.pageInfo.first)
        assertEquals("r-9", parsed.value.pageInfo.last)
        assertEquals(9, parsed.value.pageInfo.count)
    }

    @Test
    fun test_parseQueryResult_whenFinMissing_returnsFailure() {
        val service = DefaultXmppMamService(FakeMamClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<result xmlns='urn:xmpp:mam:2' id='r-1'></result>"

        val parsed = service.parseQueryResult(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals("MAM response missing <fin/> stanza.", parsed.error.message)
    }

    @Test
    fun test_parseQueryResult_whenResultMissingId_skipsMalformedResult() {
        val service = DefaultXmppMamService(FakeMamClient(sendResult = XmppResult.Success(Unit)))
        val xml = """
            <result xmlns='urn:xmpp:mam:2'>
              <forwarded xmlns='urn:xmpp:forward:0'><message from='alice@example.com'><body>X</body></message></forwarded>
            </result>
            <fin xmlns='urn:xmpp:mam:2' complete='false' stable='true'>
              <set xmlns='http://jabber.org/protocol/rsm'><count>0</count></set>
            </fin>
        """.trimIndent()

        val parsed = service.parseQueryResult(xml)

        assertIs<XmppResult.Success<MamQueryResult>>(parsed)
        assertEquals(0, parsed.value.messages.size)
        assertNull(parsed.value.pageInfo.first)
        assertNull(parsed.value.pageInfo.last)
    }

    @Test
    fun test_parseQueryResult_whenPrefixedMamTagsPresent_returnsParsedData() {
        val service = DefaultXmppMamService(FakeMamClient(sendResult = XmppResult.Success(Unit)))
        val xml = """
            <message>
              <m:result xmlns:m='urn:xmpp:mam:2' m:queryid='q-8' m:id='r-2'>
                <f:forwarded xmlns:f='urn:xmpp:forward:0'>
                  <message from='carol@example.com'>
                    <b:body xmlns:b='jabber:client'>HiPrefixed</b:body>
                  </message>
                </f:forwarded>
              </m:result>
            </message>
            <iq type='result' id='q-8'>
              <m:fin xmlns:m='urn:xmpp:mam:2' m:stable='false' m:complete='false'>
                <r:set xmlns:r='http://jabber.org/protocol/rsm'>
                  <r:first>r-2</r:first>
                  <r:last>r-4</r:last>
                  <r:count>4</r:count>
                </r:set>
              </m:fin>
            </iq>
        """.trimIndent()

        val parsed = service.parseQueryResult(xml)

        assertIs<XmppResult.Success<MamQueryResult>>(parsed)
        assertEquals(1, parsed.value.messages.size)
        assertEquals("r-2", parsed.value.messages[0].resultId)
        assertEquals("q-8", parsed.value.messages[0].queryId)
        assertEquals("carol", parsed.value.messages[0].from?.local)
        assertEquals("HiPrefixed", parsed.value.messages[0].body)
        assertEquals("r-2", parsed.value.pageInfo.first)
        assertEquals("r-4", parsed.value.pageInfo.last)
        assertEquals(4, parsed.value.pageInfo.count)
        assertEquals(false, parsed.value.pageInfo.complete)
        assertEquals(false, parsed.value.pageInfo.stable)
    }
}

private class FakeMamClient(
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
