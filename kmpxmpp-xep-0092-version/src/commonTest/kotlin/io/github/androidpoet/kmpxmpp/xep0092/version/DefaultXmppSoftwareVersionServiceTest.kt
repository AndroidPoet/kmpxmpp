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
