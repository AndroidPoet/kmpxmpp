package io.github.androidpoet.kmpxmpp.client

import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.XmppResultException
import io.github.androidpoet.kmpxmpp.core.flatMap
import io.github.androidpoet.kmpxmpp.core.getOrThrow
import io.github.androidpoet.kmpxmpp.core.retryXmppResult
import io.github.androidpoet.kmpxmpp.core.XmppRetryPolicy
import io.github.androidpoet.kmpxmpp.core.xmppResultOfSuspend
import io.github.androidpoet.kmpxmpp.sasl.DefaultSaslAuthenticationService
import io.github.androidpoet.kmpxmpp.sasl.SaslAuthenticationService
import io.github.androidpoet.kmpxmpp.sasl.SaslMechanism
import io.github.androidpoet.kmpxmpp.security.SecurityPolicy
import io.github.androidpoet.kmpxmpp.security.validateAuthMechanism
import io.github.androidpoet.kmpxmpp.stream.XmppStreamEngine
import io.github.androidpoet.kmpxmpp.stream.XmppStreamState
import io.github.androidpoet.kmpxmpp.transport.XmppTransport
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

public class DefaultKmpXmppClient(
    private val streamEngine: XmppStreamEngine,
    private val transport: XmppTransport,
    private val securityPolicy: SecurityPolicy = SecurityPolicy(),
    private val saslAuthenticationService: SaslAuthenticationService = DefaultSaslAuthenticationService(),
    private val connectRetryPolicy: XmppRetryPolicy = XmppRetryPolicy(maxAttempts = 1),
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

            val selectedMechanism = saslAuthenticationService.authenticate(
                jid = jid,
                password = password,
                tlsActive = context.tlsActive,
                serverMechanisms = context.serverMechanisms,
            ).getOrThrow()

            securityPolicy.validateAuthMechanism(
                mechanism = selectedMechanism,
                tlsActive = context.tlsActive,
            ).getOrThrow()

            val authXml = buildSaslAuthXml(
                mechanism = selectedMechanism,
                jid = jid,
                password = password,
            )
            transport.write(authXml).getOrThrow()

            val authServerReply = transport.read().getOrThrow()
            if (!authServerReply.contains("<success", ignoreCase = true)) {
                throw XmppResultException("SASL authentication rejected by server.")
            }

            transport.write(buildBindRequestXml()).getOrThrow()
            val bindReply = transport.read().getOrThrow()
            if (!bindReply.contains("type='result'", ignoreCase = true) &&
                !bindReply.contains("type=\"result\"", ignoreCase = true)
            ) {
                throw XmppResultException("Resource bind rejected by server.")
            }

            authenticatedJid = jid
        }

    @OptIn(ExperimentalEncodingApi::class)
    private fun buildSaslAuthXml(
        mechanism: SaslMechanism,
        jid: Jid,
        password: String,
    ): String = when (mechanism) {
        SaslMechanism.Plain -> {
            val authzid = ""
            val authcid = jid.local ?: jid.domain
            val raw = "$authzid\u0000$authcid\u0000$password"
            val encoded = Base64.encode(raw.encodeToByteArray())
            "<auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='PLAIN'>$encoded</auth>\n"
        }
        SaslMechanism.ScramSha1,
        SaslMechanism.ScramSha256,
        -> throw XmppResultException("SASL $mechanism wire exchange is not implemented yet.")
    }

    private fun buildBindRequestXml(): String =
        "<iq type='set' id='bind-1'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'><resource>kmpxmpp</resource></bind></iq>\n"

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
