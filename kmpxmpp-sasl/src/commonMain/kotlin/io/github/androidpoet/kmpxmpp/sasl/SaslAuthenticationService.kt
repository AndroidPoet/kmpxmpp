package io.github.androidpoet.kmpxmpp.sasl

import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppError
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult

public interface SaslAuthenticationService {
    public suspend fun authenticate(
        jid: Jid,
        password: String,
        tlsActive: Boolean,
        serverMechanisms: Set<SaslMechanism>,
    ): XmppResult<SaslMechanism>
}

public class DefaultSaslAuthenticationService : SaslAuthenticationService {
    override suspend fun authenticate(
        jid: Jid,
        password: String,
        tlsActive: Boolean,
        serverMechanisms: Set<SaslMechanism>,
    ): XmppResult<SaslMechanism> {
        if (jid.domain.isBlank()) {
            return XmppResult.Failure(
                XmppError(
                    message = "JID domain cannot be blank.",
                    code = XmppErrorCode.InvalidInput,
                    stage = XmppErrorStage.Authentication,
                    recoverable = false,
                ),
            )
        }
        if (password.isEmpty()) {
            return XmppResult.Failure(
                XmppError(
                    message = "Password cannot be empty.",
                    code = XmppErrorCode.InvalidInput,
                    stage = XmppErrorStage.Authentication,
                    recoverable = true,
                ),
            )
        }

        val supported = rankedClientMechanisms().filter { it in serverMechanisms }
        if (supported.isEmpty()) {
            return XmppResult.Failure(
                XmppError(
                    message = "No compatible SASL mechanism found with server.",
                    code = XmppErrorCode.AuthenticationFailed,
                    stage = XmppErrorStage.Authentication,
                    recoverable = true,
                ),
            )
        }

        val selected = supported.first()
        if (selected == SaslMechanism.Plain && !tlsActive) {
            return XmppResult.Failure(
                XmppError(
                    message = "SASL PLAIN requires TLS.",
                    code = XmppErrorCode.SecurityPolicyViolation,
                    stage = XmppErrorStage.Authentication,
                    recoverable = true,
                ),
            )
        }

        return XmppResult.Success(selected)
    }

    private fun rankedClientMechanisms(): List<SaslMechanism> = listOf(
        SaslMechanism.ScramSha256,
        SaslMechanism.ScramSha1,
        SaslMechanism.Plain,
    )
}
