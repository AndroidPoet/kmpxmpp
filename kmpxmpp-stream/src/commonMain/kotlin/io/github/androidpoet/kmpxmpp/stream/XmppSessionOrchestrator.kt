package io.github.androidpoet.kmpxmpp.stream

import io.github.androidpoet.kmpxmpp.core.XmppError
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.flatMap
import io.github.androidpoet.kmpxmpp.security.TlsMode
import io.github.androidpoet.kmpxmpp.security.validateAuthMechanism
import io.github.androidpoet.kmpxmpp.security.validateTlsState
import io.github.androidpoet.kmpxmpp.transport.XmppTransport
import io.github.androidpoet.kmpxmpp.xml.DefaultXmppStreamFeaturesParser
import io.github.androidpoet.kmpxmpp.xml.XmppStreamFeaturesParser

public class XmppSessionOrchestrator(
    private val config: XmppSessionConfig,
    private val transport: XmppTransport,
    private val featuresXmlProvider: () -> String,
    private val featuresParser: XmppStreamFeaturesParser = DefaultXmppStreamFeaturesParser(),
) : XmppStreamEngine {

    override var state: XmppStreamState = XmppStreamState.Disconnected
        private set

    override var sessionContext: XmppSessionContext? = null
        private set

    override suspend fun start(): XmppResult<Unit> {
        val tlsActiveAfterHandshake = config.tlsInitiallyActive || config.securityPolicy.tlsMode != TlsMode.Disabled

        return transport.connect(config.host, config.port)
            .flatMap {
                transitionTo(XmppStreamState.Connected)
                    .flatMap { transitionTo(XmppStreamState.StreamOpened) }
                    .flatMap { transitionTo(XmppStreamState.FeaturesReceived) }
            }
            .flatMap { featuresParser.parse(featuresXmlProvider()) }
            .flatMap { parsedFeatures ->
                sessionContext = XmppSessionContext(
                    tlsActive = tlsActiveAfterHandshake,
                    serverMechanisms = parsedFeatures.mechanisms,
                )

                config.securityPolicy.validateTlsState(tlsActiveAfterHandshake)
                    .flatMap { transitionTo(XmppStreamState.TlsUpgraded) }
                    .flatMap {
                        val selected = parsedFeatures.mechanisms.firstOrNull()
                            ?: return@flatMap XmppResult.Failure(
                                XmppError("No server SASL mechanism available for authentication."),
                            )
                        config.securityPolicy.validateAuthMechanism(selected, tlsActiveAfterHandshake)
                    }
                    .flatMap { transitionTo(XmppStreamState.Authenticated) }
                    .flatMap { transitionTo(XmppStreamState.Bound) }
                    .flatMap { transitionTo(XmppStreamState.Ready) }
            }
    }

    override suspend fun stop(): XmppResult<Unit> =
        transport.close().flatMap {
            sessionContext = null
            transitionTo(XmppStreamState.Disconnected)
        }

    private fun transitionTo(next: XmppStreamState): XmppResult<Unit> {
        if (!isTransitionAllowed(state, next)) {
            return XmppResult.Failure(
                XmppError("Invalid stream transition from $state to $next."),
            )
        }

        state = next
        return XmppResult.Success(Unit)
    }

    private fun isTransitionAllowed(current: XmppStreamState, next: XmppStreamState): Boolean =
        when (current) {
            XmppStreamState.Disconnected -> next == XmppStreamState.Connected
            XmppStreamState.Connected -> next == XmppStreamState.StreamOpened || next == XmppStreamState.Disconnected
            XmppStreamState.StreamOpened -> next == XmppStreamState.FeaturesReceived || next == XmppStreamState.Disconnected
            XmppStreamState.FeaturesReceived -> next == XmppStreamState.TlsUpgraded || next == XmppStreamState.Disconnected
            XmppStreamState.TlsUpgraded -> next == XmppStreamState.Authenticated || next == XmppStreamState.Disconnected
            XmppStreamState.Authenticated -> next == XmppStreamState.Bound || next == XmppStreamState.Disconnected
            XmppStreamState.Bound -> next == XmppStreamState.Ready || next == XmppStreamState.Disconnected
            XmppStreamState.Ready -> next == XmppStreamState.Disconnected
        }
}
