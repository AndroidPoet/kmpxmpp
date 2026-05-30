package io.github.androidpoet.kmpxmpp.stream

import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.flatMap
import io.github.androidpoet.kmpxmpp.core.getOrThrow
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidState
import io.github.androidpoet.kmpxmpp.core.xmppResultOfSuspend
import io.github.androidpoet.kmpxmpp.security.TlsMode
import io.github.androidpoet.kmpxmpp.security.validateAuthMechanism
import io.github.androidpoet.kmpxmpp.security.validateTlsState
import io.github.androidpoet.kmpxmpp.transport.XmppTransport
import io.github.androidpoet.kmpxmpp.xml.DefaultXmppStreamFeaturesParser
import io.github.androidpoet.kmpxmpp.xml.XmppStreamFeaturesParser

public class XmppSessionOrchestrator(
    private val config: XmppSessionConfig,
    private val transport: XmppTransport,
    private val featuresParser: XmppStreamFeaturesParser = DefaultXmppStreamFeaturesParser(),
) : XmppStreamEngine {
    private val streamFeaturesStartTag: String = "<stream:features"
    private val streamFeaturesEndTag: String = "</stream:features>"

    override var state: XmppStreamState = XmppStreamState.Disconnected
        private set

    override var sessionContext: XmppSessionContext? = null
        private set

    override suspend fun start(): XmppResult<Unit> =
        xmppResultOfSuspend(stage = XmppErrorStage.StreamNegotiation) {
            val tlsActiveAfterHandshake = config.tlsInitiallyActive || config.securityPolicy.tlsMode != TlsMode.Disabled

            transport.connect(config.host, config.port)
                .flatMap {
                    transitionTo(XmppStreamState.Connected).flatMap {
                        sendStreamOpen().flatMap { transitionTo(XmppStreamState.StreamOpened) }
                    }
                }
                .flatMap {
                    readStreamFeatures().flatMap { featuresXml ->
                        featuresParser.parse(featuresXml)
                    }.flatMap { parsed ->
                        transitionTo(XmppStreamState.FeaturesReceived).flatMap {
                            XmppResult.Success(parsed)
                        }
                    }
                }
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
                                    xmppErrorInvalidState(
                                        message = "No server SASL mechanism available for authentication.",
                                        stage = XmppErrorStage.Authentication,
                                        recoverable = false,
                                    ),
                                )
                            config.securityPolicy.validateAuthMechanism(selected, tlsActiveAfterHandshake)
                        }
                        .flatMap { transitionTo(XmppStreamState.Authenticated) }
                        .flatMap { transitionTo(XmppStreamState.Bound) }
                        .flatMap { transitionTo(XmppStreamState.Ready) }
                }
                .getOrThrow()
        }

    private suspend fun sendStreamOpen(): XmppResult<Unit> {
        val streamOpen = buildString {
            append("<?xml version='1.0'?>")
            append("<stream:stream to='")
            append(config.host)
            append("' xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' version='1.0'>\n")
        }
        return transport.write(streamOpen)
    }

    private suspend fun readStreamFeatures(): XmppResult<String> {
        val rawBuilder = StringBuilder()

        repeat(6) {
            when (val readResult = transport.read()) {
                is XmppResult.Success -> {
                    rawBuilder.append(readResult.value)
                    extractFeaturesXml(rawBuilder.toString())?.let { featuresXml ->
                        return XmppResult.Success(featuresXml)
                    }
                }
                is XmppResult.Failure -> return readResult
            }
        }

        return XmppResult.Failure(
            xmppErrorParsing(
                message = "Unable to extract <stream:features> from server response.",
                stage = XmppErrorStage.StreamNegotiation,
                recoverable = true,
            ),
        )
    }

    private fun extractFeaturesXml(raw: String): String? {
        val startIndex = raw.indexOf(streamFeaturesStartTag)
        if (startIndex == -1) {
            return null
        }

        val endIndex = raw.indexOf(streamFeaturesEndTag, startIndex)
        if (endIndex == -1) {
            return null
        }

        val endExclusive = endIndex + streamFeaturesEndTag.length
        return raw.substring(startIndex, endExclusive)
    }

    override suspend fun stop(): XmppResult<Unit> =
        xmppResultOfSuspend(stage = XmppErrorStage.Disconnect, recoverable = true) {
            transport.close().flatMap {
                sessionContext = null
                transitionTo(XmppStreamState.Disconnected)
            }.getOrThrow()
        }

    private fun transitionTo(next: XmppStreamState): XmppResult<Unit> {
        if (!isTransitionAllowed(state, next)) {
            return XmppResult.Failure(
                xmppErrorInvalidState(
                    message = "Invalid stream transition from $state to $next.",
                    stage = XmppErrorStage.StreamNegotiation,
                    recoverable = false,
                ),
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
