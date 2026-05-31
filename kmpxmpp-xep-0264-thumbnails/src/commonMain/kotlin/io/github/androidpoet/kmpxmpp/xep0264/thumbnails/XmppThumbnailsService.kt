package io.github.androidpoet.kmpxmpp.xep0264.thumbnails

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val THUMBNAILS_NAMESPACE: String = "urn:xmpp:thumbs:1"

public data class ParsedThumbnailReference(
    val mediaUri: String,
    val thumbnailUri: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
)

public interface XmppThumbnailsService {
    public suspend fun sendThumbnailReference(
        to: Jid,
        mediaCid: String,
        thumbnailCid: String,
        mimeType: String,
        width: Int,
        height: Int,
    ): XmppResult<Unit>

    public fun parseThumbnailReference(xml: String): XmppResult<ParsedThumbnailReference>

    public fun validateThumbnailReference(xml: String): XmppResult<Unit>
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

    override fun parseThumbnailReference(xml: String): XmppResult<ParsedThumbnailReference> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput("Thumbnail reference XML cannot be blank.", XmppErrorStage.Messaging, true),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        val referenceTag = tags.firstOrNull { it.name == "reference" && it.attributes["xmlns"] == "urn:xmpp:reference:0" }
            ?: return XmppResult.Failure(
                xmppErrorParsing("Thumbnail payload missing <reference xmlns='urn:xmpp:reference:0'>.", XmppErrorStage.Messaging, true),
            )
        val mediaUri = referenceTag.attributes["uri"]?.trim()?.takeIf { it.isNotBlank() }
            ?: return XmppResult.Failure(
                xmppErrorParsing("Thumbnail payload missing non-blank reference uri.", XmppErrorStage.Messaging, true),
            )
        val thumbnailTag = tags.firstOrNull { it.name == "thumbnail" && it.attributes["xmlns"] == THUMBNAILS_NAMESPACE }
            ?: return XmppResult.Failure(
                xmppErrorParsing("Thumbnail payload missing <thumbnail xmlns='urn:xmpp:thumbs:1'/>.", XmppErrorStage.Messaging, true),
            )
        val thumbnailUri = thumbnailTag.attributes["uri"]?.trim()?.takeIf { it.isNotBlank() }
            ?: return XmppResult.Failure(
                xmppErrorParsing("Thumbnail payload missing non-blank thumbnail uri.", XmppErrorStage.Messaging, true),
            )
        val mimeType = thumbnailTag.attributes["media-type"]?.trim()?.takeIf { it.isNotBlank() }
            ?: return XmppResult.Failure(
                xmppErrorParsing("Thumbnail payload missing non-blank media-type.", XmppErrorStage.Messaging, true),
            )
        val width = thumbnailTag.attributes["width"]?.toIntOrNull()
            ?: return XmppResult.Failure(
                xmppErrorParsing("Thumbnail payload missing numeric width.", XmppErrorStage.Messaging, true),
            )
        val height = thumbnailTag.attributes["height"]?.toIntOrNull()
            ?: return XmppResult.Failure(
                xmppErrorParsing("Thumbnail payload missing numeric height.", XmppErrorStage.Messaging, true),
            )
        if (width <= 0 || height <= 0) {
            return XmppResult.Failure(
                xmppErrorParsing("Thumbnail width and height must be greater than zero.", XmppErrorStage.Messaging, true),
            )
        }

        return XmppResult.Success(
            ParsedThumbnailReference(
                mediaUri = mediaUri,
                thumbnailUri = thumbnailUri,
                mimeType = mimeType,
                width = width,
                height = height,
            ),
        )
    }

    override fun validateThumbnailReference(xml: String): XmppResult<Unit> =
        when (val parsed = parseThumbnailReference(xml)) {
            is XmppResult.Success -> XmppResult.Success(Unit)
            is XmppResult.Failure -> parsed
        }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
