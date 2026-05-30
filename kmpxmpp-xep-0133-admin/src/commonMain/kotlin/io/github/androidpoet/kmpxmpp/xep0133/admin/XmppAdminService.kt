package io.github.androidpoet.kmpxmpp.xep0133.admin

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

private const val ADHOC_COMMANDS_NAMESPACE: String = "http://jabber.org/protocol/commands"

public interface XmppAdminService {
    public suspend fun requestRegisteredUsersCount(adminService: Jid, requestId: String): XmppResult<Unit>
}

public class DefaultXmppAdminService(
    private val client: KmpXmppClient,
) : XmppAdminService {

    override suspend fun requestRegisteredUsersCount(adminService: Jid, requestId: String): XmppResult<Unit> {
        if (adminService.domain.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Admin service JID domain cannot be blank.", XmppErrorStage.Messaging, false))
        }
        if (requestId.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Request id cannot be blank.", XmppErrorStage.Messaging, true))
        }

        val stanza = "<iq type='set' to='${escapeXml(adminService.toString())}' id='${escapeXml(requestId)}'><command xmlns='$ADHOC_COMMANDS_NAMESPACE' node='http://jabber.org/protocol/admin#get-registered-users-num' action='execute'/></iq>"
        return client.sendStanza(stanza)
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
