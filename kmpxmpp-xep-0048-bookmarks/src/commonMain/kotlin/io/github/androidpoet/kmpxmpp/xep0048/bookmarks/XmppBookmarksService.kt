package io.github.androidpoet.kmpxmpp.xep0048.bookmarks

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

private const val PRIVATE_NAMESPACE: String = "jabber:iq:private"
private const val STORAGE_NAMESPACE: String = "storage:bookmarks"

public data class XmppBookmark(
    val room: Jid,
    val name: String? = null,
    val autojoin: Boolean = false,
    val nick: String? = null,
)

public interface XmppBookmarksService {
    public suspend fun storeBookmarks(bookmarks: List<XmppBookmark>, requestId: String): XmppResult<Unit>
}

public class DefaultXmppBookmarksService(
    private val client: KmpXmppClient,
) : XmppBookmarksService {

    override suspend fun storeBookmarks(bookmarks: List<XmppBookmark>, requestId: String): XmppResult<Unit> {
        if (requestId.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Request id cannot be blank.", XmppErrorStage.Messaging, true))
        }
        for (bookmark in bookmarks) {
            if (bookmark.room.local.isNullOrBlank() || bookmark.room.domain.isBlank()) {
                return XmppResult.Failure(xmppErrorInvalidInput("Bookmark room JID must contain local and domain parts.", XmppErrorStage.Messaging, false))
            }
            if (bookmark.name != null && bookmark.name.isBlank()) {
                return XmppResult.Failure(xmppErrorInvalidInput("Bookmark name cannot be blank when provided.", XmppErrorStage.Messaging, true))
            }
        }

        val conferences = bookmarks.joinToString(separator = "") { bookmark ->
            val nameAttr = bookmark.name?.let { " name='${escapeXml(it)}'" } ?: ""
            val nickNode = bookmark.nick?.takeIf { it.isNotBlank() }?.let { "<nick>${escapeXml(it)}</nick>" } ?: ""
            "<conference jid='${escapeXml(bookmark.room.toString())}' autojoin='${if (bookmark.autojoin) "true" else "false"}'$nameAttr>$nickNode</conference>"
        }
        val stanza = "<iq type='set' id='${escapeXml(requestId)}'><query xmlns='$PRIVATE_NAMESPACE'><storage xmlns='$STORAGE_NAMESPACE'>$conferences</storage></query></iq>"
        return client.sendStanza(stanza)
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
