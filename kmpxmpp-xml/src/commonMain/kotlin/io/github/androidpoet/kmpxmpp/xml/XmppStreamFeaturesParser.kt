package io.github.androidpoet.kmpxmpp.xml

import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing
import io.github.androidpoet.kmpxmpp.sasl.SaslMechanism

public interface XmppStreamFeaturesParser {
    public fun parse(xml: String): XmppResult<XmppStreamFeatures>
}

public class DefaultXmppStreamFeaturesParser : XmppStreamFeaturesParser {
    override fun parse(xml: String): XmppResult<XmppStreamFeatures> {
        if (!xml.contains("<stream:features") && !xml.contains("<features")) {
            return XmppResult.Failure(
                xmppErrorParsing("Missing <stream:features> root element."),
            )
        }

        val mechanisms = parseMechanisms(xml)
        if (mechanisms.isEmpty()) {
            return XmppResult.Failure(
                xmppErrorParsing("No SASL mechanisms found in stream features."),
            )
        }

        return XmppResult.Success(
            XmppStreamFeatures(
                mechanisms = mechanisms,
                supportsStartTls = xml.contains("<starttls", ignoreCase = true),
            ),
        )
    }

    private fun parseMechanisms(xml: String): Set<SaslMechanism> {
        val found = linkedSetOf<SaslMechanism>()
        val regex = Regex("<mechanism>\\s*([^<]+?)\\s*</mechanism>", RegexOption.IGNORE_CASE)

        regex.findAll(xml).forEach { match ->
            when (match.groupValues[1].trim().uppercase()) {
                "SCRAM-SHA-256" -> found += SaslMechanism.ScramSha256
                "SCRAM-SHA-1" -> found += SaslMechanism.ScramSha1
                "PLAIN" -> found += SaslMechanism.Plain
            }
        }

        return found
    }
}
