package io.github.androidpoet.kmpxmpp.core

public sealed interface KmpXmppResult<out T> {
    public data class Success<T>(val value: T) : KmpXmppResult<T>
    public data class Failure(val error: KmpXmppError) : KmpXmppResult<Nothing>
}

public data class KmpXmppError(
    val message: String,
    val cause: Throwable? = null,
)
