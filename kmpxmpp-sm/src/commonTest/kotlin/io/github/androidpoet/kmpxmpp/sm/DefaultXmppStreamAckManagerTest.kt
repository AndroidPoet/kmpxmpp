package io.github.androidpoet.kmpxmpp.sm

import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultXmppStreamAckManagerTest {
    @Test
    fun test_recordOutbound_whenValid_enqueuesWithIncrementedSequence() {
        val manager = DefaultXmppStreamAckManager()

        val one = manager.recordOutbound("<message id='1'/>")
        val two = manager.recordOutbound("<message id='2'/>")

        assertIs<XmppResult.Success<PendingStanza>>(one)
        assertIs<XmppResult.Success<PendingStanza>>(two)
        assertEquals(1L, one.value.sequence)
        assertEquals(2L, two.value.sequence)
        assertEquals(2, manager.pending().size)
    }

    @Test
    fun test_acknowledgeTo_whenCountsAdvance_returnsAckedAndShrinksPending() {
        val manager = DefaultXmppStreamAckManager()
        manager.recordOutbound("<message id='1'/>")
        manager.recordOutbound("<message id='2'/>")
        manager.recordOutbound("<message id='3'/>")

        val ackResult = manager.acknowledgeTo(2)

        assertIs<XmppResult.Success<List<PendingStanza>>>(ackResult)
        assertEquals(2, ackResult.value.size)
        assertEquals(1, manager.pending().size)
        assertEquals(3L, manager.pending().first().sequence)
    }

    @Test
    fun test_acknowledgeTo_whenServerCountBackwards_returnsFailure() {
        val manager = DefaultXmppStreamAckManager()
        manager.recordOutbound("<message id='1'/>")
        manager.acknowledgeTo(1)

        val result = manager.acknowledgeTo(0)

        assertIs<XmppResult.Failure>(result)
        assertEquals("Server handled count moved backward.", result.error.message)
        assertEquals(XmppErrorCode.InvalidState, result.error.code)
        assertEquals(XmppErrorStage.StreamNegotiation, result.error.stage)
    }

    @Test
    fun test_acknowledgeTo_whenServerCountTooHigh_returnsFailure() {
        val manager = DefaultXmppStreamAckManager()
        manager.recordOutbound("<message id='1'/>")

        val result = manager.acknowledgeTo(2)

        assertIs<XmppResult.Failure>(result)
        assertEquals("Server handled count exceeds sent stanza count.", result.error.message)
        assertEquals(XmppErrorCode.InvalidState, result.error.code)
        assertEquals(XmppErrorStage.StreamNegotiation, result.error.stage)
    }
}
