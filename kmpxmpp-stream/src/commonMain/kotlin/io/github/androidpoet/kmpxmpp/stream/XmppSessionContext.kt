package io.github.androidpoet.kmpxmpp.stream

import io.github.androidpoet.kmpxmpp.sasl.SaslMechanism
import io.github.androidpoet.kmpxmpp.xml.XmppSaslProfile

public data class XmppSessionContext(
    val tlsActive: Boolean,
    val serverMechanisms: Set<SaslMechanism>,
    val saslProfile: XmppSaslProfile = XmppSaslProfile.Sasl1,
    val channelBindingTypes: Set<String> = emptySet(),
)
