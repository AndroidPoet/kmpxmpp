package io.github.androidpoet.kmpxmpp.xml

import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing
import io.github.androidpoet.kmpxmpp.sasl.SaslMechanism

public interface XmppStreamFeaturesParser {
    public fun parse(xml: String): XmppResult<XmppStreamFeatures>
}

public class DefaultXmppStreamFeaturesParser : XmppStreamFeaturesParser {
    override fun parse(xml: String): XmppResult<XmppStreamFeatures> {
        val startTags = XmppXmlMiniParser.parseStartTags(xml)
        val hasFeaturesRoot = startTags.any { it.name == "features" }
        if (!hasFeaturesRoot) {
            return XmppResult.Failure(
                xmppErrorParsing("Missing <stream:features> root element."),
            )
        }

        val mechanisms = parseMechanisms(xml, startTags)
        if (mechanisms.isEmpty()) {
            return XmppResult.Failure(
                xmppErrorParsing("No SASL mechanisms found in stream features."),
            )
        }

        return XmppResult.Success(
            XmppStreamFeatures(
                mechanisms = mechanisms,
                supportsStartTls = startTags.any { it.name == "starttls" },
                saslProfile = detectSaslProfile(startTags),
                channelBindingTypes = parseChannelBindingTypes(startTags),
            ),
        )
    }

    private fun detectSaslProfile(startTags: List<io.github.androidpoet.kmpxmpp.core.XmlStartTag>): XmppSaslProfile {
        val hasSasl2Auth = startTags.any { it.name == "authentication" && it.attributes["xmlns"] == "urn:xmpp:sasl:2" }
        return if (hasSasl2Auth) XmppSaslProfile.Sasl2 else XmppSaslProfile.Sasl1
    }

    private fun parseMechanisms(xml: String, startTags: List<io.github.androidpoet.kmpxmpp.core.XmlStartTag>): Set<SaslMechanism> {
        val found = linkedSetOf<SaslMechanism>()
        val mechanismTexts = startTags
            .filter { it.name == "mechanism" }
            .mapNotNull { XmppXmlMiniParser.tagInnerXml(xml, it)?.trim() }
            .filter { it.isNotEmpty() }
        mechanismTexts.forEach { mechanismText ->
            when (mechanismText.trim().uppercase()) {
                "SCRAM-SHA-256-PLUS" -> found += SaslMechanism.ScramSha256Plus
                "SCRAM-SHA-1-PLUS" -> found += SaslMechanism.ScramSha1Plus
                "SCRAM-SHA-256" -> found += SaslMechanism.ScramSha256
                "SCRAM-SHA-1" -> found += SaslMechanism.ScramSha1
                "PLAIN" -> found += SaslMechanism.Plain
            }
        }
        if (found.isNotEmpty()) return found
        // Fallback when servers advertise mechanisms only as attributes in odd envelopes.
        startTags
            .filter { it.name == "mechanism" }
            .mapNotNull { it.attributes["name"]?.trim()?.uppercase() }
            .forEach { token ->
                when (token) {
                    "SCRAM-SHA-256-PLUS" -> found += SaslMechanism.ScramSha256Plus
                    "SCRAM-SHA-1-PLUS" -> found += SaslMechanism.ScramSha1Plus
                    "SCRAM-SHA-256" -> found += SaslMechanism.ScramSha256
                    "SCRAM-SHA-1" -> found += SaslMechanism.ScramSha1
                    "PLAIN" -> found += SaslMechanism.Plain
                }
        }
        return found
    }

    private fun parseChannelBindingTypes(startTags: List<io.github.androidpoet.kmpxmpp.core.XmlStartTag>): Set<String> {
        val found = linkedSetOf<String>()
        startTags
            .asSequence()
            .filter { it.name == "channel-binding" }
            .mapNotNull { it.attributes["type"] }
            .forEach { rawType ->
                val type = rawType.trim()
                if (type.isNotEmpty()) {
                    found += type
                }
            }
        return found
    }
}
