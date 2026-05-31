package io.github.androidpoet.kmpxmpp.xml

import io.github.androidpoet.kmpxmpp.sasl.SaslMechanism

public enum class XmppSaslProfile {
    Sasl1,
    Sasl2,
}

public data class XmppStreamFeatures(
    val mechanisms: Set<SaslMechanism>,
    val supportsStartTls: Boolean,
    val saslProfile: XmppSaslProfile,
    val channelBindingTypes: Set<String>,
)
