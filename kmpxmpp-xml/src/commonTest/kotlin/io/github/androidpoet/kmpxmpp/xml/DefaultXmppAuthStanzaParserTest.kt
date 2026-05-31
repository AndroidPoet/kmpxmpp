package io.github.androidpoet.kmpxmpp.xml

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultXmppAuthStanzaParserTest {
    private val parser = DefaultXmppAuthStanzaParser()

    @Test
    fun test_isSaslSuccessReply_whenSuccessWithSaslNamespace_returnsTrue() {
        val xml = "<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>"
        assertTrue(parser.isSaslSuccessReply(xml, "urn:ietf:params:xml:ns:xmpp-sasl"))
    }

    @Test
    fun test_isSaslSuccessReply_whenSasl2Namespace_returnsTrue() {
        val xml = "<success xmlns='urn:xmpp:sasl:2'/>"
        assertTrue(parser.isSaslSuccessReply(xml, "urn:xmpp:sasl:2"))
    }

    @Test
    fun test_isSaslSuccessReply_whenFailurePresent_returnsFalse() {
        val xml = "<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/><failure xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>"
        assertFalse(parser.isSaslSuccessReply(xml, "urn:ietf:params:xml:ns:xmpp-sasl"))
    }

    @Test
    fun test_isSaslSuccessReply_whenNamespaceMissing_returnsFalse() {
        val xml = "<success/>"
        assertFalse(parser.isSaslSuccessReply(xml, "urn:ietf:params:xml:ns:xmpp-sasl"))
    }

    @Test
    fun test_isBindSuccessReply_whenIqResultAndBindNamespace_returnsTrue() {
        val xml = "<iq type='result' id='bind-1'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></iq>"
        assertTrue(parser.isBindSuccessReply(xml))
    }

    @Test
    fun test_isBindSuccessReply_whenIqErrorPresent_returnsFalse() {
        val xml = "<iq type='result'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></iq><iq type='error'/>"
        assertFalse(parser.isBindSuccessReply(xml))
    }

    @Test
    fun test_isBindSuccessReply_whenPrefixedTags_returnsTrue() {
        val xml = "<stream:iq type='result'><xmpp:bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></stream:iq>"
        assertTrue(parser.isBindSuccessReply(xml))
    }
}
