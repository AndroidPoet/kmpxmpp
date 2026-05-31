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
                <mechanism>SCRAM-SHA-256-PLUS</mechanism>
                <mechanism>SCRAM-SHA-256</mechanism>
                <mechanism>PLAIN</mechanism>
              </mechanisms>
              <starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>
            </stream:features>
        """.trimIndent()

        val result = parser.parse(xml)

        assertIs<XmppResult.Success<XmppStreamFeatures>>(result)
        assertEquals(setOf(SaslMechanism.ScramSha256Plus, SaslMechanism.ScramSha256, SaslMechanism.Plain), result.value.mechanisms)
        assertTrue(result.value.supportsStartTls)
        assertEquals(XmppSaslProfile.Sasl1, result.value.saslProfile)
        assertTrue(result.value.channelBindingTypes.isEmpty())
    }

    @Test
    fun test_parse_whenSasl2AuthenticationPresent_detectsSasl2Profile() {
        val xml = """
            <stream:features>
              <authentication xmlns='urn:xmpp:sasl:2'>
                <mechanism>SCRAM-SHA-256</mechanism>
              </authentication>
            </stream:features>
        """.trimIndent()

        val result = parser.parse(xml)

        assertIs<XmppResult.Success<XmppStreamFeatures>>(result)
        assertEquals(XmppSaslProfile.Sasl2, result.value.saslProfile)
        assertTrue(result.value.channelBindingTypes.isEmpty())
    }

    @Test
    fun test_parse_whenChannelBindingFeaturePresent_extractsBindingTypes() {
        val xml = """
            <stream:features>
              <sasl-channel-binding xmlns='urn:xmpp:sasl-cb:0'>
                <channel-binding type='tls-exporter'/>
                <channel-binding type='tls-server-end-point'/>
              </sasl-channel-binding>
              <authentication xmlns='urn:xmpp:sasl:2'>
                <mechanism>SCRAM-SHA-256</mechanism>
              </authentication>
            </stream:features>
        """.trimIndent()

        val result = parser.parse(xml)

        assertIs<XmppResult.Success<XmppStreamFeatures>>(result)
        assertEquals(setOf("tls-exporter", "tls-server-end-point"), result.value.channelBindingTypes)
    }

    @Test
    fun test_parse_whenPrefixedFeaturesAndMechanisms_parsesSuccessfully() {
        val xml = """
            <stream:features>
              <sasl:authentication xmlns:sasl='urn:xmpp:sasl:2' xmlns='urn:xmpp:sasl:2'>
                <sasl:mechanism>SCRAM-SHA-256</sasl:mechanism>
              </sasl:authentication>
              <cb:sasl-channel-binding xmlns:cb='urn:xmpp:sasl-cb:0'>
                <cb:channel-binding type='tls-exporter'/>
              </cb:sasl-channel-binding>
            </stream:features>
        """.trimIndent()

        val result = parser.parse(xml)

        assertIs<XmppResult.Success<XmppStreamFeatures>>(result)
        assertEquals(XmppSaslProfile.Sasl2, result.value.saslProfile)
        assertEquals(setOf(SaslMechanism.ScramSha256), result.value.mechanisms)
        assertEquals(setOf("tls-exporter"), result.value.channelBindingTypes)
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
