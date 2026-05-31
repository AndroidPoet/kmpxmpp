package io.github.androidpoet.kmpxmpp.xep0363.httpupload

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val UPLOAD_NAMESPACE: String = "urn:xmpp:http:upload:0"

public data class HttpUploadHeader(
    val name: String,
    val value: String,
)

public data class HttpUploadSlot(
    val putUrl: String,
    val getUrl: String,
    val headers: List<HttpUploadHeader>,
)

public interface XmppHttpUploadService {
    public suspend fun requestUploadSlot(
        uploadService: Jid,
        fileName: String,
        sizeBytes: Long,
        contentType: String?,
        requestId: String,
    ): XmppResult<Unit>

    public fun parseUploadSlotResult(xml: String): XmppResult<HttpUploadSlot>

    public fun validateUploadSlotRequest(xml: String): XmppResult<String>
}

public class DefaultXmppHttpUploadService(
    private val client: KmpXmppClient,
) : XmppHttpUploadService {

    override suspend fun requestUploadSlot(
        uploadService: Jid,
        fileName: String,
        sizeBytes: Long,
        contentType: String?,
        requestId: String,
    ): XmppResult<Unit> {
        if (uploadService.domain.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Upload service JID domain cannot be blank.", XmppErrorStage.Messaging, false))
        }
        if (fileName.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("File name cannot be blank.", XmppErrorStage.Messaging, true))
        }
        if (sizeBytes <= 0) {
            return XmppResult.Failure(xmppErrorInvalidInput("File size must be greater than zero.", XmppErrorStage.Messaging, true))
        }
        if (requestId.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Request id cannot be blank.", XmppErrorStage.Messaging, true))
        }
        if (contentType != null && contentType.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Content type cannot be blank when provided.", XmppErrorStage.Messaging, true))
        }

        val contentTypeNode = contentType?.let { "<content-type>${escapeXml(it)}</content-type>" } ?: ""
        val stanza = "<iq type='get' to='${escapeXml(uploadService.toString())}' id='${escapeXml(requestId)}'><request xmlns='$UPLOAD_NAMESPACE' filename='${escapeXml(fileName)}' size='$sizeBytes'>$contentTypeNode</request></iq>"
        return client.sendStanza(stanza)
    }

    override fun parseUploadSlotResult(xml: String): XmppResult<HttpUploadSlot> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Upload slot result XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        val iqTag = tags.firstOrNull { it.name == "iq" }
        if (!iqTag?.attributes?.get("type").equals("result", ignoreCase = true)) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Upload slot response must be IQ type='result'.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val slotTag = tags.firstOrNull { it.name == "slot" && it.attributes["xmlns"] == UPLOAD_NAMESPACE }
        val slotBody = slotTag?.let { XmppXmlMiniParser.tagInnerXml(xml, it) }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Upload slot response missing <slot xmlns='urn:xmpp:http:upload:0'>.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val slotTags = XmppXmlMiniParser.parseStartTags(slotBody)

        val putUrl = slotTags
            .firstOrNull { it.name == "put" }
            ?.attributes
            ?.get("url")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Upload slot response missing non-blank put URL.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )

        val getUrl = slotTags
            .firstOrNull { it.name == "get" }
            ?.attributes
            ?.get("url")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Upload slot response missing non-blank get URL.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )

        val headers = slotTags.filter { it.name == "header" }.mapNotNull { tag ->
            val name = (tag.attributes["name"] ?: "").trim()
            val value = (XmppXmlMiniParser.tagInnerXml(slotBody, tag) ?: "").trim()
            if (name.isBlank() || value.isBlank()) {
                null
            } else {
                HttpUploadHeader(name = name, value = value)
            }
        }.toList()

        return XmppResult.Success(HttpUploadSlot(putUrl = putUrl, getUrl = getUrl, headers = headers))
    }

    override fun validateUploadSlotRequest(xml: String): XmppResult<String> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Upload slot request XML cannot be blank.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        val iqTag = tags.firstOrNull { it.name == "iq" }
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Upload slot request must contain <iq/>.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val type = iqTag.attributes["type"]
        if (!type.equals("get", ignoreCase = true)) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Upload slot request IQ must be type='get'.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        val requestId = iqTag.attributes["id"]
            ?: return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Upload slot request IQ missing id attribute.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        val requestTag = tags.firstOrNull { it.name == "request" }
        if (requestTag?.attributes?.get("xmlns") != UPLOAD_NAMESPACE) {
            return XmppResult.Failure(
                xmppErrorParsing(
                    message = "Upload slot request missing urn:xmpp:http:upload:0 namespace.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            )
        }
        return XmppResult.Success(requestId)
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
