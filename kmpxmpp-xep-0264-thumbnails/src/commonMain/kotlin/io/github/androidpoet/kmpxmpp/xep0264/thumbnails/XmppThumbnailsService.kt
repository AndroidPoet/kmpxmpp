package io.github.androidpoet.kmpxmpp.xep0264.thumbnails

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

private const val THUMBNAILS_NAMESPACE: String = "urn:xmpp:thumbs:1"

public interface XmppThumbnailsService {
    public suspend fun sendThumbnailReference(
        to: Jid,
        mediaCid: String,
        thumbnailCid: String,
        mimeType: String,
        width: Int,
        height: Int,
    ): XmppResult<Unit>
}

public class DefaultXmppThumbnailsService(
    private val client: KmpXmppClient,
) : XmppThumbnailsService {

    override suspend fun sendThumbnailReference(
        to: Jid,
        mediaCid: String,
        thumbnailCid: String,
        mimeType: String,
        width: Int,
        height: Int,
    ): XmppResult<Unit> {
        if (to.domain.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Recipient JID domain cannot be blank.", XmppErrorStage.Messaging, false))
        }
        if (mediaCid.isBlank() || thumbnailCid.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Media and thumbnail content IDs cannot be blank.", XmppErrorStage.Messaging, true))
        }
        if (mimeType.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Thumbnail mime type cannot be blank.", XmppErrorStage.Messaging, true))
        }
        if (width <= 0 || height <= 0) {
            return XmppResult.Failure(xmppErrorInvalidInput("Thumbnail width and height must be greater than zero.", XmppErrorStage.Messaging, true))
        }

        val stanza = "<message to='${escapeXml(to.toString())}'><reference xmlns='urn:xmpp:reference:0' type='data' uri='${escapeXml(mediaCid)}'><thumbnail xmlns='$THUMBNAILS_NAMESPACE' uri='${escapeXml(thumbnailCid)}' media-type='${escapeXml(mimeType)}' width='$width' height='$height'/></reference></message>"
        return client.sendStanza(stanza)
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
