package io.github.androidpoet.kmpxmpp.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class XmppRetryTest {
    @Test
    fun test_retry_whenFirstAttemptSucceeds_returnsImmediately() = runTest {
        var calls = 0

        val result = retryXmppResult(
            policy = XmppRetryPolicy(maxAttempts = 3),
        ) {
            calls += 1
            XmppResult.Success("ok")
        }

        assertIs<XmppResult.Success<String>>(result)
        assertEquals("ok", result.value)
        assertEquals(1, calls)
    }

    @Test
    fun test_retry_whenRecoverableThenSuccess_retriesAndReturnsSuccess() = runTest {
        var calls = 0

        val result = retryXmppResult(
            policy = XmppRetryPolicy(maxAttempts = 3),
        ) {
            calls += 1
            if (calls < 3) {
                XmppResult.Failure(
                    xmppErrorTransport("temp", stage = XmppErrorStage.Connect, recoverable = true),
                )
            } else {
                XmppResult.Success("ok")
            }
        }

        assertIs<XmppResult.Success<String>>(result)
        assertEquals("ok", result.value)
        assertEquals(3, calls)
    }

    @Test
    fun test_retry_whenNonRecoverableFailure_stopsWithoutExtraAttempts() = runTest {
        var calls = 0

        val result = retryXmppResult(
            policy = XmppRetryPolicy(maxAttempts = 5),
        ) {
            calls += 1
            XmppResult.Failure(
                xmppErrorInvalidState("fatal", stage = XmppErrorStage.Connect, recoverable = false),
            )
        }

        assertIs<XmppResult.Failure>(result)
        assertEquals("fatal", result.error.message)
        assertEquals(1, calls)
    }

    @Test
    fun test_retry_whenRecoverableAlwaysFails_returnsLastFailure() = runTest {
        var calls = 0

        val result = retryXmppResult(
            policy = XmppRetryPolicy(maxAttempts = 3),
        ) {
            calls += 1
            XmppResult.Failure(
                xmppErrorTransport("still-failing-$calls", stage = XmppErrorStage.Connect, recoverable = true),
            )
        }

        assertIs<XmppResult.Failure>(result)
        assertEquals("still-failing-3", result.error.message)
        assertEquals(3, calls)
    }
}
