package io.github.androidpoet.kmpxmpp.xep0234.jinglefiletransfer

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppXmlMiniParser
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import io.github.androidpoet.kmpxmpp.core.xmppErrorParsing

private const val JINGLE_NAMESPACE: String = "urn:xmpp:jingle:1"
private const val JINGLE_FT_NAMESPACE: String = "urn:xmpp:jingle:apps:file-transfer:5"

public data class ParsedJingleFileTransfer(
    val sid: String,
    val fileName: String,
    val fileSize: Long,
)

public interface XmppJingleFileTransferService {
    public suspend fun initiateFileTransfer(
        to: Jid,
        sid: String,
        fileName: String,
        fileSize: Long,
        requestId: String,
    ): XmppResult<Unit>

    public fun parseFileTransferInitiation(xml: String): XmppResult<ParsedJingleFileTransfer>

    public fun validateFileTransferResponse(xml: String, requestId: String): XmppResult<Unit>
}

public class DefaultXmppJingleFileTransferService(
    private val client: KmpXmppClient,
) : XmppJingleFileTransferService {

    override suspend fun initiateFileTransfer(
        to: Jid,
        sid: String,
        fileName: String,
        fileSize: Long,
        requestId: String,
    ): XmppResult<Unit> {
        if (to.domain.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Target JID domain cannot be blank.", XmppErrorStage.Messaging, false))
        }
        if (sid.isBlank() || requestId.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("Session id and request id cannot be blank.", XmppErrorStage.Messaging, true))
        }
        if (fileName.isBlank()) {
            return XmppResult.Failure(xmppErrorInvalidInput("File name cannot be blank.", XmppErrorStage.Messaging, true))
        }
        if (fileSize <= 0) {
            return XmppResult.Failure(xmppErrorInvalidInput("File size must be greater than zero.", XmppErrorStage.Messaging, true))
        }

        val stanza = "<iq type='set' to='${escapeXml(to.toString())}' id='${escapeXml(requestId)}'><jingle xmlns='$JINGLE_NAMESPACE' action='session-initiate' sid='${escapeXml(sid)}'><content creator='initiator' name='file'><description xmlns='$JINGLE_FT_NAMESPACE'><file><name>${escapeXml(fileName)}</name><size>$fileSize</size></file></description></content></jingle></iq>"
        return client.sendStanza(stanza)
    }

    override fun parseFileTransferInitiation(xml: String): XmppResult<ParsedJingleFileTransfer> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput("Jingle file transfer XML cannot be blank.", XmppErrorStage.Messaging, true),
            )
        }
        val tags = XmppXmlMiniParser.parseStartTags(xml)
        val jingleTag = tags.firstOrNull { it.name == "jingle" && it.attributes["xmlns"] == JINGLE_NAMESPACE }
            ?: return XmppResult.Failure(
                xmppErrorParsing("Missing <jingle xmlns='urn:xmpp:jingle:1'>.", XmppErrorStage.Messaging, true),
            )
        val sid = jingleTag.attributes["sid"]?.trim()?.takeIf { it.isNotBlank() }
            ?: return XmppResult.Failure(
                xmppErrorParsing("Jingle stanza missing non-blank sid attribute.", XmppErrorStage.Messaging, true),
            )
        if (tags.none { it.attributes["xmlns"] == JINGLE_FT_NAMESPACE }) {
            return XmppResult.Failure(
                xmppErrorParsing("Jingle stanza missing file-transfer namespace.", XmppErrorStage.Messaging, true),
            )
        }
        val fileName = XmppXmlMiniParser.textForTag(xml, "name")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return XmppResult.Failure(
                xmppErrorParsing("Jingle file transfer missing non-blank file name.", XmppErrorStage.Messaging, true),
            )
        val fileSize = XmppXmlMiniParser.textForTag(xml, "size")
            ?.trim()
            ?.toLongOrNull()
            ?: return XmppResult.Failure(
                xmppErrorParsing("Jingle file transfer missing numeric file size.", XmppErrorStage.Messaging, true),
            )
        if (fileSize <= 0L) {
            return XmppResult.Failure(
                xmppErrorParsing("Jingle file size must be greater than zero.", XmppErrorStage.Messaging, true),
            )
        }
        return XmppResult.Success(ParsedJingleFileTransfer(sid = sid, fileName = fileName, fileSize = fileSize))
    }

    override fun validateFileTransferResponse(xml: String, requestId: String): XmppResult<Unit> {
        if (xml.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput("Jingle file transfer response XML cannot be blank.", XmppErrorStage.Messaging, true),
            )
        }
        val iqTag = XmppXmlMiniParser.parseStartTags(xml).firstOrNull { it.name == "iq" }
        if (!iqTag?.attributes?.get("type").equals("result", ignoreCase = true)) {
            return XmppResult.Failure(
                xmppErrorParsing("Jingle file transfer response must be IQ type='result'.", XmppErrorStage.Messaging, true),
            )
        }
        if (iqTag?.attributes?.get("id") != requestId) {
            return XmppResult.Failure(
                xmppErrorParsing("Jingle file transfer response id does not match request id.", XmppErrorStage.Messaging, true),
            )
        }
        return XmppResult.Success(Unit)
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
