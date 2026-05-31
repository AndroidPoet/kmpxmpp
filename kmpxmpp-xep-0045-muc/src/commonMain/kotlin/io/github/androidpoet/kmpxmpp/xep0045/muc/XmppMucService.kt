package io.github.androidpoet.kmpxmpp.xep0045.muc

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.parseJidOrNull
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val MUC_NAMESPACE: String = "http://jabber.org/protocol/muc"
private const val MUC_USER_NAMESPACE: String = "http://jabber.org/protocol/muc#user"

public data class MucOccupantPresence(
    val room: Jid,
    val nickname: String,
    val from: Jid?,
    val joined: Boolean,
    val unavailable: Boolean,
)

public data class MucGroupMessage(
    val room: Jid,
    val fromNickname: String?,
    val body: String?,
    val messageId: String?,
)

public interface XmppMucService {
    public suspend fun joinRoom(room: Jid, nickname: String): XmppResult<Unit>

    public suspend fun leaveRoom(room: Jid, nickname: String): XmppResult<Unit>

    public suspend fun sendGroupMessage(room: Jid, body: String): XmppResult<Unit>

    public suspend fun inviteUser(room: Jid, invitee: Jid, reason: String? = null): XmppResult<Unit>

    public fun parseOccupantPresence(xml: String): XmppResult<MucOccupantPresence>

    public fun parseGroupMessage(xml: String): XmppResult<MucGroupMessage>
}

public class DefaultXmppMucService(
    private val client: KmpXmppClient,
) : XmppMucService {

    override suspend fun joinRoom(room: Jid, nickname: String): XmppResult<Unit> {
        val inputError = validateRoomAndNickname(room, nickname)
        if (inputError != null) {
            return XmppResult.Failure(inputError)
        }

        val occupantJid = "${room.local}@${room.domain}/${escapeXml(nickname)}"
        val stanza = "<presence to='${escapeXml(occupantJid)}'><x xmlns='$MUC_NAMESPACE'/></presence>"
        return client.sendStanza(stanza)
    }

    override suspend fun leaveRoom(room: Jid, nickname: String): XmppResult<Unit> {
        val inputError = validateRoomAndNickname(room, nickname)
        if (inputError != null) {
            return XmppResult.Failure(inputError)
        }

        val occupantJid = "${room.local}@${room.domain}/${escapeXml(nickname)}"
        val stanza = "<presence to='${escapeXml(occupantJid)}' type='unavailable'/>"
        return client.sendStanza(stanza)
    }

    override suspend fun sendGroupMessage(room: Jid, body: String): XmppResult<Unit> {
        if (room.local.isNullOrBlank() || room.domain.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Room JID must contain local and domain parts.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = false,
                ),
            )
        }
        if (body.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Group message body cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        val stanza = "<message to='${escapeXml("${room.local}@${room.domain}")}' type='groupchat'><body>${escapeXml(body)}</body></message>"
        return client.sendStanza(stanza)
    }

    override suspend fun inviteUser(room: Jid, invitee: Jid, reason: String?): XmppResult<Unit> {
        if (room.local.isNullOrBlank() || room.domain.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Room JID must contain local and domain parts.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = false,
                ),
            )
        }
        if (invitee.domain.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Invitee JID domain cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = false,
                ),
            )
        }
        if (reason != null && reason.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Invite reason cannot be blank when provided.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        val roomJid = "${room.local}@${room.domain}"
        val reasonNode = reason?.let { "<reason>${escapeXml(it)}</reason>" } ?: ""
        val stanza =
            "<message to='${escapeXml(roomJid)}'><x xmlns='$MUC_USER_NAMESPACE'><invite to='${escapeXml(invitee.toString())}'>$reasonNode</invite></x></message>"
        return client.sendStanza(stanza)
    }

    override fun parseOccupantPresence(xml: String): XmppResult<MucOccupantPresence> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "MUC presence XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        val presenceTag = tags.firstOrNull { it.name == "presence" }
        if (presenceTag == null) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "MUC presence stanza must contain <presence/>.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        if (tags.none { it.attributes["xmlns"] == MUC_NAMESPACE || it.attributes["xmlns"] == MUC_USER_NAMESPACE }) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "MUC presence stanza missing MUC namespace.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }

        val fromAttr = presenceTag.attributes["from"]
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "MUC presence stanza missing from attribute.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val occupant = parseRoomOccupantJid(fromAttr)
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Invalid MUC occupant JID in presence from attribute.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val unavailable = presenceTag.attributes["type"]?.equals("unavailable", ignoreCase = true) == true
        return XmppResult.Success(
            MucOccupantPresence(
                room = occupant.first,
                nickname = occupant.second,
                from = parseJidOrNull(fromAttr),
                joined = !unavailable,
                unavailable = unavailable,
            ),
        )
    }

    override fun parseGroupMessage(xml: String): XmppResult<MucGroupMessage> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "MUC message XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        val messageTag = tags.firstOrNull { it.name == "message" }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "MUC group message must contain <message/>.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val type = messageTag.attributes["type"]
        if (!type.equals("groupchat", ignoreCase = true)) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "MUC group message must be type='groupchat'.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val from = messageTag.attributes["from"]
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "MUC group message missing from attribute.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val roomAndNick = parseRoomOccupantJid(from)
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Invalid MUC room occupant JID in message from attribute.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val body = XmppXmlMiniParser.textForTag(xml, "body")?.trim()?.ifBlank { null }
        val messageId = messageTag.attributes["id"]

        return XmppResult.Success(
            MucGroupMessage(
                room = roomAndNick.first,
                fromNickname = roomAndNick.second.ifBlank { null },
                body = body,
                messageId = messageId,
            ),
        )
    }

    private fun validateRoomAndNickname(room: Jid, nickname: String) =
        when {
            room.local.isNullOrBlank() || room.domain.isBlank() -> xmppErrorInvalidInput(
                message = "Room JID must contain local and domain parts.",
                stage = XmppErrorStage.Messaging,
                recoverable = false,
            )
            nickname.isBlank() -> xmppErrorInvalidInput(
                message = "Nickname cannot be blank.",
                stage = XmppErrorStage.Messaging,
                recoverable = true,
            )
            else -> null
        }

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun parseRoomOccupantJid(value: String): Pair<Jid, String>? {
        val parsed = parseJidOrNull(value) ?: return null
        val local = parsed.local ?: return null
        val nick = parsed.resource ?: return null
        return Jid(local = local, domain = parsed.domain) to nick
    }
}
