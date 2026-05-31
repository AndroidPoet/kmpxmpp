package io.github.androidpoet.kmpxmpp.xep0133.admin

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppAdminServiceTest {
    @Test
    fun test_requestRegisteredUsersCount_whenValidInput_sendsAdminCommand() = runTest {
        val client = FakeAdminClient(XmppResult.Success(Unit))
        val service = DefaultXmppAdminService(client)

        val result = service.requestRegisteredUsersCount(
            adminService = Jid(local = null, domain = "example.com"),
            requestId = "admin-1",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("protocol/commands"))
        assertTrue(client.lastStanza!!.contains("get-registered-users-num"))
    }

    @Test
    fun test_requestRegisteredUsersCount_whenRequestIdBlank_returnsFailure() = runTest {
        val service = DefaultXmppAdminService(FakeAdminClient(XmppResult.Success(Unit)))

        val result = service.requestRegisteredUsersCount(
            adminService = Jid(local = null, domain = "example.com"),
            requestId = " ",
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
    }

    @Test
    fun test_parseRegisteredUsersCountResult_whenValidResult_returnsCount() {
        val service = DefaultXmppAdminService(FakeAdminClient(XmppResult.Success(Unit)))
        val xml = """
            <iq type='result' id='admin-1'>
              <command xmlns='http://jabber.org/protocol/commands' status='completed'>
                <x xmlns='jabber:x:data' type='result'>
                  <field var='registeredusersnum'><value>42</value></field>
                </x>
              </command>
            </iq>
        """.trimIndent()

        val parsed = service.parseRegisteredUsersCountResult(xml)

        assertIs<XmppResult.Success<RegisteredUsersCountResult>>(parsed)
        assertEquals(42, parsed.value.count)
    }

    @Test
    fun test_validateRegisteredUsersCountRequest_whenValidRequest_returnsRequestId() {
        val service = DefaultXmppAdminService(FakeAdminClient(XmppResult.Success(Unit)))
        val xml = "<iq type='set' id='admin-1'><command xmlns='http://jabber.org/protocol/commands' node='http://jabber.org/protocol/admin#get-registered-users-num' action='execute'/></iq>"

        val validated = service.validateRegisteredUsersCountRequest(xml)

        assertIs<XmppResult.Success<String>>(validated)
        assertEquals("admin-1", validated.value)
    }

    @Test
    fun test_validateRegisteredUsersCountResponse_whenIdMismatch_returnsFailure() {
        val service = DefaultXmppAdminService(FakeAdminClient(XmppResult.Success(Unit)))
        val xml = "<iq type='result' id='other'/>"

        val validated = service.validateRegisteredUsersCountResponse(xml, requestId = "admin-1")

        assertIs<XmppResult.Failure>(validated)
        assertEquals(XmppErrorCode.ParsingFailed, validated.error.code)
    }
}

private class FakeAdminClient(private val sendResult: XmppResult<Unit>) : KmpXmppClient {
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
