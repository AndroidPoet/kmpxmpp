package io.github.androidpoet.kmpxmpp.compliance

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XmppComplianceEvaluatorTest {

    @Test
    fun test_evaluate_whenAllRequiredPresent_marksCompliant() {
        val supported = XmppComplianceProfile.Cs2023Core.requiredFeatures +
            XmppComplianceProfile.Cs2023Core.recommendedFeatures

        val report = XmppComplianceEvaluator.evaluate(
            profile = XmppComplianceProfile.Cs2023Core,
            supportedFeatures = supported,
        )

        assertTrue(report.isCompliant)
        assertTrue(report.missingRequiredFeatures.isEmpty())
    }

    @Test
    fun test_evaluate_whenRequiredMissing_marksNonCompliant() {
        val supported = XmppComplianceProfile.Cs2023Core.requiredFeatures -
            "urn:xmpp:mam:2"

        val report = XmppComplianceEvaluator.evaluate(
            profile = XmppComplianceProfile.Cs2023Core,
            supportedFeatures = supported,
        )

        assertFalse(report.isCompliant)
        assertTrue("urn:xmpp:mam:2" in report.missingRequiredFeatures)
    }

    @Test
    fun test_evaluate_normalizesCaseAndWhitespace() {
        val supported = setOf(
            " urn:ietf:params:xml:ns:xmpp-sasl ",
            "URN:IETF:PARAMS:XML:NS:XMPP-BIND",
            " urn:xmpp:sm:3",
            "urn:xmpp:carbons:2",
            "urn:xmpp:receipts",
            "urn:xmpp:mam:2",
            "http://jabber.org/protocol/disco#info",
        )

        val report = XmppComplianceEvaluator.evaluate(
            profile = XmppComplianceProfile.Cs2023Core,
            supportedFeatures = supported,
        )

        assertTrue(report.isCompliant)
    }
}
