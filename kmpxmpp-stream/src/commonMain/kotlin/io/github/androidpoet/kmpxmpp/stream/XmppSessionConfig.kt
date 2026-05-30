package io.github.androidpoet.kmpxmpp.stream

import io.github.androidpoet.kmpxmpp.security.SecurityPolicy

public data class XmppSessionConfig(
    val host: String,
    val port: Int = 5222,
    val tlsInitiallyActive: Boolean = false,
    val securityPolicy: SecurityPolicy = SecurityPolicy(),
)
