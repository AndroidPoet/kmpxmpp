package io.github.androidpoet.kmpxmpp.client

import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppError
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.XmppResultException
import io.github.androidpoet.kmpxmpp.core.flatMap
import io.github.androidpoet.kmpxmpp.core.getOrThrow
import io.github.androidpoet.kmpxmpp.core.xmppResultOfSuspend
import io.github.androidpoet.kmpxmpp.sasl.DefaultSaslAuthenticationService
import io.github.androidpoet.kmpxmpp.sasl.SaslAuthenticationService
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
) : KmpXmppClient {

    private var authenticatedJid: Jid? = null

    override suspend fun connect(): XmppResult<Unit> =
        xmppResultOfSuspend {
            streamEngine.start().getOrThrow()
        }

    override suspend fun authenticate(jid: Jid, password: String): XmppResult<Unit> =
        xmppResultOfSuspend {
            if (streamEngine.state != XmppStreamState.Ready) {
                throw XmppResultException("Cannot authenticate before stream is ready.")
            }

            val context = streamEngine.sessionContext
                ?: throw XmppResultException("Missing stream session context.")

            saslAuthenticationService.authenticate(
                jid = jid,
                password = password,
                tlsActive = context.tlsActive,
                serverMechanisms = context.serverMechanisms,
            ).flatMap { selectedMechanism ->
                securityPolicy.validateAuthMechanism(
                    mechanism = selectedMechanism,
                    tlsActive = context.tlsActive,
                )
            }.flatMap {
                authenticatedJid = jid
                XmppResult.Success(Unit)
            }.getOrThrow()
        }

    override suspend fun sendStanza(rawXml: String): XmppResult<Unit> =
        xmppResultOfSuspend {
            if (authenticatedJid == null) {
                throw XmppResultException("Cannot send stanza before authentication.")
            }
            if (rawXml.isBlank()) {
                throw XmppResultException("Stanza payload cannot be blank.")
            }

            transport.write(rawXml).getOrThrow()
        }

    override suspend fun disconnect(): XmppResult<Unit> =
        xmppResultOfSuspend {
            streamEngine.stop().flatMap {
                authenticatedJid = null
                XmppResult.Success(Unit)
            }.getOrThrow()
        }
}
