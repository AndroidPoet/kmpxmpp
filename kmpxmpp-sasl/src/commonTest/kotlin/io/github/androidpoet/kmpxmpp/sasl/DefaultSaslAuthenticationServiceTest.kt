package io.github.androidpoet.kmpxmpp.sasl

import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultSaslAuthenticationServiceTest {
    private val service = DefaultSaslAuthenticationService()

    @Test
    fun test_authenticate_whenScramAvailable_selectsBestMechanism() = runTest {
        val result = service.authenticate(
            jid = Jid(local = "alice", domain = "example.com"),
            password = "secret",
            tlsActive = true,
            serverMechanisms = setOf(SaslMechanism.Plain, SaslMechanism.ScramSha256),
        )

        assertIs<XmppResult.Success<SaslMechanism>>(result)
        assertEquals(SaslMechanism.ScramSha256, result.value)
    }

    @Test
    fun test_authenticate_whenOnlyPlainWithoutTls_returnsFailure() = runTest {
        val result = service.authenticate(
            jid = Jid(local = "alice", domain = "example.com"),
            password = "secret",
            tlsActive = false,
            serverMechanisms = setOf(SaslMechanism.Plain),
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals("SASL PLAIN requires TLS.", result.error.message)
    }

    @Test
    fun test_authenticate_whenNoOverlap_returnsFailure() = runTest {
        val result = service.authenticate(
            jid = Jid(local = "alice", domain = "example.com"),
            password = "secret",
            tlsActive = true,
            serverMechanisms = emptySet(),
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals("No compatible SASL mechanism found with server.", result.error.message)
    }
}
