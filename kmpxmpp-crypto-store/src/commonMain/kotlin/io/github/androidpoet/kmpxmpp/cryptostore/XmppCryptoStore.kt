package io.github.androidpoet.kmpxmpp.cryptostore

import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.xmppErrorInvalidInput
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

public data class CryptoMaterial(
    val alias: String,
    val bytes: ByteArray,
    val createdAt: Instant,
    val updatedAt: Instant,
)

public interface XmppCryptoStore {
    public suspend fun put(alias: String, bytes: ByteArray): XmppResult<Unit>
    public suspend fun get(alias: String): XmppResult<ByteArray?>
    public suspend fun contains(alias: String): XmppResult<Boolean>
    public suspend fun delete(alias: String): XmppResult<Boolean>
    public suspend fun clear(): XmppResult<Unit>
    public suspend fun listAliases(): XmppResult<Set<String>>
}

public class InMemoryXmppCryptoStore(
    private val clock: Clock = Clock.System,
) : XmppCryptoStore {
    private val mutex = Mutex()
    private val entries = linkedMapOf<String, CryptoMaterial>()

    override suspend fun put(alias: String, bytes: ByteArray): XmppResult<Unit> {
        val error = validate(alias, bytes)
        if (error != null) return XmppResult.Failure(error)
        return mutex.withLock {
            val now = clock.now()
            val old = entries[alias]
            old?.bytes?.fill(0)
            entries[alias] = CryptoMaterial(
                alias = alias,
                bytes = bytes.copyOf(),
                createdAt = old?.createdAt ?: now,
                updatedAt = now,
            )
            XmppResult.Success(Unit)
        }
    }

    override suspend fun get(alias: String): XmppResult<ByteArray?> {
        if (alias.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Crypto alias cannot be blank.",
                    stage = XmppErrorStage.Authentication,
                    recoverable = true,
                ),
            )
        }
        return mutex.withLock {
            XmppResult.Success(entries[alias]?.bytes?.copyOf())
        }
    }

    override suspend fun contains(alias: String): XmppResult<Boolean> {
        if (alias.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Crypto alias cannot be blank.",
                    stage = XmppErrorStage.Authentication,
                    recoverable = true,
                ),
            )
        }
        return mutex.withLock { XmppResult.Success(entries.containsKey(alias)) }
    }

    override suspend fun delete(alias: String): XmppResult<Boolean> {
        if (alias.isBlank()) {
            return XmppResult.Failure(
                xmppErrorInvalidInput(
                    message = "Crypto alias cannot be blank.",
                    stage = XmppErrorStage.Authentication,
                    recoverable = true,
                ),
            )
        }
        return mutex.withLock {
            val removed = entries.remove(alias)
            removed?.bytes?.fill(0)
            XmppResult.Success(removed != null)
        }
    }

    override suspend fun clear(): XmppResult<Unit> = mutex.withLock {
        entries.values.forEach { it.bytes.fill(0) }
        entries.clear()
        XmppResult.Success(Unit)
    }

    override suspend fun listAliases(): XmppResult<Set<String>> = mutex.withLock {
        XmppResult.Success(entries.keys.toSet())
    }

    private fun validate(alias: String, bytes: ByteArray): io.github.androidpoet.kmpxmpp.core.XmppError? {
        if (alias.isBlank()) {
            return xmppErrorInvalidInput(
                message = "Crypto alias cannot be blank.",
                stage = XmppErrorStage.Authentication,
                recoverable = true,
            )
        }
        if (bytes.isEmpty()) {
            return xmppErrorInvalidInput(
                message = "Crypto bytes cannot be empty.",
                stage = XmppErrorStage.Authentication,
                recoverable = true,
            )
        }
        return null
    }
}
