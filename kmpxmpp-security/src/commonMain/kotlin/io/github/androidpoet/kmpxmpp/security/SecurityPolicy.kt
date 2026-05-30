package io.github.androidpoet.kmpxmpp.security

public enum class TlsMode {
    Required,
    Preferred,
    Disabled,
}

public data class SecurityPolicy(
    val tlsMode: TlsMode = TlsMode.Required,
    val allowPlainAuthWithoutTls: Boolean = false,
)
