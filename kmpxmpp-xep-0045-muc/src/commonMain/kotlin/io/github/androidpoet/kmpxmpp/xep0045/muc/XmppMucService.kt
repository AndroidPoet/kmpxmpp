package io.github.androidpoet.kmpxmpp.xep0045.muc

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

public interface XmppMucService {
    public suspend fun joinRoom(room: Jid, nickname: String): XmppResult<Unit>

    public suspend fun leaveRoom(room: Jid, nickname: String): XmppResult<Unit>

    public suspend fun sendGroupMessage(room: Jid, body: String): XmppResult<Unit>
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
        val stanza = "<presence to='${escapeXml(occupantJid)}'><x xmlns='http://jabber.org/protocol/muc'/></presence>"
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
}
