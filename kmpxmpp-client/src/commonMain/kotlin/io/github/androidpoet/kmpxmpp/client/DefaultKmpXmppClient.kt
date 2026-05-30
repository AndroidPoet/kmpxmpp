package io.github.androidpoet.kmpxmpp.client

import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppError
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.flatMap
import io.github.androidpoet.kmpxmpp.sasl.DefaultSaslAuthenticationService
import io.github.androidpoet.kmpxmpp.sasl.SaslAuthenticationService
import io.github.androidpoet.kmpxmpp.sasl.SaslMechanism
import io.github.androidpoet.kmpxmpp.security.SecurityPolicy
import io.github.androidpoet.kmpxmpp.security.validateAuthMechanism
import io.github.androidpoet.kmpxmpp.stream.XmppStreamEngine
import io.github.androidpoet.kmpxmpp.stream.XmppStreamState
import io.github.androidpoet.kmpxmpp.transport.XmppTransport

public class DefaultKmpXmppClient(
    private val streamEngine: XmppStreamEngine,
    private val transport: XmppTransport,
    private val securityPolicy: SecurityPolicy = SecurityPolicy(),
    private val saslAuthenticationService: SaslAuthenticationService = DefaultSaslAuthenticationService(),
    private val tlsActiveProvider: () -> Boolean = { true },
    private val serverMechanismsProvider: () -> Set<SaslMechanism> = {
        setOf(SaslMechanism.ScramSha256, SaslMechanism.ScramSha1, SaslMechanism.Plain)
    },
) : KmpXmppClient {

    private var authenticatedJid: Jid? = null

    override suspend fun connect(): XmppResult<Unit> = streamEngine.start()

    override suspend fun authenticate(jid: Jid, password: String): XmppResult<Unit> {
        if (streamEngine.state != XmppStreamState.Ready) {
            return XmppResult.Failure(XmppError("Cannot authenticate before stream is ready."))
        }

        val tlsActive = tlsActiveProvider()
        val mechanisms = serverMechanismsProvider()

        return saslAuthenticationService.authenticate(
            jid = jid,
            password = password,
            tlsActive = tlsActive,
            serverMechanisms = mechanisms,
        ).flatMap { selectedMechanism ->
            securityPolicy.validateAuthMechanism(
                mechanism = selectedMechanism,
                tlsActive = tlsActive,
            )
        }.flatMap {
            authenticatedJid = jid
            XmppResult.Success(Unit)
        }
    }

    override suspend fun sendStanza(rawXml: String): XmppResult<Unit> {
        if (authenticatedJid == null) {
            return XmppResult.Failure(XmppError("Cannot send stanza before authentication."))
        }
        if (rawXml.isBlank()) {
            return XmppResult.Failure(XmppError("Stanza payload cannot be blank."))
        }

        return transport.write(rawXml)
    }

    override suspend fun disconnect(): XmppResult<Unit> =
        streamEngine.stop().flatMap {
            authenticatedJid = null
            XmppResult.Success(Unit)
        }
}
