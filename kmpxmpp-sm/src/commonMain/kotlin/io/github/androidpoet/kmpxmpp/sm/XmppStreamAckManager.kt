package io.github.androidpoet.kmpxmpp.sm

import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidState

public data class PendingStanza(
    val sequence: Long,
    val stanza: String,
)

public interface XmppStreamAckManager {
    public fun recordOutbound(stanza: String): XmppResult<PendingStanza>

    public fun acknowledgeTo(serverHandledCount: Long): XmppResult<List<PendingStanza>>

    public fun pending(): List<PendingStanza>
}

public class DefaultXmppStreamAckManager : XmppStreamAckManager {
    private var sentCount: Long = 0
    private var ackedCount: Long = 0
    private val queue: ArrayDeque<PendingStanza> = ArrayDeque()

    override fun recordOutbound(stanza: String): XmppResult<PendingStanza> {
        if (stanza.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidState(
                    message = "Cannot record blank stanza in stream management.",
                    stage = XmppErrorStage.Messaging,
                    recoverable = false,
                ),
            )
        }

        sentCount += 1
        val pending = PendingStanza(sequence = sentCount, stanza = stanza)
        queue.addLast(pending)
        return XmppResult.Success(pending)
    }

    override fun acknowledgeTo(serverHandledCount: Long): XmppResult<List<PendingStanza>> {
        if (serverHandledCount < ackedCount) {
            return XmppResult.Failure(
                xmppErrorInvalidState(
                    message = "Server handled count moved backward.",
                    stage = XmppErrorStage.StreamNegotiation,
                    recoverable = false,
                ),
            )
        }
        if (serverHandledCount > sentCount) {
            return XmppResult.Failure(
                xmppErrorInvalidState(
                    message = "Server handled count exceeds sent stanza count.",
                    stage = XmppErrorStage.StreamNegotiation,
                    recoverable = true,
                ),
            )
        }

        val newlyAcked = mutableListOf<PendingStanza>()
        while (ackedCount < serverHandledCount) {
            val next = queue.removeFirstOrNull()
                ?: return XmppResult.Failure(
                    xmppErrorInvalidState(
                        message = "Pending queue underflow while applying acknowledgements.",
                        stage = XmppErrorStage.StreamNegotiation,
                        recoverable = false,
                    ),
                )
            newlyAcked += next
            ackedCount += 1
        }

        return XmppResult.Success(newlyAcked)
    }

    override fun pending(): List<PendingStanza> = queue.toList()
}
