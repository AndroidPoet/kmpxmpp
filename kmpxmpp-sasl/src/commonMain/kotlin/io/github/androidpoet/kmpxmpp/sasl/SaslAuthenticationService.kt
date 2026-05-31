package io.github.androidpoet.kmpxmpp.sasl

import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorAuthentication
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorSecurityViolation

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
                xmppErrorInvalidInput(
                    message = "JID domain cannot be blank.",
                    stage = XmppErrorStage.Authentication,
                    recoverable = false,
                ),
            )
        }
        if (password.isEmpty()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Password cannot be empty.",
                    stage = XmppErrorStage.Authentication,
                    recoverable = true,
                ),
            )
        }

        val supported = rankedClientMechanisms().filter { it in serverMechanisms }
        if (supported.isEmpty()) {
            return XmppResult.Failure(
                xmppErrorAuthentication(
                    message = "No compatible SASL mechanism found with server.",
                    recoverable = true,
                ),
            )
        }

        val selected = supported.first()
        if (selected == SaslMechanism.Plain && !tlsActive) {
            return XmppResult.Failure(
                xmppErrorSecurityViolation(
                    message = "SASL PLAIN requires TLS.",
                    stage = XmppErrorStage.Authentication,
                    recoverable = true,
                ),
            )
        }

        return XmppResult.Success(selected)
    }

    private fun rankedClientMechanisms(): List<SaslMechanism> = listOf(
        SaslMechanism.ScramSha256Plus,
        SaslMechanism.ScramSha1Plus,
        SaslMechanism.ScramSha256,
        SaslMechanism.ScramSha1,
        SaslMechanism.Plain,
    )
}
