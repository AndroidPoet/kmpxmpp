package io.github.androidpoet.kmpxmpp.xep0249.directmucinvite

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.parseJidOrNull
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val CONFERENCE_NAMESPACE: String = "jabber:x:conference"

public data class ParsedDirectMucInvite(
    val inviter: Jid?,
    val invitee: Jid?,
    val room: Jid,
    val reason: String?,
    val password: String?,
    val thread: String?,
    val continueFlag: Boolean,
)

public interface XmppDirectMucInviteService {
    public suspend fun invite(to: Jid, room: Jid, reason: String? = null): XmppResult<Unit>

    public fun parseInvite(xml: String): XmppResult<ParsedDirectMucInvite>
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

    override fun parseInvite(xml: String): XmppResult<ParsedDirectMucInvite> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Direct MUC invite XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        val tags = XmppXmlMiniParser.parseStartTags(xml)
        val conferenceTag = tags.firstOrNull { it.name == "x" && it.attributes["xmlns"] == CONFERENCE_NAMESPACE }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Direct MUC invite stanza missing <x xmlns='jabber:x:conference'/> element.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val roomRaw = conferenceTag.attributes["jid"]
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Direct MUC invite conference element missing jid attribute.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val room = parseRoomJid(roomRaw)
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Direct MUC invite room jid is invalid.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )

        val messageTag = tags.firstOrNull { it.name == "message" }
        val invitee = messageTag?.attributes?.get("to")?.let { parseJidOrNull(it) }
        val inviter = messageTag?.attributes?.get("from")?.let { parseJidOrNull(it) }
        val reason = conferenceTag.attributes["reason"]?.takeIf { it.isNotBlank() }
        val password = conferenceTag.attributes["password"]?.takeIf { it.isNotBlank() }
        val thread = conferenceTag.attributes["thread"]?.takeIf { it.isNotBlank() }
        val continueFlag = conferenceTag.attributes["continue"]?.equals("true", ignoreCase = true) == true

        return XmppResult.Success(
            ParsedDirectMucInvite(
                inviter = inviter,
                invitee = invitee,
                room = room,
                reason = reason,
                password = password,
                thread = thread,
                continueFlag = continueFlag,
            ),
        )
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private fun parseRoomJid(value: String): Jid? {
        val parsed = parseJidOrNull(value) ?: return null
        val local = parsed.local ?: return null
        return Jid(local = local, domain = parsed.domain)
    }
}
