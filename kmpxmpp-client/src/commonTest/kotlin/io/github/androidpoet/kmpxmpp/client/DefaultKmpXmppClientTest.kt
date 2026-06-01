package io.github.androidpoet.kmpxmpp.client

import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppError
import io.github.androidpoet.kmpxmpp.core.XmppFeatureCatalog
import io.github.androidpoet.kmpxmpp.core.XmppFeaturePolicy
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.XmppRetryPolicy
import io.github.androidpoet.kmpxmpp.sasl.SaslAuthenticationService
import io.github.androidpoet.kmpxmpp.sasl.SaslChannelBinding
import io.github.androidpoet.kmpxmpp.sasl.SaslMechanism
import io.github.androidpoet.kmpxmpp.security.SecurityPolicy
import io.github.androidpoet.kmpxmpp.security.TlsMode
import io.github.androidpoet.kmpxmpp.stream.XmppSessionContext
import io.github.androidpoet.kmpxmpp.stream.XmppStreamEngine
import io.github.androidpoet.kmpxmpp.stream.XmppStreamState
import io.github.androidpoet.kmpxmpp.transport.XmppTransport
import io.github.androidpoet.kmpxmpp.xml.XmppSaslProfile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

class DefaultKmpXmppClientTest {
    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun test_client_authenticate_whenScramSha256ChallengeAndVerifierValid_returnsSuccess() = runTest {
        val jid = Jid("alice", "example.com")
        val password = "pencil"
        val nonce = "fixedClientNonce123"
        val serverNonce = "${nonce}ServerNonce"
        val salt = "salt123".encodeToByteArray()
        val iterations = 4096
        val serverFirstMessage = "r=$serverNonce,s=${Base64.encode(salt)},i=$iterations"
        val serverSuccess = scramServerSuccess(
            mechanism = SaslMechanism.ScramSha256,
            username = "alice",
            password = password,
            nonce = nonce,
            serverFirst = serverFirstMessage,
        )

        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
            contextAfterStart = XmppSessionContext(
                tlsActive = true,
                serverMechanisms = setOf(SaslMechanism.ScramSha256),
            ),
        )
        val transport = FakeTransport(
            readResults = listOf(
                XmppResult.Success("<challenge xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>${Base64.encode(serverFirstMessage.encodeToByteArray())}</challenge>"),
                XmppResult.Success("<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>${Base64.encode(serverSuccess.encodeToByteArray())}</success>"),
                XmppResult.Success("<stream:features><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></stream:features>"),
                XmppResult.Success("<iq type='result' id='bind-1'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></iq>"),
            ),
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = transport,
            saslAuthenticationService = FakeAuthService(XmppResult.Success(SaslMechanism.ScramSha256)),
            nonceGenerator = { nonce },
        )

        client.connect()
        val result = client.authenticate(jid, password)

        assertIs<XmppResult.Success<Unit>>(result)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun test_client_authenticate_whenScramServerVerifierMismatch_returnsFailure() = runTest {
        val nonce = "fixedClientNonce123"
        val serverNonce = "${nonce}ServerNonce"
        val serverFirstMessage = "r=$serverNonce,s=${Base64.encode("salt123".encodeToByteArray())},i=4096"
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
            contextAfterStart = XmppSessionContext(
                tlsActive = true,
                serverMechanisms = setOf(SaslMechanism.ScramSha256),
            ),
        )
        val transport = FakeTransport(
            readResults = listOf(
                XmppResult.Success("<challenge xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>${Base64.encode(serverFirstMessage.encodeToByteArray())}</challenge>"),
                XmppResult.Success("<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>${Base64.encode("v=invalidVerifier".encodeToByteArray())}</success>"),
            ),
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = transport,
            saslAuthenticationService = FakeAuthService(XmppResult.Success(SaslMechanism.ScramSha256)),
            nonceGenerator = { nonce },
        )

        client.connect()
        val result = client.authenticate(Jid("alice", "example.com"), "pencil")

        assertIs<XmppResult.Failure>(result)
        assertEquals("SCRAM server verifier mismatch.", result.error.message)
    }

    @Test
    fun test_client_advertisedFeatures_whenAuthCompletes_exposesCapabilities() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
            contextAfterStart = XmppSessionContext(
                tlsActive = true,
                serverMechanisms = setOf(SaslMechanism.Plain),
            ),
        )
        val transport = FakeTransport(
            readResults = listOf(
                XmppResult.Success(
                    "<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>",
                ),
                XmppResult.Success(
                    "<stream:features><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/>" +
                        "<c xmlns='urn:xmpp:carbons:2'/></stream:features>",
                ),
                XmppResult.Success("<iq type='result' id='bind-1'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></iq>"),
            ),
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = transport,
            saslAuthenticationService = FakeAuthService(XmppResult.Success(SaslMechanism.Plain)),
            featurePolicy = XmppFeaturePolicy.AllowAll,
        )

        client.connect()
        val authResult = client.authenticate(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Success<Unit>>(authResult)
        assertTrue(client.supportsFeature(XmppFeatureCatalog.Carbons))
        assertTrue(client.advertisedFeatures().isNotEmpty())
    }

    @Test
    fun test_client_connect_whenStreamStarts_returnsSuccess() = runTest {
        val client = DefaultKmpXmppClient(
            streamEngine = FakeStreamEngine(startResult = XmppResult.Success(Unit), stateAfterStart = XmppStreamState.Ready),
            transport = FakeTransport(),
        )

        val result = client.connect()

        assertIs<XmppResult.Success<Unit>>(result)
    }

    @Test
    fun test_client_connect_whenFirstAttemptFailsAndRetryEnabled_succeedsAfterRetry() = runTest {
        val stream = FlakyStreamEngine(failuresBeforeSuccess = 1)
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = FakeTransport(),
            connectRetryPolicy = XmppRetryPolicy(maxAttempts = 2),
        )

        val result = client.connect()

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(2, stream.startCalls)
    }

    @Test
    fun test_client_authenticate_whenNotReady_returnsFailure() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Connected,
        )
        val client = DefaultKmpXmppClient(streamEngine = stream, transport = FakeTransport())

        val result = client.authenticate(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Failure>(result)
        assertEquals("Cannot authenticate before stream is ready.", result.error.message)
    }

    @Test
    fun test_client_authenticate_whenSessionContextMissing_returnsFailure() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
            contextAfterStart = null,
        )
        val client = DefaultKmpXmppClient(streamEngine = stream, transport = FakeTransport())

        client.connect()
        val result = client.authenticate(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Failure>(result)
        assertEquals("Missing stream session context.", result.error.message)
    }

    @Test
    fun test_client_authenticate_whenAuthServiceFails_propagatesFailure() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = FakeTransport(),
            saslAuthenticationService = FakeAuthService(
                result = XmppResult.Failure(XmppError("auth-service-failed")),
            ),
        )

        client.connect()
        val result = client.authenticate(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Failure>(result)
        assertEquals("auth-service-failed", result.error.message)
    }

    @Test
    fun test_client_authenticate_whenAuthServiceThrows_returnsFailure() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = FakeTransport(),
            saslAuthenticationService = ThrowingAuthService(),
        )

        client.connect()
        val result = client.authenticate(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Failure>(result)
        assertEquals("auth-service-crash", result.error.message)
    }

    @Test
    fun test_client_authenticate_whenPolicyRejectsMechanism_propagatesFailure() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
            contextAfterStart = XmppSessionContext(
                tlsActive = false,
                serverMechanisms = setOf(SaslMechanism.Plain),
            ),
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = FakeTransport(),
            securityPolicy = SecurityPolicy(
                tlsMode = TlsMode.Required,
                allowPlainAuthWithoutTls = false,
            ),
            saslAuthenticationService = FakeAuthService(
                result = XmppResult.Success(SaslMechanism.Plain),
            ),
        )

        client.connect()
        val result = client.authenticate(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Failure>(result)
        assertEquals("SASL PLAIN without TLS is disabled by policy.", result.error.message)
    }

    @Test
    fun test_client_authenticate_whenServerRejectsSasl_returnsFailure() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
            contextAfterStart = XmppSessionContext(
                tlsActive = true,
                serverMechanisms = setOf(SaslMechanism.Plain),
            ),
        )
        val transport = FakeTransport(
            readResults = listOf(
                XmppResult.Success("<failure xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>"),
            ),
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = transport,
            saslAuthenticationService = FakeAuthService(XmppResult.Success(SaslMechanism.Plain)),
        )

        client.connect()
        val result = client.authenticate(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Failure>(result)
        assertEquals("SASL authentication rejected by server.", result.error.message)
    }

    @Test
    fun test_client_authenticate_whenStreamFeaturesArriveFragmented_returnsSuccess() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
            contextAfterStart = XmppSessionContext(
                tlsActive = true,
                serverMechanisms = setOf(SaslMechanism.Plain),
            ),
        )
        val transport = FakeTransport(
            readResults = listOf(
                XmppResult.Success("<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>"),
                XmppResult.Success("<stream:fea"),
                XmppResult.Success("tures><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/>"),
                XmppResult.Success("</stream:features>"),
                XmppResult.Success("<iq type='result' id='bind-1'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></iq>"),
            ),
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = transport,
            saslAuthenticationService = FakeAuthService(XmppResult.Success(SaslMechanism.Plain)),
        )

        client.connect()
        val result = client.authenticate(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Success<Unit>>(result)
    }

    @Test
    fun test_client_authenticate_whenFeatureTokensAppearOnlyInText_returnsFailure() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
            contextAfterStart = XmppSessionContext(
                tlsActive = true,
                serverMechanisms = setOf(SaslMechanism.Plain),
            ),
        )
        val deceptive = "<message><body>&lt;stream:features&gt;fake&lt;/stream:features&gt;</body></message>"
        val transport = FakeTransport(
            readResults = listOf(
                XmppResult.Success("<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>"),
                XmppResult.Success(deceptive),
                XmppResult.Success("<presence/>"),
                XmppResult.Success("<message/>"),
                XmppResult.Success("<iq type='result' id='noop'/>"),
                XmppResult.Success("<r/>"),
                XmppResult.Success("<a/>"),
            ),
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = transport,
            saslAuthenticationService = FakeAuthService(XmppResult.Success(SaslMechanism.Plain)),
        )

        client.connect()
        val result = client.authenticate(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Failure>(result)
        assertEquals("Stream restart did not return valid <stream:features>.", result.error.message)
    }

    @Test
    fun test_client_authenticate_whenServerAcceptsSaslAndBind_returnsSuccess() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
            contextAfterStart = XmppSessionContext(
                tlsActive = true,
                serverMechanisms = setOf(SaslMechanism.Plain),
            ),
        )
        val transport = FakeTransport(
            readResults = listOf(
                XmppResult.Success("<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>"),
                XmppResult.Success("<stream:features><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></stream:features>"),
                XmppResult.Success("<iq type='result' id='bind-1'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></iq>"),
            ),
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = transport,
            saslAuthenticationService = FakeAuthService(XmppResult.Success(SaslMechanism.Plain)),
        )

        client.connect()
        val result = client.authenticate(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Success<Unit>>(result)
    }

    @Test
    fun test_client_authenticate_whenSuccessMissingSaslNamespace_returnsFailure() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
            contextAfterStart = XmppSessionContext(
                tlsActive = true,
                serverMechanisms = setOf(SaslMechanism.Plain),
            ),
        )
        val transport = FakeTransport(
            readResults = listOf(
                XmppResult.Success("<success/>"),
            ),
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = transport,
            saslAuthenticationService = FakeAuthService(XmppResult.Success(SaslMechanism.Plain)),
        )

        client.connect()
        val result = client.authenticate(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Failure>(result)
        assertEquals("SASL authentication rejected by server.", result.error.message)
    }

    @Test
    fun test_client_authenticate_whenBindIqNotResult_returnsFailure() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
            contextAfterStart = XmppSessionContext(
                tlsActive = true,
                serverMechanisms = setOf(SaslMechanism.Plain),
            ),
        )
        val transport = FakeTransport(
            readResults = listOf(
                XmppResult.Success("<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>"),
                XmppResult.Success("<stream:features><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></stream:features>"),
                XmppResult.Success("<iq type='error' id='bind-1'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></iq>"),
            ),
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = transport,
            saslAuthenticationService = FakeAuthService(XmppResult.Success(SaslMechanism.Plain)),
        )

        client.connect()
        val result = client.authenticate(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Failure>(result)
        assertEquals("Resource bind rejected by server.", result.error.message)
    }

    @Test
    fun test_client_authenticate_whenSaslReplyContainsFailure_returnsFailure() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
            contextAfterStart = XmppSessionContext(
                tlsActive = true,
                serverMechanisms = setOf(SaslMechanism.Plain),
            ),
        )
        val transport = FakeTransport(
            readResults = listOf(
                XmppResult.Success(
                    "<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>" +
                        "<failure xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>",
                ),
            ),
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = transport,
            saslAuthenticationService = FakeAuthService(XmppResult.Success(SaslMechanism.Plain)),
        )

        client.connect()
        val result = client.authenticate(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Failure>(result)
        assertEquals("SASL authentication rejected by server.", result.error.message)
    }

    @Test
    fun test_client_authenticate_whenBindReplyContainsError_returnsFailure() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
            contextAfterStart = XmppSessionContext(
                tlsActive = true,
                serverMechanisms = setOf(SaslMechanism.Plain),
            ),
        )
        val transport = FakeTransport(
            readResults = listOf(
                XmppResult.Success("<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>"),
                XmppResult.Success("<stream:features><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></stream:features>"),
                XmppResult.Success("<iq type='result'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></iq><iq type='error'/>"),
            ),
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = transport,
            saslAuthenticationService = FakeAuthService(XmppResult.Success(SaslMechanism.Plain)),
        )

        client.connect()
        val result = client.authenticate(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Failure>(result)
        assertEquals("Resource bind rejected by server.", result.error.message)
    }

    @Test
    fun test_client_authenticate_whenSasl2Profile_usesAuthenticateAndSkipsStreamRestartWrite() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
            contextAfterStart = XmppSessionContext(
                tlsActive = true,
                serverMechanisms = setOf(SaslMechanism.Plain),
                saslProfile = XmppSaslProfile.Sasl2,
            ),
        )
        val transport = FakeTransport(
            readResults = listOf(
                XmppResult.Success("<success xmlns='urn:xmpp:sasl:2'/>"),
                XmppResult.Success("<stream:features><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></stream:features>"),
                XmppResult.Success("<iq type='result' id='bind-1'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></iq>"),
            ),
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = transport,
            saslAuthenticationService = FakeAuthService(XmppResult.Success(SaslMechanism.Plain)),
        )

        client.connect()
        val result = client.authenticate(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(2, transport.writes.size)
        assertTrue(transport.writes[0].contains("<authenticate"))
        assertTrue(transport.writes[0].contains("urn:xmpp:sasl:2"))
        assertTrue(transport.writes[1].contains("<iq type='set'"))
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun test_client_authenticate_whenPlusAdvertisedButNoProvider_filtersPlusAndUsesScram() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
            contextAfterStart = XmppSessionContext(
                tlsActive = true,
                serverMechanisms = setOf(SaslMechanism.ScramSha256Plus, SaslMechanism.ScramSha256),
            ),
        )
        val transport = FakeTransport(
            readResults = listOf(
                XmppResult.Success("<challenge xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>${Base64.encode("r=fixedClientNonce123ServerNonce,s=${Base64.encode("salt123".encodeToByteArray())},i=4096".encodeToByteArray())}</challenge>"),
                XmppResult.Success("<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>${Base64.encode(scramServerSuccess(SaslMechanism.ScramSha256, "alice", "secret", "fixedClientNonce123", "r=fixedClientNonce123ServerNonce,s=${Base64.encode("salt123".encodeToByteArray())},i=4096").encodeToByteArray())}</success>"),
                XmppResult.Success("<stream:features><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></stream:features>"),
                XmppResult.Success("<iq type='result' id='bind-1'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></iq>"),
            ),
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = transport,
            nonceGenerator = { "fixedClientNonce123" },
        )

        client.connect()
        val result = client.authenticate(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Success<Unit>>(result)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun test_client_authenticate_whenPlusMechanismSelectedWithoutBindingProvider_returnsFailure() = runTest {
        val nonce = "fixedClientNonce123"
        val serverNonce = "${nonce}ServerNonce"
        val serverFirstMessage = "r=$serverNonce,s=${Base64.encode("salt123".encodeToByteArray())},i=4096"
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
            contextAfterStart = XmppSessionContext(
                tlsActive = true,
                serverMechanisms = setOf(SaslMechanism.ScramSha256Plus),
            ),
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = FakeTransport(
                readResults = listOf(
                    XmppResult.Success("<challenge xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>${Base64.encode(serverFirstMessage.encodeToByteArray())}</challenge>"),
                ),
            ),
            saslAuthenticationService = FakeAuthService(XmppResult.Success(SaslMechanism.ScramSha256Plus)),
            nonceGenerator = { nonce },
        )

        client.connect()
        val result = client.authenticate(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Failure>(result)
        assertEquals("SCRAM PLUS requires channel-binding data provider; not configured.", result.error.message)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun test_client_authenticate_whenScramSha256PlusWithBindingProvider_returnsSuccess() = runTest {
        val jid = Jid("alice", "example.com")
        val password = "pencil"
        val nonce = "fixedClientNonce123"
        val serverNonce = "${nonce}ServerNonce"
        val salt = "salt123".encodeToByteArray()
        val iterations = 4096
        val serverFirstMessage = "r=$serverNonce,s=${Base64.encode(salt)},i=$iterations"
        val gs2Header = "p=tls-exporter,,"
        val bindingData = "test-binding".encodeToByteArray()
        val channelBindingBase64 = Base64.encode(gs2Header.encodeToByteArray() + bindingData)
        val serverSuccess = scramServerSuccess(
            mechanism = SaslMechanism.ScramSha256Plus,
            username = "alice",
            password = password,
            nonce = nonce,
            serverFirst = serverFirstMessage,
            channelBindingBase64 = channelBindingBase64,
        )

        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
            contextAfterStart = XmppSessionContext(
                tlsActive = true,
                serverMechanisms = setOf(SaslMechanism.ScramSha256Plus),
            ),
        )
        val transport = FakeTransport(
            readResults = listOf(
                XmppResult.Success("<challenge xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>${Base64.encode(serverFirstMessage.encodeToByteArray())}</challenge>"),
                XmppResult.Success("<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>${Base64.encode(serverSuccess.encodeToByteArray())}</success>"),
                XmppResult.Success("<stream:features><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></stream:features>"),
                XmppResult.Success("<iq type='result' id='bind-1'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></iq>"),
            ),
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = transport,
            saslAuthenticationService = FakeAuthService(XmppResult.Success(SaslMechanism.ScramSha256Plus)),
            nonceGenerator = { nonce },
            channelBindingProvider = { SaslChannelBinding(type = "tls-exporter", data = bindingData) },
        )

        client.connect()
        val result = client.authenticate(jid, password)

        assertIs<XmppResult.Success<Unit>>(result)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun test_client_authenticate_whenScramStanzasArePrefixed_returnsSuccess() = runTest {
        val jid = Jid("alice", "example.com")
        val password = "secret"
        val nonce = "fixedClientNonce123"
        val serverFirstMessage = "r=fixedClientNonce123ServerNonce,s=${Base64.encode("salt123".encodeToByteArray())},i=4096"
        val serverSuccess = scramServerSuccess(
            mechanism = SaslMechanism.ScramSha256,
            username = "alice",
            password = password,
            nonce = nonce,
            serverFirst = serverFirstMessage,
        )

        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
            contextAfterStart = XmppSessionContext(
                tlsActive = true,
                serverMechanisms = setOf(SaslMechanism.ScramSha256),
            ),
        )
        val transport = FakeTransport(
            readResults = listOf(
                XmppResult.Success("<s:challenge xmlns:s='urn:ietf:params:xml:ns:xmpp-sasl' xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>${Base64.encode(serverFirstMessage.encodeToByteArray())}</s:challenge>"),
                XmppResult.Success("<s:success xmlns:s='urn:ietf:params:xml:ns:xmpp-sasl' xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>${Base64.encode(serverSuccess.encodeToByteArray())}</s:success>"),
                XmppResult.Success("<stream:features><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></stream:features>"),
                XmppResult.Success("<iq type='result' id='bind-1'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></iq>"),
            ),
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = transport,
            saslAuthenticationService = FakeAuthService(XmppResult.Success(SaslMechanism.ScramSha256)),
            nonceGenerator = { nonce },
        )

        client.connect()
        val result = client.authenticate(jid, password)

        assertIs<XmppResult.Success<Unit>>(result)
    }

    @Test
    fun test_client_sendStanza_whenNotAuthenticated_returnsFailure() = runTest {
        val client = DefaultKmpXmppClient(
            streamEngine = FakeStreamEngine(startResult = XmppResult.Success(Unit), stateAfterStart = XmppStreamState.Ready),
            transport = FakeTransport(),
        )

        val result = client.sendStanza("<message/>")

        assertIs<XmppResult.Failure>(result)
        assertEquals("Cannot send stanza before authentication.", result.error.message)
    }

    @Test
    fun test_client_sendStanza_whenAuthenticated_propagatesTransportResult() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
        )
        val transport = FakeTransport(
            writeResults = listOf(
                XmppResult.Success(Unit), // SASL auth write
                XmppResult.Success(Unit), // stream restart write
                XmppResult.Success(Unit), // bind write
                XmppResult.Failure(XmppError("write-failed")), // message write
            ),
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = transport,
            saslAuthenticationService = FakeAuthService(XmppResult.Success(SaslMechanism.Plain)),
        )

        client.connect()
        client.authenticate(Jid("alice", "example.com"), "secret")
        val result = client.sendStanza("<message/>")

        assertIs<XmppResult.Failure>(result)
        assertEquals("write-failed", result.error.message)
    }

    @Test
    fun test_client_sendStanza_whenTransportThrows_returnsFailure() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = ThrowingTransport(),
            saslAuthenticationService = FakeAuthService(XmppResult.Success(SaslMechanism.Plain)),
        )

        client.connect()
        client.authenticate(Jid("alice", "example.com"), "secret")
        val result = client.sendStanza("<message/>")

        assertIs<XmppResult.Failure>(result)
        assertEquals("transport-write-crash", result.error.message)
    }

    @Test
    fun test_client_disconnect_whenCalled_clearsSessionAndReturnsSuccess() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stopResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = FakeTransport(),
            saslAuthenticationService = FakeAuthService(XmppResult.Success(SaslMechanism.Plain)),
        )

        client.connect()
        client.authenticate(Jid("alice", "example.com"), "secret")
        val disconnectResult = client.disconnect()
        val sendAfterDisconnect = client.sendStanza("<message/>")

        assertIs<XmppResult.Success<Unit>>(disconnectResult)
        assertIs<XmppResult.Failure>(sendAfterDisconnect)
        assertEquals("Cannot send stanza before authentication.", sendAfterDisconnect.error.message)
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun scramServerSuccess(
    mechanism: SaslMechanism,
    username: String,
    password: String,
    nonce: String,
    serverFirst: String,
    channelBindingBase64: String = Base64.encode("n,,".encodeToByteArray()),
): String {
    val fields = serverFirst.split(",").associate {
        val idx = it.indexOf('=')
        it.substring(0, idx) to it.substring(idx + 1)
    }
    val serverNonce = fields.getValue("r")
    val salt = fields.getValue("s").decodeBase64()!!.toByteArray()
    val iterations = fields.getValue("i").toInt()
    val clientFirstBare = "n=$username,r=$nonce"
    val clientFinalWithoutProof = "c=$channelBindingBase64,r=$serverNonce"
    val authMessage = "$clientFirstBare,$serverFirst,$clientFinalWithoutProof"
    val hash = when (mechanism) {
        SaslMechanism.ScramSha256, SaslMechanism.ScramSha256Plus -> "SHA-256"
        SaslMechanism.ScramSha1, SaslMechanism.ScramSha1Plus -> "SHA-1"
        SaslMechanism.Plain -> error("PLAIN is not a SCRAM mechanism.")
    }
    val saltedPassword = pbkdf2Test(password.encodeToByteArray(), salt, iterations, hash)
    val serverKey = hmacTest(saltedPassword, "Server Key".encodeToByteArray(), hash)
    val serverSignature = hmacTest(serverKey, authMessage.encodeToByteArray(), hash)
    return "v=${Base64.encode(serverSignature)}"
}

private fun pbkdf2Test(password: ByteArray, salt: ByteArray, iterations: Int, hash: String): ByteArray {
    val int1 = byteArrayOf(0, 0, 0, 1)
    val u1 = hmacTest(password, salt + int1, hash)
    var output = u1.copyOf()
    var previous = u1
    repeat(iterations - 1) {
        previous = hmacTest(password, previous, hash)
        output = xorTest(output, previous)
    }
    return output
}

private fun hmacTest(key: ByteArray, message: ByteArray, hash: String): ByteArray = when (hash) {
    "SHA-1" -> message.toByteString().hmacSha1(key.toByteString()).toByteArray()
    "SHA-256" -> message.toByteString().hmacSha256(key.toByteString()).toByteArray()
    else -> error("Unsupported hash")
}

private fun xorTest(a: ByteArray, b: ByteArray): ByteArray {
    val out = ByteArray(a.size)
    for (i in a.indices) {
        out[i] = (a[i].toInt() xor b[i].toInt()).toByte()
    }
    return out
}

private class FakeAuthService(
    private val result: XmppResult<SaslMechanism>,
) : SaslAuthenticationService {
    override suspend fun authenticate(
        jid: Jid,
        password: String,
        tlsActive: Boolean,
        serverMechanisms: Set<SaslMechanism>,
    ): XmppResult<SaslMechanism> = result
}

private class ThrowingAuthService : SaslAuthenticationService {
    override suspend fun authenticate(
        jid: Jid,
        password: String,
        tlsActive: Boolean,
        serverMechanisms: Set<SaslMechanism>,
    ): XmppResult<SaslMechanism> = error("auth-service-crash")
}

private class FakeStreamEngine(
    private val startResult: XmppResult<Unit>,
    private val stopResult: XmppResult<Unit> = XmppResult.Success(Unit),
    private val stateAfterStart: XmppStreamState,
    private val contextAfterStart: XmppSessionContext? = XmppSessionContext(
        tlsActive = true,
        serverMechanisms = setOf(SaslMechanism.ScramSha256, SaslMechanism.Plain),
    ),
) : XmppStreamEngine {
    override var state: XmppStreamState = XmppStreamState.Disconnected
    override var sessionContext: XmppSessionContext? = null

    override suspend fun start(): XmppResult<Unit> {
        if (startResult is XmppResult.Success) {
            state = stateAfterStart
            sessionContext = contextAfterStart
        }
        return startResult
    }

    override suspend fun stop(): XmppResult<Unit> {
        if (stopResult is XmppResult.Success) {
            state = XmppStreamState.Disconnected
            sessionContext = null
        }
        return stopResult
    }
}

private class FlakyStreamEngine(
    private val failuresBeforeSuccess: Int,
) : XmppStreamEngine {
    override var state: XmppStreamState = XmppStreamState.Disconnected
    override var sessionContext: XmppSessionContext? = XmppSessionContext(
        tlsActive = true,
        serverMechanisms = setOf(SaslMechanism.ScramSha256),
    )
    var startCalls: Int = 0

    override suspend fun start(): XmppResult<Unit> {
        startCalls += 1
        return if (startCalls <= failuresBeforeSuccess) {
            XmppResult.Failure(XmppError("temporary-connect-fail", recoverable = true))
        } else {
            state = XmppStreamState.Ready
            XmppResult.Success(Unit)
        }
    }

    override suspend fun stop(): XmppResult<Unit> {
        state = XmppStreamState.Disconnected
        sessionContext = null
        return XmppResult.Success(Unit)
    }
}

private class FakeTransport(
    private val writeResults: List<XmppResult<Unit>> = listOf(XmppResult.Success(Unit)),
    private val readResults: List<XmppResult<String>> = listOf(
        XmppResult.Success("<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>"),
        XmppResult.Success("<stream:features><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></stream:features>"),
        XmppResult.Success("<iq type='result' id='bind-1'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></iq>"),
    ),
) : XmppTransport {
    private var writeIndex: Int = 0
    private var readIndex: Int = 0
    val writes: MutableList<String> = mutableListOf()

    override suspend fun connect(host: String, port: Int): XmppResult<Unit> = XmppResult.Success(Unit)

    override suspend fun write(data: String): XmppResult<Unit> {
        writes += data
        val result = writeResults.getOrNull(writeIndex) ?: writeResults.last()
        writeIndex += 1
        return result
    }

    override suspend fun read(): XmppResult<String> {
        val result = readResults.getOrNull(readIndex) ?: readResults.last()
        readIndex += 1
        return result
    }

    override suspend fun close(): XmppResult<Unit> = XmppResult.Success(Unit)
}

private class ThrowingTransport : XmppTransport {
    private var readCalls: Int = 0

    override suspend fun connect(host: String, port: Int): XmppResult<Unit> = XmppResult.Success(Unit)

    override suspend fun write(data: String): XmppResult<Unit> {
        if (data.contains("<message")) {
            error("transport-write-crash")
        }
        return XmppResult.Success(Unit)
    }

    override suspend fun read(): XmppResult<String> {
        readCalls += 1
        return if (readCalls == 1) {
            XmppResult.Success("<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>")
        } else if (readCalls == 2) {
            XmppResult.Success("<stream:features><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></stream:features>")
        } else {
            XmppResult.Success("<iq type='result' id='bind-1'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></iq>")
        }
    }

    override suspend fun close(): XmppResult<Unit> = XmppResult.Success(Unit)
}
