package io.github.androidpoet.kmpxmpp.xml

import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser

public interface XmppAuthStanzaParser {
    public fun isSaslSuccessReply(xml: String, saslNamespace: String): Boolean
    public fun isBindSuccessReply(xml: String): Boolean
}

public class DefaultXmppAuthStanzaParser : XmppAuthStanzaParser {
    override fun isSaslSuccessReply(xml: String, saslNamespace: String): Boolean {
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        if (tags.any { it.name == "iq" && it.attributes["type"]?.equals("error", ignoreCase = true) == true }) {
            return false
        }
        if (tags.any { it.name == "failure" }) return false
        return tags.any {
            it.name == "success" &&
                it.attributes["xmlns"]?.equals(saslNamespace, ignoreCase = false) == true
        }
    }

    override fun isBindSuccessReply(xml: String): Boolean {
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        if (tags.any { it.name == "iq" && it.attributes["type"]?.equals("error", ignoreCase = true) == true }) {
            return false
        }
        val hasIqResult = tags.any { it.name == "iq" && it.attributes["type"]?.equals("result", ignoreCase = true) == true }
        val hasBindNamespace = tags.any {
            it.name == "bind" &&
                it.attributes["xmlns"] == BIND_NAMESPACE
        }
        return hasIqResult && hasBindNamespace
    }

    private companion object {
        private const val BIND_NAMESPACE: String = "urn:ietf:params:xml:ns:xmpp-bind"
    }
}
