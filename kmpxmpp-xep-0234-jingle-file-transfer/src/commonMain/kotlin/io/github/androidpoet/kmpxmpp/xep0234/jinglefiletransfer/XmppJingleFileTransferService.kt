package io.github.androidpoet.kmpxmpp.xep0234.jinglefiletransfer

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput

private const val JINGLE_NAMESPACE: String = "urn:xmpp:jingle:1"
private const val JINGLE_FT_NAMESPACE: String = "urn:xmpp:jingle:apps:file-transfer:5"

public interface XmppJingleFileTransferService {
    public suspend fun initiateFileTransfer(
        to: Jid,
        sid: String,
        fileName: String,
        fileSize: Long,
        requestId: String,
    ): XmppResult<Unit>
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

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
