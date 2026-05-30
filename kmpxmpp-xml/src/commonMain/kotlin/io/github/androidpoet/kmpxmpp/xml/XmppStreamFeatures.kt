package io.github.androidpoet.kmpxmpp.xml

import io.github.androidpoet.kmpxmpp.sasl.SaslMechanism

public data class XmppStreamFeatures(
    val mechanisms: Set<SaslMechanism>,
    val supportsStartTls: Boolean,
)
