package io.github.androidpoet.kmpxmpp.sasl

import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppError
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
            return XmppResult.Failure(XmppError("JID domain cannot be blank."))
        }
        if (password.isEmpty()) {
            return XmppResult.Failure(XmppError("Password cannot be empty."))
        }

        val supported = rankedClientMechanisms().filter { it in serverMechanisms }
        if (supported.isEmpty()) {
            return XmppResult.Failure(
                XmppError("No compatible SASL mechanism found with server."),
            )
        }

        val selected = supported.first()
        if (selected == SaslMechanism.Plain && !tlsActive) {
            return XmppResult.Failure(
                XmppError("SASL PLAIN requires TLS."),
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
