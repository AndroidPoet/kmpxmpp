package io.github.androidpoet.kmpxmpp.client

import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.XmppResultException
import io.github.androidpoet.kmpxmpp.core.flatMap
import io.github.androidpoet.kmpxmpp.core.getOrThrow
import io.github.androidpoet.kmpxmpp.core.retryXmppResult
import io.github.androidpoet.kmpxmpp.core.XmppRetryPolicy
import io.github.androidpoet.kmpxmpp.core.xmppResultOfSuspend
import io.github.androidpoet.kmpxmpp.sasl.DefaultSaslAuthenticationService
import io.github.androidpoet.kmpxmpp.sasl.SaslChannelBinding
import io.github.androidpoet.kmpxmpp.sasl.SaslAuthenticationService
import io.github.androidpoet.kmpxmpp.sasl.SaslMechanism
import io.github.androidpoet.kmpxmpp.security.SecurityPolicy
import io.github.androidpoet.kmpxmpp.security.validateAuthMechanism
import io.github.androidpoet.kmpxmpp.stream.XmppStreamEngine
import io.github.androidpoet.kmpxmpp.stream.XmppStreamState
import io.github.androidpoet.kmpxmpp.transport.XmppTransport
import io.github.androidpoet.kmpxmpp.xml.DefaultXmppAuthStanzaParser
import io.github.androidpoet.kmpxmpp.xml.XmppAuthStanzaParser
import io.github.androidpoet.kmpxmpp.xml.XmppSaslProfile
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

public class DefaultKmpXmppClient(
    private val streamEngine: XmppStreamEngine,
    private val transport: XmppTransport,
    private val securityPolicy: SecurityPolicy = SecurityPolicy(),
    private val saslAuthenticationService: SaslAuthenticationService = DefaultSaslAuthenticationService(),
    private val channelBindingProvider: (() -> SaslChannelBinding?)? = null,
    private val authStanzaParser: XmppAuthStanzaParser = DefaultXmppAuthStanzaParser(),
    private val connectRetryPolicy: XmppRetryPolicy = XmppRetryPolicy(maxAttempts = 1),
    private val nonceGenerator: () -> String = { randomNonce() },
) : KmpXmppClient {

    private var authenticatedJid: Jid? = null

    override suspend fun connect(): XmppResult<Unit> =
        retryXmppResult(policy = connectRetryPolicy) {
            xmppResultOfSuspend(stage = XmppErrorStage.Connect, recoverable = true) {
                streamEngine.start().getOrThrow()
            }
        }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun authenticate(jid: Jid, password: String): XmppResult<Unit> =
        xmppResultOfSuspend(stage = XmppErrorStage.Authentication, recoverable = true) {
            if (streamEngine.state != XmppStreamState.Ready) {
                throw XmppResultException("Cannot authenticate before stream is ready.")
            }

            val context = streamEngine.sessionContext
                ?: throw XmppResultException("Missing stream session context.")
            val hasBindingProvider = channelBindingProvider != null
            val effectiveMechanisms = if (hasBindingProvider) {
                context.serverMechanisms
            } else {
                context.serverMechanisms.filterNot {
                    it == SaslMechanism.ScramSha1Plus || it == SaslMechanism.ScramSha256Plus
                }.toSet()
            }

            val selectedMechanism = saslAuthenticationService.authenticate(
                jid = jid,
                password = password,
                tlsActive = context.tlsActive,
                serverMechanisms = effectiveMechanisms,
            ).getOrThrow()

            securityPolicy.validateAuthMechanism(
                mechanism = selectedMechanism,
                tlsActive = context.tlsActive,
            ).getOrThrow()

            when (selectedMechanism) {
                SaslMechanism.Plain -> {
                    val authXml = buildSaslAuthXml(
                        mechanism = selectedMechanism,
                        jid = jid,
                        password = password,
                        saslProfile = context.saslProfile,
                    )
                    transport.write(authXml).getOrThrow()
                    val authServerReply = transport.read().getOrThrow()
                    if (!authStanzaParser.isSaslSuccessReply(authServerReply, saslNamespaceFor(context.saslProfile))) {
                        throw XmppResultException("SASL authentication rejected by server.")
                    }
                }
                SaslMechanism.ScramSha1,
                SaslMechanism.ScramSha256,
                SaslMechanism.ScramSha1Plus,
                SaslMechanism.ScramSha256Plus,
                -> {
                    runScramExchange(
                        mechanism = selectedMechanism,
                        jid = jid,
                        password = password,
                        saslProfile = context.saslProfile,
                    )
                }
            }
            if (context.saslProfile == XmppSaslProfile.Sasl1) {
                transport.write(buildStreamOpenXml(jid.domain)).getOrThrow()
                readAndValidateStreamFeatures()
            } else {
                readAndValidateStreamFeatures()
            }

            transport.write(buildBindRequestXml()).getOrThrow()
            val bindReply = transport.read().getOrThrow()
            if (!authStanzaParser.isBindSuccessReply(bindReply)) {
                throw XmppResultException("Resource bind rejected by server.")
            }

            authenticatedJid = jid
        }

    @OptIn(ExperimentalEncodingApi::class)
    private fun buildSaslAuthXml(
        mechanism: SaslMechanism,
        jid: Jid,
        password: String,
        saslProfile: XmppSaslProfile,
    ): String = when (mechanism) {
        SaslMechanism.Plain -> {
            val authzid = ""
            val authcid = jid.local ?: jid.domain
            val raw = "$authzid\u0000$authcid\u0000$password"
            val encoded = Base64.encode(raw.encodeToByteArray())
            if (saslProfile == XmppSaslProfile.Sasl2) {
                "<authenticate xmlns='urn:xmpp:sasl:2' mechanism='PLAIN'><initial-response>$encoded</initial-response></authenticate>\n"
            } else {
                "<auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='PLAIN'>$encoded</auth>\n"
            }
        }
        SaslMechanism.ScramSha1,
        SaslMechanism.ScramSha256,
        SaslMechanism.ScramSha1Plus,
        SaslMechanism.ScramSha256Plus,
        -> throw XmppResultException("SCRAM auth XML must be built by SCRAM exchange flow.")
    }

    private fun buildBindRequestXml(): String =
        "<iq type='set' id='bind-1'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'><resource>kmpxmpp</resource></bind></iq>\n"

    private fun buildStreamOpenXml(domain: String): String =
        "<?xml version='1.0'?><stream:stream to='$domain' xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' version='1.0'>\n"

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun runScramExchange(
        mechanism: SaslMechanism,
        jid: Jid,
        password: String,
        saslProfile: XmppSaslProfile,
    ) {
        val username = escapeScramUsername(jid.local ?: jid.domain)
        val nonce = nonceGenerator()
        val clientFirstBare = "n=$username,r=$nonce"
        val initialResponse = "n,,$clientFirstBare"
        val initialResponseBase64 = Base64.encode(initialResponse.encodeToByteArray())
        val initialExchangeXml = if (saslProfile == XmppSaslProfile.Sasl2) {
            "<authenticate xmlns='urn:xmpp:sasl:2' mechanism='${mechanism.token}'><initial-response>$initialResponseBase64</initial-response></authenticate>\n"
        } else {
            "<auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='${mechanism.token}'>$initialResponseBase64</auth>\n"
        }
        transport.write(initialExchangeXml).getOrThrow()
        val challengeXml = transport.read().getOrThrow()
        val serverFirst = parseSaslContent(challengeXml, "challenge", saslNamespaceFor(saslProfile))
            ?: throw XmppResultException("SASL SCRAM challenge missing.")
        val challengeFields = parseScramMessage(serverFirst)
        val serverNonce = challengeFields["r"] ?: throw XmppResultException("SCRAM challenge nonce is missing.")
        val saltBase64 = challengeFields["s"] ?: throw XmppResultException("SCRAM challenge salt is missing.")
        val iterationCount = challengeFields["i"]?.toIntOrNull()
            ?: throw XmppResultException("SCRAM challenge iterations are invalid.")
        if (!serverNonce.startsWith(nonce)) {
            throw XmppResultException("SCRAM challenge nonce does not extend client nonce.")
        }
        if (iterationCount <= 0) {
            throw XmppResultException("SCRAM challenge iterations must be positive.")
        }

        val salt = saltBase64.decodeBase64()?.toByteArray()
            ?: throw XmppResultException("SCRAM challenge salt is not valid base64.")
        val plusMechanism = mechanism == SaslMechanism.ScramSha1Plus || mechanism == SaslMechanism.ScramSha256Plus
        val channelBinding = if (plusMechanism) {
            val binding = channelBindingProvider?.invoke()
                ?: throw XmppResultException("SCRAM PLUS requires channel-binding data provider; not configured.")
            val gs2Header = "p=${binding.type},,"
            Base64.encode(gs2Header.encodeToByteArray() + binding.data)
        } else {
            Base64.encode("n,,".encodeToByteArray())
        }
        val clientFinalWithoutProof = "c=$channelBinding,r=$serverNonce"
        val authMessage = "$clientFirstBare,$serverFirst,$clientFinalWithoutProof"
        val hashAlgorithm = mechanism.hashAlgorithm

        val saltedPassword = pbkdf2(
            password = password.encodeToByteArray(),
            salt = salt,
            iterations = iterationCount,
            hash = hashAlgorithm,
        )
        val clientKey = hmac(saltedPassword, "Client Key".encodeToByteArray(), hashAlgorithm)
        val storedKey = hash(clientKey, hashAlgorithm)
        val clientSignature = hmac(storedKey, authMessage.encodeToByteArray(), hashAlgorithm)
        val clientProof = xor(clientKey, clientSignature)
        val clientFinal = "$clientFinalWithoutProof,p=${Base64.encode(clientProof)}"
        val responseXml = if (saslProfile == XmppSaslProfile.Sasl2) {
            "<response xmlns='urn:xmpp:sasl:2'>${Base64.encode(clientFinal.encodeToByteArray())}</response>\n"
        } else {
            "<response xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>${Base64.encode(clientFinal.encodeToByteArray())}</response>\n"
        }
        transport.write(responseXml).getOrThrow()

        val successXml = transport.read().getOrThrow()
        val successPayload = parseSaslContent(successXml, "success", saslNamespaceFor(saslProfile))
            ?: throw XmppResultException("SASL authentication rejected by server.")
        val successFields = parseScramMessage(successPayload)
        val serverVerifier = successFields["v"]
            ?: throw XmppResultException("SCRAM success is missing server verifier.")
        val serverKey = hmac(saltedPassword, "Server Key".encodeToByteArray(), hashAlgorithm)
        val serverSignature = hmac(serverKey, authMessage.encodeToByteArray(), hashAlgorithm)
        val expectedVerifier = Base64.encode(serverSignature)
        if (serverVerifier != expectedVerifier) {
            throw XmppResultException("SCRAM server verifier mismatch.")
        }
    }

    private fun parseSaslContent(xml: String, element: String, saslNamespace: String): String? {
        val target = element.lowercase()
        val parsed = parseElementContent(xml, target) ?: return null
        if (parsed.name != target) return null
        val elementNamespace = parsed.attributes["xmlns"]
        if (elementNamespace != saslNamespace) return null
        val value = parsed.content.trim()
        if (value.isBlank()) return null
        val decoded = value.decodeBase64()?.utf8() ?: return null
        return decoded
    }

    private fun parseElementContent(xml: String, expectedLocalName: String): ParsedElement? {
        val expected = expectedLocalName.lowercase()
        val startTag = XmppXmlMiniParser.parseStartTags(xml).firstOrNull { it.name == expected } ?: return null
        val content = XmppXmlMiniParser.tagInnerXml(xml, startTag) ?: return null
        return ParsedElement(
            name = startTag.name,
            attributes = startTag.attributes,
            content = content,
        )
    }

    private data class ParsedElement(
        val name: String,
        val attributes: Map<String, String>,
        val content: String,
    )

    private fun saslNamespaceFor(profile: XmppSaslProfile): String =
        if (profile == XmppSaslProfile.Sasl2) "urn:xmpp:sasl:2" else "urn:ietf:params:xml:ns:xmpp-sasl"

    private fun parseScramMessage(message: String): Map<String, String> =
        message.split(",")
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
            }
            .toMap()

    private suspend fun readAndValidateStreamFeatures() {
        val rawBuilder = StringBuilder()
        repeat(6) {
            val chunk = transport.read().getOrThrow()
            rawBuilder.append(chunk)
            val current = rawBuilder.toString()
            if (hasCompleteStreamFeatures(current)) {
                return
            }
        }
        throw XmppResultException("Stream restart did not return valid <stream:features>.")
    }

    private fun hasCompleteStreamFeatures(xml: String): Boolean {
        val featuresTag = XmppXmlMiniParser.parseStartTags(xml).firstOrNull { it.name == "features" } ?: return false
        return XmppXmlMiniParser.tagInnerXml(xml, featuresTag) != null
    }

    private fun escapeScramUsername(username: String): String =
        username.replace("=", "=3D").replace(",", "=2C")

    private fun hash(input: ByteArray, algorithm: String): ByteArray = when (algorithm) {
        "SHA-1" -> input.toByteString().sha1().toByteArray()
        "SHA-256" -> input.toByteString().sha256().toByteArray()
        else -> throw XmppResultException("Unsupported hash algorithm: $algorithm")
    }

    private fun hmac(key: ByteArray, message: ByteArray, algorithm: String): ByteArray = when (algorithm) {
        "SHA-1" -> message.toByteString().hmacSha1(key.toByteString()).toByteArray()
        "SHA-256" -> message.toByteString().hmacSha256(key.toByteString()).toByteArray()
        else -> throw XmppResultException("Unsupported HMAC algorithm: $algorithm")
    }

    private fun pbkdf2(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        hash: String,
    ): ByteArray {
        val int1 = byteArrayOf(0, 0, 0, 1)
        val u1 = hmac(password, salt + int1, hash)
        var output = u1.copyOf()
        var previous = u1
        repeat(iterations - 1) {
            previous = hmac(password, previous, hash)
            output = xor(output, previous)
        }
        return output
    }

    private fun xor(a: ByteArray, b: ByteArray): ByteArray {
        if (a.size != b.size) throw XmppResultException("SCRAM XOR input length mismatch.")
        val result = ByteArray(a.size)
        for (i in a.indices) {
            result[i] = (a[i].toInt() xor b[i].toInt()).toByte()
        }
        return result
    }

    private val SaslMechanism.token: String
        get() = when (this) {
            SaslMechanism.Plain -> "PLAIN"
            SaslMechanism.ScramSha1 -> "SCRAM-SHA-1"
            SaslMechanism.ScramSha256 -> "SCRAM-SHA-256"
            SaslMechanism.ScramSha1Plus -> "SCRAM-SHA-1-PLUS"
            SaslMechanism.ScramSha256Plus -> "SCRAM-SHA-256-PLUS"
        }

    private val SaslMechanism.hashAlgorithm: String
        get() = when (this) {
            SaslMechanism.ScramSha1 -> "SHA-1"
            SaslMechanism.ScramSha256 -> "SHA-256"
            SaslMechanism.ScramSha1Plus -> "SHA-1"
            SaslMechanism.ScramSha256Plus -> "SHA-256"
            SaslMechanism.Plain -> throw XmppResultException("PLAIN has no SCRAM hash algorithm.")
        }

    private companion object {
        private const val NONCE_LENGTH = 24
        private const val NONCE_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

        private fun randomNonce(length: Int = NONCE_LENGTH): String =
            buildString(length) {
                repeat(length) {
                    append(NONCE_ALPHABET[Random.nextInt(NONCE_ALPHABET.length)])
                }
            }
    }

    override suspend fun sendStanza(rawXml: String): XmppResult<Unit> =
        xmppResultOfSuspend(stage = XmppErrorStage.Messaging, recoverable = true) {
            if (authenticatedJid == null) {
                throw XmppResultException("Cannot send stanza before authentication.")
            }
            if (rawXml.isBlank()) {
                throw XmppResultException("Stanza payload cannot be blank.")
            }

            transport.write(rawXml).getOrThrow()
        }

    override suspend fun disconnect(): XmppResult<Unit> =
        xmppResultOfSuspend(stage = XmppErrorStage.Disconnect, recoverable = true) {
            streamEngine.stop().flatMap {
                authenticatedJid = null
                XmppResult.Success(Unit)
            }.getOrThrow()
        }
}
