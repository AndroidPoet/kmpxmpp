package io.github.androidpoet.kmpxmpp.xep0092.version

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppSoftwareVersionServiceTest {
    @Test
    fun test_requestVersion_whenValidInput_sendsGetIq() = runTest {
        val client = FakeVersionClient(XmppResult.Success(Unit))
        val service = DefaultXmppSoftwareVersionService(client)

        val result = service.requestVersion(
            to = Jid(local = null, domain = "example.com"),
            requestId = "ver-1",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("jabber:iq:version"))
    }

    @Test
    fun test_sendVersionResult_whenBlankName_returnsFailure() = runTest {
        val service = DefaultXmppSoftwareVersionService(FakeVersionClient(XmppResult.Success(Unit)))

        val result = service.sendVersionResult(
            to = Jid(local = null, domain = "example.com"),
            requestId = "ver-2",
            version = XmppSoftwareVersion(name = " ", version = "1.0.0"),
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
    }

    @Test
    fun test_parseVersionResult_whenValidResult_returnsParsedVersion() {
        val service = DefaultXmppSoftwareVersionService(FakeVersionClient(XmppResult.Success(Unit)))
        val xml = """
            <iq type='result' id='v-1'>
              <query xmlns='jabber:iq:version'>
                <name>ChatApp</name>
                <version>1.2.3</version>
                <os>Android</os>
              </query>
            </iq>
        """.trimIndent()

        val parsed = service.parseVersionResult(xml)

        assertIs<XmppResult.Success<XmppSoftwareVersion>>(parsed)
        assertEquals("ChatApp", parsed.value.name)
        assertEquals("1.2.3", parsed.value.version)
        assertEquals("Android", parsed.value.os)
    }

    @Test
    fun test_parseVersionResult_whenVersionMissing_returnsFailure() {
        val service = DefaultXmppSoftwareVersionService(FakeVersionClient(XmppResult.Success(Unit)))
        val xml = "<iq type='result'><query xmlns='jabber:iq:version'><name>ChatApp</name></query></iq>"

        val parsed = service.parseVersionResult(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals("Version response missing non-blank <version>.", parsed.error.message)
    }

    @Test
    fun test_validateVersionRequest_whenValidGet_returnsRequestId() {
        val service = DefaultXmppSoftwareVersionService(FakeVersionClient(XmppResult.Success(Unit)))
        val xml = "<iq type='get' id='req-9'><query xmlns='jabber:iq:version'/></iq>"

        val validated = service.validateVersionRequest(xml)

        assertIs<XmppResult.Success<String>>(validated)
        assertEquals("req-9", validated.value)
    }

    @Test
    fun test_validateVersionRequest_whenTypeNotGet_returnsFailure() {
        val service = DefaultXmppSoftwareVersionService(FakeVersionClient(XmppResult.Success(Unit)))
        val xml = "<iq type='set' id='req-9'><query xmlns='jabber:iq:version'/></iq>"

        val validated = service.validateVersionRequest(xml)

        assertIs<XmppResult.Failure>(validated)
        assertEquals("Version request IQ must be type='get'.", validated.error.message)
    }

    @Test
    fun test_parseVersionResult_whenPrefixedTagsPresent_returnsParsedVersion() {
        val service = DefaultXmppSoftwareVersionService(FakeVersionClient(XmppResult.Success(Unit)))
        val xml = """
            <iq type='result' id='v-2'>
              <v:query xmlns:v='jabber:iq:version' xmlns='jabber:iq:version'>
                <v:name>PrefixChat</v:name>
                <v:version>9.9.9</v:version>
                <v:os>Linux</v:os>
              </v:query>
            </iq>
        """.trimIndent()

        val parsed = service.parseVersionResult(xml)

        assertIs<XmppResult.Success<XmppSoftwareVersion>>(parsed)
        assertEquals("PrefixChat", parsed.value.name)
        assertEquals("9.9.9", parsed.value.version)
        assertEquals("Linux", parsed.value.os)
    }
}

private class FakeVersionClient(private val sendResult: XmppResult<Unit>) : KmpXmppClient {
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
