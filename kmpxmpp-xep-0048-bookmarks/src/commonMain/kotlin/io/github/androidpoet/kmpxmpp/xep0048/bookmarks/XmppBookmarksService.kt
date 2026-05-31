package io.github.androidpoet.kmpxmpp.xep0048.bookmarks

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.parseJidOrNull
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

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

    public fun parseStoredBookmarks(xml: String): XmppResult<List<XmppBookmark>>

    public fun validateStoreBookmarksRequest(xml: String): XmppResult<String>
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

    override fun parseStoredBookmarks(xml: String): XmppResult<List<XmppBookmark>> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Bookmarks XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        val queryTag = tags.firstOrNull { it.name == "query" }
        if (queryTag?.attributes?.get("xmlns") != PRIVATE_NAMESPACE) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Bookmarks payload missing jabber:iq:private namespace.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val storageTag = tags.firstOrNull { it.name == "storage" && it.attributes["xmlns"] == STORAGE_NAMESPACE }
        val storageBody = storageTag?.let { XmppXmlMiniParser.tagInnerXml(xml, it) }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Bookmarks payload missing <storage xmlns='storage:bookmarks'>.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )

        val bookmarks = XmppXmlMiniParser.parseStartTags(storageBody).mapNotNull { tag ->
            if (tag.name != "conference") return@mapNotNull null
            val jidValue = tag.attributes["jid"] ?: return@mapNotNull null
            val room = parseRoomJid(jidValue) ?: return@mapNotNull null
            val name = tag.attributes["name"]?.trim()?.ifBlank { null }
            val autojoin = tag.attributes["autojoin"]?.equals("true", ignoreCase = true) == true
            val conferenceBody = XmppXmlMiniParser.tagInnerXml(storageBody, tag) ?: ""
            val nick = XmppXmlMiniParser.textForTag(conferenceBody, "nick")?.trim()?.ifBlank { null }
            XmppBookmark(room = room, name = name, autojoin = autojoin, nick = nick)
        }

        return XmppResult.Success(bookmarks)
    }

    override fun validateStoreBookmarksRequest(xml: String): XmppResult<String> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Store bookmarks request XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        val iqTag = tags.firstOrNull { it.name == "iq" }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Store bookmarks request must contain <iq/>.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val type = iqTag.attributes["type"]
        if (!type.equals("set", ignoreCase = true)) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Store bookmarks request IQ must be type='set'.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val requestId = iqTag.attributes["id"]
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Store bookmarks request IQ missing id attribute.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val queryTag = tags.firstOrNull { it.name == "query" }
        if (queryTag?.attributes?.get("xmlns") != PRIVATE_NAMESPACE) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Store bookmarks request missing jabber:iq:private namespace.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val storageTag = tags.firstOrNull { it.name == "storage" }
        if (storageTag?.attributes?.get("xmlns") != STORAGE_NAMESPACE) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Store bookmarks request missing storage:bookmarks namespace.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        return XmppResult.Success(requestId)
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private fun parseRoomJid(value: String): Jid? {
        val parsed = parseJidOrNull(value) ?: return null
        if (parsed.local.isNullOrBlank()) {
            return null
        }
        return Jid(local = parsed.local, domain = parsed.domain)
    }
}
