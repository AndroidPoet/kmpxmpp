package io.github.androidpoet.kmpxmpp.stream

import io.github.androidpoet.kmpxmpp.sasl.SaslMechanism
import io.github.androidpoet.kmpxmpp.security.SecurityPolicy

public data class XmppSessionConfig(
    val host: String,
    val port: Int = 5222,
    val tlsInitiallyActive: Boolean = false,
    val mechanism: SaslMechanism = SaslMechanism.ScramSha256,
    val securityPolicy: SecurityPolicy = SecurityPolicy(),
)
