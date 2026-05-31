package io.github.androidpoet.kmpxmpp.xep0030.disco

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppDiscoServiceTest {
    @Test
    fun test_requestInfo_whenValidInput_sendsDiscoInfoIq() = runTest {
        val client = FakeDiscoClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppDiscoService(client)

        val result = service.requestInfo(
            to = Jid(local = null, domain = "example.com"),
            node = "urn:xmpp:mam:2",
            requestId = "disco-1",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("disco#info"))
        assertTrue(client.lastStanza!!.contains("node='urn:xmpp:mam:2'"))
    }

    @Test
    fun test_requestItems_whenRequestIdBlank_returnsFailure() = runTest {
        val service = DefaultXmppDiscoService(FakeDiscoClient(sendResult = XmppResult.Success(Unit)))

        val result = service.requestItems(
            to = Jid(local = null, domain = "example.com"),
            node = null,
            requestId = " ",
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
        assertEquals("Request id cannot be blank.", result.error.message)
    }

    @Test
    fun test_parseInfoResult_whenValidXml_returnsIdentitiesAndFeatures() {
        val service = DefaultXmppDiscoService(FakeDiscoClient(sendResult = XmppResult.Success(Unit)))
        val xml = """
            <iq type='result' id='d1'>
              <query xmlns='http://jabber.org/protocol/disco#info'>
                <identity category='server' type='im' name='Example Server'/>
                <feature var='urn:xmpp:ping'/>
                <feature var='urn:xmpp:mam:2'/>
              </query>
            </iq>
        """.trimIndent()

        val parsed = service.parseInfoResult(xml)

        assertIs<XmppResult.Success<DiscoInfoResult>>(parsed)
        assertEquals(1, parsed.value.identities.size)
        assertEquals("server", parsed.value.identities[0].category)
        assertEquals(2, parsed.value.features.size)
        assertEquals("urn:xmpp:ping", parsed.value.features[0])
    }

    @Test
    fun test_parseItemsResult_whenValidXml_returnsItems() {
        val service = DefaultXmppDiscoService(FakeDiscoClient(sendResult = XmppResult.Success(Unit)))
        val xml = """
            <iq type='result' id='d2'>
              <query xmlns='http://jabber.org/protocol/disco#items'>
                <item jid='room@conference.example.com' node='muc-room' name='Team Room'/>
              </query>
            </iq>
        """.trimIndent()

        val parsed = service.parseItemsResult(xml)

        assertIs<XmppResult.Success<DiscoItemsResult>>(parsed)
        assertEquals(1, parsed.value.items.size)
        assertEquals("conference.example.com", parsed.value.items[0].jid?.domain)
        assertEquals("muc-room", parsed.value.items[0].node)
    }

    @Test
    fun test_validateDiscoRequest_whenValidInfoRequest_returnsRequestId() {
        val service = DefaultXmppDiscoService(FakeDiscoClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<iq type='get' id='req-1'><query xmlns='http://jabber.org/protocol/disco#info'/></iq>"

        val validated = service.validateDiscoRequest(xml, "http://jabber.org/protocol/disco#info")

        assertIs<XmppResult.Success<String>>(validated)
        assertEquals("req-1", validated.value)
    }

    @Test
    fun test_validateDiscoRequest_whenWrongType_returnsFailure() {
        val service = DefaultXmppDiscoService(FakeDiscoClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<iq type='set' id='req-1'><query xmlns='http://jabber.org/protocol/disco#info'/></iq>"

        val validated = service.validateDiscoRequest(xml, "http://jabber.org/protocol/disco#info")

        assertIs<XmppResult.Failure>(validated)
        assertEquals("Disco request IQ must be type='get'.", validated.error.message)
    }
}

private class FakeDiscoClient(
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
