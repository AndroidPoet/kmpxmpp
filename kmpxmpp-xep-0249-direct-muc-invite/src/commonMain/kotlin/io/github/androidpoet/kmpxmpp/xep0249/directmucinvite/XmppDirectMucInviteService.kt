package io.github.androidpoet.kmpxmpp.xep0249.directmucinvite

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

private const val CONFERENCE_NAMESPACE: String = "jabber:x:conference"

public interface XmppDirectMucInviteService {
    public suspend fun invite(to: Jid, room: Jid, reason: String? = null): XmppResult<Unit>
}

public class DefaultXmppDirectMucInviteService(
    private val client: KmpXmppClient,
) : XmppDirectMucInviteService {

    override suspend fun invite(to: Jid, room: Jid, reason: String?): XmppResult<Unit> {
        if (to.domain.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Invitee JID domain cannot be blank.", XmppErrorStage.Messaging, false))
        }
        if (room.local.isNullOrBlank() || room.domain.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Room JID must contain local and domain parts.", XmppErrorStage.Messaging, false))
        }
        if (reason != null && reason.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Invite reason cannot be blank when provided.", XmppErrorStage.Messaging, true))
        }

        val reasonAttr = reason?.let { " reason='${escapeXml(it)}'" } ?: ""
        val stanza = "<message to='${escapeXml(to.toString())}'><x xmlns='$CONFERENCE_NAMESPACE' jid='${escapeXml(room.toString())}'$reasonAttr/></message>"
        return client.sendStanza(stanza)
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
