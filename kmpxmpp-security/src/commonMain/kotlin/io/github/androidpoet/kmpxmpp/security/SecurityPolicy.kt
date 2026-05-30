package io.github.androidpoet.kmpxmpp.security

import io.github.androidpoet.kmpxmpp.core.XmppError
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.sasl.SaslMechanism

public enum class TlsMode {
    Required,
    Preferred,
    Disabled,
}

public data class SecurityPolicy(
    val tlsMode: TlsMode = TlsMode.Required,
    val allowPlainAuthWithoutTls: Boolean = false,
)

public fun SecurityPolicy.validateTlsState(tlsActive: Boolean): XmppResult<Unit> {
    if (tlsMode == TlsMode.Required && !tlsActive) {
        return XmppResult.Failure(
            XmppError(
                message = "TLS is required by policy but not active.",
                code = XmppErrorCode.SecurityPolicyViolation,
                stage = XmppErrorStage.Tls,
                recoverable = false,
            ),
        )
    }

    return XmppResult.Success(Unit)
}

public fun SecurityPolicy.validateAuthMechanism(
    mechanism: SaslMechanism,
    tlsActive: Boolean,
): XmppResult<Unit> {
    if (mechanism == SaslMechanism.Plain && !tlsActive && !allowPlainAuthWithoutTls) {
        return XmppResult.Failure(
            XmppError(
                message = "SASL PLAIN without TLS is disabled by policy.",
                code = XmppErrorCode.SecurityPolicyViolation,
                stage = XmppErrorStage.Authentication,
                recoverable = true,
            ),
        )
    }

    return XmppResult.Success(Unit)
}
