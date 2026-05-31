package io.github.androidpoet.kmpxmpp.compliance

public enum class XmppComplianceProfile(
    public val label: String,
    public val requiredFeatures: Set<String>,
    public val recommendedFeatures: Set<String>,
) {
    Cs2023Core(
        label = "XMPP Compliance Suites 2023 Core",
        requiredFeatures = setOf(
            "urn:ietf:params:xml:ns:xmpp-sasl",
            "urn:ietf:params:xml:ns:xmpp-bind",
            "urn:xmpp:sm:3",
            "urn:xmpp:carbons:2",
            "urn:xmpp:receipts",
            "urn:xmpp:mam:2",
            "http://jabber.org/protocol/disco#info",
        ),
        recommendedFeatures = setOf(
            "urn:xmpp:sasl:2",
            "urn:xmpp:sasl-cb:0",
            "urn:xmpp:ping",
            "http://jabber.org/protocol/rsm",
        ),
    ),
    Xep0479SelfLabel(
        label = "XEP-0479 Self-Labeling Compliance",
        requiredFeatures = setOf(
            "urn:xmpp:features:rosterver",
            "http://jabber.org/protocol/disco#info",
        ),
        recommendedFeatures = setOf(
            "urn:xmpp:mam:2",
            "urn:xmpp:carbons:2",
        ),
    ),
}

public data class XmppComplianceReport(
    val profile: XmppComplianceProfile,
    val supportedFeatures: Set<String>,
    val missingRequiredFeatures: Set<String>,
    val missingRecommendedFeatures: Set<String>,
) {
    public val isCompliant: Boolean = missingRequiredFeatures.isEmpty()
}

public object XmppComplianceEvaluator {
    public fun evaluate(
        profile: XmppComplianceProfile,
        supportedFeatures: Set<String>,
    ): XmppComplianceReport {
        val normalized = supportedFeatures
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        val required = profile.requiredFeatures.map { it.lowercase() }.toSet()
        val recommended = profile.recommendedFeatures.map { it.lowercase() }.toSet()
        return XmppComplianceReport(
            profile = profile,
            supportedFeatures = normalized,
            missingRequiredFeatures = required - normalized,
            missingRecommendedFeatures = recommended - normalized,
        )
    }
}
