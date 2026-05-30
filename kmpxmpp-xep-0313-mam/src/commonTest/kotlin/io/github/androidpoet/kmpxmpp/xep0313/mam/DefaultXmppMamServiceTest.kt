package io.github.androidpoet.kmpxmpp.xep0313.mam

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
