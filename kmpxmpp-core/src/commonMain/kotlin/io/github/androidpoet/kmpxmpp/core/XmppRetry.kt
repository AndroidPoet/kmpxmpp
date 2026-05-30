package io.github.androidpoet.kmpxmpp.core

import kotlinx.coroutines.delay

public data class XmppRetryPolicy(
    val maxAttempts: Int = 1,
    val initialDelayMillis: Long = 0,
    val backoffMultiplier: Double = 1.0,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1." }
        require(initialDelayMillis >= 0) { "initialDelayMillis must be >= 0." }
        require(backoffMultiplier >= 1.0) { "backoffMultiplier must be >= 1.0." }
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
                    delay(delayMillis)
                }
                delayMillis = (delayMillis * policy.backoffMultiplier).toLong()
                attempt += 1
            }
        }
    }

    return lastFailure ?: XmppResult.Failure(
        xmppErrorUnknown("Retry ended without a result.", recoverable = false),
    )
}
