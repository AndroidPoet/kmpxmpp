package io.github.androidpoet.kmpxmpp.testkit

import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class XmppTestKitTest {
    @Test
    fun test_fakeClient_tracksAuthenticationAndSentStanzas() = runTest {
        val client = FakeKmpXmppClient()
        client.connect()
        client.authenticate(Jid("alice", "example.com"), "pw")
        client.sendStanza("<message id='m1'/>")
        client.disconnect()

        assertEquals(1, client.connectCalls)
        assertEquals(1, client.authenticateCalls)
        assertEquals(1, client.disconnectCalls)
        assertEquals("alice@example.com", client.lastAuthentication!!.first.toString())
        assertEquals("pw", client.lastAuthentication!!.second)
        assertEquals(listOf("<message id='m1'/>"), client.sentStanzas())
    }

    @Test
    fun test_fakeTransport_readQueueAndWrites_behaveDeterministically() = runTest {
        val transport = FakeXmppTransport(
            readQueue = listOf(
                XmppResult.Success("<stream:features/>"),
                XmppResult.Success("<iq type='result' id='bind-1'/>"),
            ),
        )
        transport.connect("example.com", 5222)
        transport.write("<open/>")
        val first = transport.read()
        val second = transport.read()
        val third = transport.read()
        transport.close()

        assertEquals(1, transport.connectCalls)
        assertEquals("example.com" to 5222, transport.lastEndpoint)
        assertEquals(listOf("<open/>"), transport.writtenStanzas())
        assertEquals("<stream:features/>", assertIs<XmppResult.Success<String>>(first).value)
        assertEquals("<iq type='result' id='bind-1'/>", assertIs<XmppResult.Success<String>>(second).value)
        val failure = assertIs<XmppResult.Failure>(third)
        assertTrue(failure.error.message.contains("No queued fake transport read result"))
        assertEquals(1, transport.closeCalls)
    }
}
