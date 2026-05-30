package io.github.androidpoet.kmpxmpp.xep0363.httpupload

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

private const val UPLOAD_NAMESPACE: String = "urn:xmpp:http:upload:0"

public interface XmppHttpUploadService {
    public suspend fun requestUploadSlot(
        uploadService: Jid,
        fileName: String,
        sizeBytes: Long,
        contentType: String?,
        requestId: String,
    ): XmppResult<Unit>
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

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
