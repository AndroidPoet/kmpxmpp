package io.github.androidpoet.kmpxmpp.stream

import io.github.androidpoet.kmpxmpp.sasl.SaslMechanism

public data class XmppSessionContext(
    val tlsActive: Boolean,
    val serverMechanisms: Set<SaslMechanism>,
)
