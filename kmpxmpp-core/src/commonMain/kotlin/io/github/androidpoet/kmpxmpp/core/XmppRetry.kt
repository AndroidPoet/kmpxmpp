package io.github.androidpoet.kmpxmpp.core

import kotlinx.coroutines.delay
import kotlin.random.Random

public data class XmppRetryPolicy(
    val maxAttempts: Int = 1,
    val initialDelayMillis: Long = 0,
    val backoffMultiplier: Double = 1.0,
    val maxDelayMillis: Long = Long.MAX_VALUE,
    val jitterRatio: Double = 0.0,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1." }
        require(initialDelayMillis >= 0) { "initialDelayMillis must be >= 0." }
        require(backoffMultiplier >= 1.0) { "backoffMultiplier must be >= 1.0." }
        require(maxDelayMillis >= 0) { "maxDelayMillis must be >= 0." }
        require(jitterRatio in 0.0..1.0) { "jitterRatio must be between 0.0 and 1.0." }
    }
}

public suspend fun <T> retryXmppResult(
    policy: XmppRetryPolicy,
    shouldRetry: (XmppError) -> Boolean = { it.recoverable },
    operation: suspend () -> XmppResult<T>,
): XmppResult<T> {
    var attempt = 1
    var delayMillis = policy.initialDelayMillis
    var lastFailure: XmppResult.Failure? = null

    while (attempt <= policy.maxAttempts) {
        when (val result = operation()) {
            is XmppResult.Success -> return result
            is XmppResult.Failure -> {
                lastFailure = result
                val canRetry = attempt < policy.maxAttempts && shouldRetry(result.error)
                if (!canRetry) {
                    return result
                }
                if (delayMillis > 0) {
                    val jitteredDelay = applyJitter(delayMillis = delayMillis, jitterRatio = policy.jitterRatio)
                    delay(jitteredDelay)
                }
                val nextDelay = (delayMillis * policy.backoffMultiplier).toLong()
                delayMillis = nextDelay.coerceAtMost(policy.maxDelayMillis)
                attempt += 1
            }
        }
    }

    return lastFailure ?: XmppResult.Failure(
        xmppErrorUnknown("Retry ended without a result.", recoverable = false),
    )
}

private fun applyJitter(delayMillis: Long, jitterRatio: Double): Long {
    if (delayMillis <= 0L || jitterRatio <= 0.0) return delayMillis
    val jitterWindow = (delayMillis * jitterRatio).toLong().coerceAtLeast(1L)
    val lower = (delayMillis - jitterWindow).coerceAtLeast(0L)
    val upper = delayMillis + jitterWindow
    return Random.nextLong(from = lower, until = upper + 1)
}
