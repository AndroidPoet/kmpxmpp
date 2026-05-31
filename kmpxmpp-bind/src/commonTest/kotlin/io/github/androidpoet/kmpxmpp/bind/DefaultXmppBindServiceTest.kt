package io.github.androidpoet.kmpxmpp.bind

import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultXmppBindServiceTest {
    private val service = DefaultXmppBindService()

    @Test
    fun test_buildBindRequest_whenValidInput_returnsBindSetIq() {
        val result = service.buildBindRequest(requestId = "bind-1", resource = "kmpxmpp")
        val success = assertIs<XmppResult.Success<String>>(result)
        assertTrue(success.value.contains("<iq type='set' id='bind-1'>"))
        assertTrue(success.value.contains("<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>"))
        assertTrue(success.value.contains("<resource>kmpxmpp</resource>"))
    }

    @Test
    fun test_buildBindRequest_whenBlankRequestId_returnsFailure() {
        val result = service.buildBindRequest(requestId = " ", resource = "kmpxmpp")
        val failure = assertIs<XmppResult.Failure>(result)
        assertEquals("Bind request id cannot be blank.", failure.error.message)
    }

    @Test
    fun test_parseBindResult_whenValidResultWithJidAndResource_returnsParsedBindResult() {
        val xml = "<iq type='result' id='bind-1'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'><jid>alice@example.com/mobile</jid><resource>mobile</resource></bind></iq>"
        val result = service.parseBindResult(xml, expectedRequestId = "bind-1")
        val success = assertIs<XmppResult.Success<XmppBindResult>>(result)
        assertEquals("bind-1", success.value.requestId)
        assertEquals("alice@example.com/mobile", success.value.jid.toString())
        assertEquals("mobile", success.value.resource)
    }

    @Test
    fun test_parseBindResult_whenValidResultWithoutJid_returnsSuccessWithNullJid() {
        val xml = "<iq type='result' id='bind-1'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></iq>"
        val result = service.parseBindResult(xml, expectedRequestId = "bind-1")
        val success = assertIs<XmppResult.Success<XmppBindResult>>(result)
        assertNull(success.value.jid)
        assertNull(success.value.resource)
    }

    @Test
    fun test_parseBindResult_whenIqTypeError_returnsFailure() {
        val xml = "<iq type='error' id='bind-1'><error type='cancel'/></iq>"
        val result = service.parseBindResult(xml, expectedRequestId = "bind-1")
        val failure = assertIs<XmppResult.Failure>(result)
        assertEquals("Bind result stanza indicates server error.", failure.error.message)
    }

    @Test
    fun test_parseBindResult_whenExpectedIdMismatch_returnsFailure() {
        val xml = "<iq type='result' id='bind-2'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></iq>"
        val result = service.parseBindResult(xml, expectedRequestId = "bind-1")
        val failure = assertIs<XmppResult.Failure>(result)
        assertEquals("Bind response id mismatch.", failure.error.message)
    }
}
