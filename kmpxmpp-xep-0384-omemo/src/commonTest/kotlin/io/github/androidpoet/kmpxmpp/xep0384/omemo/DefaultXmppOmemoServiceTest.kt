package io.github.androidpoet.kmpxmpp.xep0384.omemo

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppOmemoServiceTest {
    @Test
    fun test_sendEncryptedMessage_whenValidInput_sendsOmemoMessage() = runTest {
        val client = FakeOmemoClient(XmppResult.Success(Unit))
        val service = DefaultXmppOmemoService(client)

        val result = service.sendEncryptedMessage(
            to = Jid(local = "alice", domain = "example.com"),
            encryptedPayloadBase64 = "a2V5",
            sid = 123,
            ivBase64 = "aXY=",
            payloadBase64 = "cGF5bG9hZA==",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("axolotl"))
        assertTrue(client.lastStanza!!.contains("<payload>"))
    }

    @Test
    fun test_sendEncryptedMessage_whenSidInvalid_returnsFailure() = runTest {
        val service = DefaultXmppOmemoService(FakeOmemoClient(XmppResult.Success(Unit)))

        val result = service.sendEncryptedMessage(
            to = Jid(local = "alice", domain = "example.com"),
            encryptedPayloadBase64 = "a2V5",
            sid = 0,
            ivBase64 = "aXY=",
            payloadBase64 = "cGF5bG9hZA==",
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
    }
}

private class FakeOmemoClient(private val sendResult: XmppResult<Unit>) : KmpXmppClient {
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
