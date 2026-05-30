package io.github.androidpoet.kmpxmpp.security

import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.sasl.SaslMechanism
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SecurityPolicyTest {
    @Test
    fun test_securityPolicy_validateTlsState_whenTlsRequiredAndInactive_returnsFailure() {
        val policy = SecurityPolicy(tlsMode = TlsMode.Required)

        val result = policy.validateTlsState(tlsActive = false)

        assertIs<XmppResult.Failure>(result)
        assertEquals("TLS is required by policy but not active.", result.error.message)
    }

    @Test
    fun test_securityPolicy_validateTlsState_whenTlsRequiredAndActive_returnsSuccess() {
        val policy = SecurityPolicy(tlsMode = TlsMode.Required)

        val result = policy.validateTlsState(tlsActive = true)

        assertIs<XmppResult.Success<Unit>>(result)
    }

    @Test
    fun test_securityPolicy_validateAuthMechanism_whenPlainWithoutTlsAndDisallowed_returnsFailure() {
        val policy = SecurityPolicy(allowPlainAuthWithoutTls = false)

        val result = policy.validateAuthMechanism(
            mechanism = SaslMechanism.Plain,
            tlsActive = false,
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals("SASL PLAIN without TLS is disabled by policy.", result.error.message)
    }

    @Test
    fun test_securityPolicy_validateAuthMechanism_whenPlainWithoutTlsAllowed_returnsSuccess() {
        val policy = SecurityPolicy(allowPlainAuthWithoutTls = true)

        val result = policy.validateAuthMechanism(
            mechanism = SaslMechanism.Plain,
            tlsActive = false,
        )

        assertIs<XmppResult.Success<Unit>>(result)
    }

    @Test
    fun test_securityPolicy_validateAuthMechanism_whenScramWithoutTls_returnsSuccess() {
        val policy = SecurityPolicy(allowPlainAuthWithoutTls = false)

        val result = policy.validateAuthMechanism(
            mechanism = SaslMechanism.ScramSha256,
            tlsActive = false,
        )

        assertIs<XmppResult.Success<Unit>>(result)
    }
}
