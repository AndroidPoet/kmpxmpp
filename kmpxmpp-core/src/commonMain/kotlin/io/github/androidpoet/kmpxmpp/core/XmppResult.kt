package io.github.androidpoet.kmpxmpp.core

public sealed interface XmppResult<out T> {
    public data class Success<T>(val value: T) : XmppResult<T>
    public data class Failure(val error: XmppError) : XmppResult<Nothing>
}

public enum class XmppErrorCode {
    Unknown,
    InvalidState,
    InvalidInput,
    SecurityPolicyViolation,
    AuthenticationFailed,
    TransportFailure,
    ParsingFailed,
}

public enum class XmppErrorStage {
    Unknown,
    Connect,
    StreamNegotiation,
    Tls,
    Authentication,
    Messaging,
    Disconnect,
}

public data class XmppError(
    val message: String,
    val code: XmppErrorCode = XmppErrorCode.Unknown,
    val stage: XmppErrorStage = XmppErrorStage.Unknown,
    val recoverable: Boolean = false,
    val cause: Throwable? = null,
)

public inline fun <T, R> XmppResult<T>.map(transform: (T) -> R): XmppResult<R> = when (this) {
    is XmppResult.Success -> XmppResult.Success(transform(value))
    is XmppResult.Failure -> this
}

public inline fun <T, R> XmppResult<T>.flatMap(transform: (T) -> XmppResult<R>): XmppResult<R> = when (this) {
    is XmppResult.Success -> transform(value)
    is XmppResult.Failure -> this
}

public inline fun <T> XmppResult<T>.recover(transform: (XmppError) -> T): XmppResult<T> = when (this) {
    is XmppResult.Success -> this
    is XmppResult.Failure -> XmppResult.Success(transform(error))
}

public inline fun <T, R> XmppResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (XmppError) -> R,
): R = when (this) {
    is XmppResult.Success -> onSuccess(value)
    is XmppResult.Failure -> onFailure(error)
}

public fun <T> XmppResult<T>.getOrNull(): T? = when (this) {
    is XmppResult.Success -> value
    is XmppResult.Failure -> null
}

public fun <T> XmppResult<T>.errorOrNull(): XmppError? = when (this) {
    is XmppResult.Success -> null
    is XmppResult.Failure -> error
}

public inline fun <T> XmppResult<T>.getOrElse(defaultValue: (XmppError) -> T): T = when (this) {
    is XmppResult.Success -> value
    is XmppResult.Failure -> defaultValue(error)
}

public class XmppResultException(
    override val message: String,
    override val cause: Throwable? = null,
) : IllegalStateException(message, cause)

public fun <T> XmppResult<T>.getOrThrow(): T = when (this) {
    is XmppResult.Success -> value
    is XmppResult.Failure -> throw XmppResultException(error.message, error.cause)
}

public inline fun <T> xmppResultOf(block: () -> T): XmppResult<T> =
    try {
        XmppResult.Success(block())
    } catch (throwable: Throwable) {
        XmppResult.Failure(
            XmppError(
                message = throwable.message ?: "Unexpected failure",
                code = XmppErrorCode.Unknown,
                stage = XmppErrorStage.Unknown,
                recoverable = false,
                cause = throwable,
            ),
        )
    }

public suspend inline fun <T> xmppResultOfSuspend(crossinline block: suspend () -> T): XmppResult<T> =
    try {
        XmppResult.Success(block())
    } catch (throwable: Throwable) {
        XmppResult.Failure(
            XmppError(
                message = throwable.message ?: "Unexpected failure",
                code = XmppErrorCode.Unknown,
                stage = XmppErrorStage.Unknown,
                recoverable = false,
                cause = throwable,
            ),
        )
    }
