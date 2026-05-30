package io.github.androidpoet.kmpxmpp.xml

import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.sasl.SaslMechanism
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppStreamFeaturesParserTest {
    private val parser = DefaultXmppStreamFeaturesParser()

    @Test
    fun test_parse_whenValidFeatures_extractsMechanismsAndStartTls() {
        val xml = """
            <stream:features>
              <mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>
                <mechanism>SCRAM-SHA-256</mechanism>
                <mechanism>PLAIN</mechanism>
              </mechanisms>
              <starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>
            </stream:features>
        """.trimIndent()

        val result = parser.parse(xml)

        assertIs<XmppResult.Success<XmppStreamFeatures>>(result)
        assertEquals(setOf(SaslMechanism.ScramSha256, SaslMechanism.Plain), result.value.mechanisms)
        assertTrue(result.value.supportsStartTls)
    }

    @Test
    fun test_parse_whenNoMechanisms_returnsFailure() {
        val xml = "<stream:features><starttls/></stream:features>"

        val result = parser.parse(xml)

        assertIs<XmppResult.Failure>(result)
        assertEquals("No SASL mechanisms found in stream features.", result.error.message)
    }

    @Test
    fun test_parse_whenMissingFeaturesRoot_returnsFailure() {
        val xml = "<foo/>"

        val result = parser.parse(xml)

        assertIs<XmppResult.Failure>(result)
        assertEquals("Missing <stream:features> root element.", result.error.message)
        assertEquals(XmppErrorCode.ParsingFailed, result.error.code)
        assertEquals(XmppErrorStage.StreamNegotiation, result.error.stage)
    }
}
