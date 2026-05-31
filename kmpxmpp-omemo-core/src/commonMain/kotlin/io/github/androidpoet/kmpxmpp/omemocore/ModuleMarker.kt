package io.github.androidpoet.kmpxmpp.omemocore

import kotlin.random.Random
import kotlinx.datetime.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

internal object ModuleMarker

public enum class OmemoIdentityTrust {
    Unknown,
    Trusted,
    Revoked,
}

public data class OmemoDeviceBundle(
    val userId: String,
    val deviceId: Int,
    val identityKeyBase64: String,
    val preKeyBase64: String,
    val signedPreKeyBase64: String,
)

public data class OmemoSessionRecord(
    val userId: String,
    val deviceId: Int,
    val rootKeyBase64: String,
    val chainKeyBase64: String,
    val trust: OmemoIdentityTrust,
)

public data class OmemoSessionLifecycleState(
    val userId: String,
    val deviceId: Int,
    val establishedAtEpochMillis: Long,
    val operationCount: Int,
    val nextSendMessageIndex: Int,
    val highestReceivedMessageIndex: Int?,
    val receivedMessageIndexesWindow: Set<Int> = emptySet(),
    val sendChainKeyBase64: String? = null,
    val receiveChainKeyBase64: String? = null,
)

public interface OmemoSessionRepository {
    public suspend fun saveBundle(bundle: OmemoDeviceBundle): Unit
    public suspend fun findBundle(userId: String, deviceId: Int): OmemoDeviceBundle?
    public suspend fun saveSession(session: OmemoSessionRecord): Unit
    public suspend fun findSession(userId: String, deviceId: Int): OmemoSessionRecord?
    public suspend fun deleteSession(userId: String, deviceId: Int): Unit
    public suspend fun setTrust(userId: String, deviceId: Int, trust: OmemoIdentityTrust): Unit
    public suspend fun saveLifecycleState(state: OmemoSessionLifecycleState): Unit
    public suspend fun findLifecycleState(userId: String, deviceId: Int): OmemoSessionLifecycleState?
    public suspend fun deleteLifecycleState(userId: String, deviceId: Int): Unit
}

public data class OmemoEncryptedPayload(
    val userId: String,
    val deviceId: Int,
    val ciphertextBase64: String,
)

public class OmemoLifecycleService(
    private val repository: OmemoSessionRepository,
    private val clockMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val maxSessionAgeMillis: Long = DEFAULT_MAX_SESSION_AGE_MILLIS,
    private val maxSessionOperations: Int = DEFAULT_MAX_SESSION_OPERATIONS,
) {
    private val replayMutex: Mutex = Mutex()
    private val recentlySeenPayloadTags: MutableMap<String, MutableSet<String>> = linkedMapOf()
    private val recentlyUsedEncryptNonces: MutableMap<String, MutableSet<String>> = linkedMapOf()

    public suspend fun establishSession(bundle: OmemoDeviceBundle): OmemoSessionRecord {
        require(bundle.userId.isNotBlank()) { "OMEMO userId cannot be blank." }
        require(bundle.deviceId > 0) { "OMEMO deviceId must be greater than zero." }
        require(bundle.identityKeyBase64.isNotBlank()) { "OMEMO identity key cannot be blank." }
        require(bundle.preKeyBase64.isNotBlank()) { "OMEMO preKey cannot be blank." }
        require(bundle.signedPreKeyBase64.isNotBlank()) { "OMEMO signedPreKey cannot be blank." }
        val identityKey = decodeKeyMaterial("identity key", bundle.identityKeyBase64)
        val preKey = decodeKeyMaterial("preKey", bundle.preKeyBase64)
        val signedPreKey = decodeKeyMaterial("signedPreKey", bundle.signedPreKeyBase64)
        require(preKey.contentEquals(signedPreKey).not()) {
            "OMEMO preKey and signedPreKey must differ."
        }
        require(identityKey.contentEquals(preKey).not()) {
            "OMEMO identity key and preKey must differ."
        }
        require(identityKey.contentEquals(signedPreKey).not()) {
            "OMEMO identity key and signedPreKey must differ."
        }
        repository.saveBundle(bundle)
        val record = OmemoSessionRecord(
            userId = bundle.userId,
            deviceId = bundle.deviceId,
            rootKeyBase64 = signedPreKey.toByteString().base64(),
            chainKeyBase64 = preKey.toByteString().base64(),
            trust = OmemoIdentityTrust.Unknown,
        )
        repository.saveSession(record)
        registerFreshSession(bundle.userId, bundle.deviceId)
        return record
    }

    public suspend fun encryptPayload(userId: String, deviceId: Int, plaintext: String): OmemoEncryptedPayload {
        require(userId.isNotBlank()) { "OMEMO userId cannot be blank." }
        require(deviceId > 0) { "OMEMO deviceId must be greater than zero." }
        require(plaintext.isNotEmpty()) { "OMEMO plaintext cannot be empty." }
        val session = repository.findSession(userId, deviceId)
            ?: throw IllegalStateException("Missing OMEMO session for $userId/$deviceId")
        enforceSessionPolicy(session.userId, session.deviceId)
        requireTrustedIdentity(session, userId, deviceId)
        val plaintextBytes = plaintext.encodeToByteArray()
        val lifecycle = findOrCreateLifecycleState(userId, deviceId)
        val sendChainKeyBase64 = lifecycle.sendChainKeyBase64 ?: session.chainKeyBase64
        val streamKey = decodeKeyMaterial("chain key", sendChainKeyBase64)
        val macKey = decodeKeyMaterial("root key", session.rootKeyBase64)
        check(streamKey.isNotEmpty()) { "OMEMO chain key is empty for $userId/$deviceId." }
        check(macKey.isNotEmpty()) { "OMEMO root key is empty for $userId/$deviceId." }
        val nonce = generateFreshEncryptNonce(userId, deviceId)
        val messageIndex = nextSendMessageIndex(userId, deviceId)
        val messageKeys = deriveMessageKeys(
            rootKey = macKey,
            chainKey = streamKey,
            nonce = nonce,
            messageIndex = messageIndex,
        )
        val transformed = xorWithDerivedKeystream(plaintextBytes, messageKeys.encryptionKey, nonce)
        val aad = buildAad(userId = userId, deviceId = deviceId, messageIndex = messageIndex)
        val tag = hmacSha256(messageKeys.authenticationKey, aad + nonce + transformed).copyOf(TAG_SIZE_BYTES)
        return OmemoEncryptedPayload(
            userId = userId,
            deviceId = deviceId,
            ciphertextBase64 = "$PAYLOAD_VERSION_PREFIX:${toHex(nonce)}:${toHex(transformed)}:${toHex(tag)}:$messageIndex",
        ).also {
            val nextSendChainKey = deriveNextChainKey(
                rootKey = macKey,
                currentChainKey = streamKey,
                direction = CHAIN_DIRECTION_RECV,
                nonce = nonce,
                tag = tag,
            ).toByteString().base64()
            val latestLifecycle = findOrCreateLifecycleState(userId, deviceId)
            repository.saveLifecycleState(latestLifecycle.copy(sendChainKeyBase64 = nextSendChainKey))
            markSessionOperation(session.userId, session.deviceId)
        }
    }

    public suspend fun decryptPayload(payload: OmemoEncryptedPayload): String {
        require(payload.userId.isNotBlank()) { "OMEMO payload userId cannot be blank." }
        require(payload.deviceId > 0) { "OMEMO payload deviceId must be greater than zero." }
        require(payload.ciphertextBase64.isNotBlank()) { "OMEMO payload ciphertext cannot be blank." }
        val session = repository.findSession(payload.userId, payload.deviceId)
            ?: throw IllegalStateException("Missing OMEMO session for ${payload.userId}/${payload.deviceId}")
        enforceSessionPolicy(session.userId, session.deviceId)
        requireTrustedIdentity(session, payload.userId, payload.deviceId)
        val lifecycle = findOrCreateLifecycleState(payload.userId, payload.deviceId)
        val receiveChainKeyBase64 = lifecycle.receiveChainKeyBase64 ?: session.chainKeyBase64
        val streamKey = decodeKeyMaterial("chain key", receiveChainKeyBase64)
        val macKey = decodeKeyMaterial("root key", session.rootKeyBase64)
        check(streamKey.isNotEmpty()) { "OMEMO chain key is empty for ${payload.userId}/${payload.deviceId}." }
        check(macKey.isNotEmpty()) { "OMEMO root key is empty for ${payload.userId}/${payload.deviceId}." }

        val tokenized = payload.ciphertextBase64.split(':')
        if (tokenized.size > 1 && (tokenized.size != 5 || tokenized[0] != PAYLOAD_VERSION_PREFIX)) {
            throw IllegalArgumentException("Unsupported OMEMO payload format.")
        }
        if (tokenized.size == 5 && tokenized[0] == PAYLOAD_VERSION_PREFIX) {
            val nonce = fromHex(tokenized[1])
            val ciphertext = fromHex(tokenized[2])
            val tag = fromHex(tokenized[3])
            val messageIndex = tokenized[4].toIntOrNull()
                ?: throw IllegalArgumentException("Invalid OMEMO message index.")
            require(messageIndex > 0) { "Invalid OMEMO message index." }
            require(nonce.size == NONCE_SIZE_BYTES) { "Invalid OMEMO nonce length." }
            require(tag.size == TAG_SIZE_BYTES) { "Invalid OMEMO tag length." }
            val messageKeys = deriveMessageKeys(
                rootKey = macKey,
                chainKey = streamKey,
                nonce = nonce,
                messageIndex = messageIndex,
            )
            val aad = buildAad(userId = payload.userId, deviceId = payload.deviceId, messageIndex = messageIndex)
            val expectedTag = hmacSha256(messageKeys.authenticationKey, aad + nonce + ciphertext).copyOf(TAG_SIZE_BYTES)
            check(constantTimeEquals(expectedTag, tag)) { "OMEMO payload integrity check failed." }
            check(validateAndRecordReceiveIndex(payload.userId, payload.deviceId, messageIndex)) {
                "OMEMO message index replay/reorder detected for ${payload.userId}/${payload.deviceId}."
            }
            val replayFingerprint = "${tokenized[1]}:${tokenized[2]}:${tokenized[3]}"
            check(markReplayAndValidateFresh(payload.userId, payload.deviceId, replayFingerprint)) {
                "OMEMO payload replay detected for ${payload.userId}/${payload.deviceId}."
            }
            val plaintext = xorWithDerivedKeystream(ciphertext, messageKeys.encryptionKey, nonce).decodeToString()
            val nextReceiveChainKey = deriveNextChainKey(
                rootKey = macKey,
                currentChainKey = streamKey,
                direction = CHAIN_DIRECTION_RECV,
                nonce = nonce,
                tag = tag,
            )
            val latestLifecycle = findOrCreateLifecycleState(payload.userId, payload.deviceId)
            repository.saveLifecycleState(
                latestLifecycle.copy(receiveChainKeyBase64 = nextReceiveChainKey.toByteString().base64()),
            )
            markSessionOperation(session.userId, session.deviceId)
            return plaintext
        }

        throw IllegalArgumentException("Unsupported OMEMO payload format; require v1 authenticated payloads.")
    }

    public suspend fun verifyIdentity(userId: String, deviceId: Int, trusted: Boolean): OmemoSessionRecord {
        require(userId.isNotBlank()) { "OMEMO userId cannot be blank." }
        require(deviceId > 0) { "OMEMO deviceId must be greater than zero." }
        val trust = if (trusted) OmemoIdentityTrust.Trusted else OmemoIdentityTrust.Revoked
        repository.setTrust(userId, deviceId, trust)
        return repository.findSession(userId, deviceId)
            ?: throw IllegalStateException("Missing OMEMO session for $userId/$deviceId")
    }

    public suspend fun rotateSession(userId: String, deviceId: Int, rotationNonce: String): OmemoSessionRecord {
        require(userId.isNotBlank()) { "OMEMO userId cannot be blank." }
        require(deviceId > 0) { "OMEMO deviceId must be greater than zero." }
        require(rotationNonce.isNotBlank()) { "OMEMO rotation nonce cannot be blank." }
        require(rotationNonce.length >= MIN_ROTATION_NONCE_LENGTH) { "OMEMO rotation nonce must be at least $MIN_ROTATION_NONCE_LENGTH characters." }
        val session = repository.findSession(userId, deviceId)
            ?: throw IllegalStateException("Missing OMEMO session for $userId/$deviceId")
        val nonceBytes = rotationNonce.encodeToByteArray()
        val rootKey = decodeKeyMaterial("root key", session.rootKeyBase64)
        val chainKey = decodeKeyMaterial("chain key", session.chainKeyBase64)
        val rotated = session.copy(
            rootKeyBase64 = deriveRotatedKey(
                currentKey = rootKey,
                context = "rotate-root",
                userId = userId,
                deviceId = deviceId,
                nonce = nonceBytes,
            ).toByteString().base64(),
            chainKeyBase64 = deriveRotatedKey(
                currentKey = chainKey,
                context = "rotate-chain",
                userId = userId,
                deviceId = deviceId,
                nonce = nonceBytes,
            ).toByteString().base64(),
        )
        repository.saveSession(rotated)
        registerFreshSession(userId, deviceId)
        return rotated
    }

    public suspend fun clearSession(userId: String, deviceId: Int) {
        require(userId.isNotBlank()) { "OMEMO userId cannot be blank." }
        require(deviceId > 0) { "OMEMO deviceId must be greater than zero." }
        repository.deleteSession(userId, deviceId)
        clearLifecycleState(userId, deviceId)
    }

    private fun xorBytes(bytes: ByteArray, key: ByteArray): ByteArray {
        if (key.isEmpty()) return bytes
        val out = ByteArray(bytes.size)
        for (index in bytes.indices) {
            out[index] = (bytes[index].toInt() xor key[index % key.size].toInt()).toByte()
        }
        return out
    }

    private fun xorWithDerivedKeystream(bytes: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        if (bytes.isEmpty()) return bytes
        val keystream = ByteArray(bytes.size)
        var offset = 0
        var counter = 0
        while (offset < bytes.size) {
            val block = hmacSha256(
                key = key,
                message = nonce + counter.toByteArray(),
            )
            val copyLen = minOf(block.size, bytes.size - offset)
            block.copyInto(keystream, destinationOffset = offset, startIndex = 0, endIndex = copyLen)
            offset += copyLen
            counter += 1
        }
        return xorBytes(bytes, keystream)
    }

    private fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray =
        message.toByteString().hmacSha256(key.toByteString()).toByteArray()

    private fun decodeKeyMaterial(label: String, value: String): ByteArray {
        val decoded = value.decodeBase64()?.toByteArray()
            ?: throw IllegalArgumentException("OMEMO $label must be valid base64.")
        require(decoded.size >= MIN_KEY_MATERIAL_BYTES) {
            "OMEMO $label material is too short."
        }
        return decoded
    }

    private fun deriveRotatedKey(
        currentKey: ByteArray,
        context: String,
        userId: String,
        deviceId: Int,
        nonce: ByteArray,
    ): ByteArray = hmacSha256(
        key = currentKey,
        message = context.encodeToByteArray() + "|".encodeToByteArray() +
            userId.encodeToByteArray() + "|".encodeToByteArray() +
            deviceId.toString().encodeToByteArray() + "|".encodeToByteArray() + nonce,
    )

    private fun deriveNextChainKey(
        rootKey: ByteArray,
        currentChainKey: ByteArray,
        direction: String,
        nonce: ByteArray,
        tag: ByteArray,
    ): ByteArray = hmacSha256(
        key = rootKey,
        message = "ratchet".encodeToByteArray() + "|".encodeToByteArray() +
            direction.encodeToByteArray() + "|".encodeToByteArray() +
            currentChainKey + "|" .encodeToByteArray() + nonce + "|" .encodeToByteArray() + tag,
    )

    private fun deriveMessageKeys(
        rootKey: ByteArray,
        chainKey: ByteArray,
        nonce: ByteArray,
        messageIndex: Int,
    ): MessageKeys {
        val contextBase = "msg".encodeToByteArray() +
            "|".encodeToByteArray() + messageIndex.toString().encodeToByteArray() +
            "|".encodeToByteArray() + nonce
        val encryptionKey = hmacSha256(chainKey, "enc|".encodeToByteArray() + contextBase)
        val authenticationKey = hmacSha256(
            rootKey,
            "auth|".encodeToByteArray() + contextBase + "|".encodeToByteArray() + chainKey,
        )
        return MessageKeys(encryptionKey = encryptionKey, authenticationKey = authenticationKey)
    }

    private fun Int.toByteArray(): ByteArray = byteArrayOf(
        ((this shr 24) and 0xFF).toByte(),
        ((this shr 16) and 0xFF).toByte(),
        ((this shr 8) and 0xFF).toByte(),
        (this and 0xFF).toByte(),
    )

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (index in a.indices) {
            diff = diff or (a[index].toInt() xor b[index].toInt())
        }
        return diff == 0
    }

    private fun buildAad(userId: String, deviceId: Int, messageIndex: Int): ByteArray =
        "$PAYLOAD_VERSION_PREFIX|$userId|$deviceId|$messageIndex".encodeToByteArray()

    private fun requireTrustedIdentity(session: OmemoSessionRecord, userId: String, deviceId: Int) {
        check(session.trust == OmemoIdentityTrust.Trusted) {
            when (session.trust) {
                OmemoIdentityTrust.Revoked -> "OMEMO identity is revoked for $userId/$deviceId."
                OmemoIdentityTrust.Unknown -> "OMEMO identity trust is unknown for $userId/$deviceId; verify identity before crypto operations."
                OmemoIdentityTrust.Trusted -> "OMEMO identity must be trusted for $userId/$deviceId."
            }
        }
    }

    private suspend fun markReplayAndValidateFresh(userId: String, deviceId: Int, tagHex: String): Boolean = replayMutex.withLock {
        val key = "$userId#$deviceId"
        while (recentlySeenPayloadTags.size >= MAX_REPLAY_SESSION_KEYS && key !in recentlySeenPayloadTags) {
            val oldestKey = recentlySeenPayloadTags.keys.firstOrNull() ?: break
            recentlySeenPayloadTags.remove(oldestKey)
        }
        val bucket = recentlySeenPayloadTags.getOrPut(key) { linkedSetOf() }
        if (tagHex in bucket) {
            return false
        }
        bucket += tagHex
        while (bucket.size > MAX_REPLAY_WINDOW) {
            val oldest = bucket.firstOrNull() ?: break
            bucket.remove(oldest)
        }
        true
    }

    private suspend fun generateFreshEncryptNonce(userId: String, deviceId: Int): ByteArray = replayMutex.withLock {
        val sessionKey = key(userId, deviceId)
        while (recentlyUsedEncryptNonces.size >= MAX_REPLAY_SESSION_KEYS && sessionKey !in recentlyUsedEncryptNonces) {
            val oldestKey = recentlyUsedEncryptNonces.keys.firstOrNull() ?: break
            recentlyUsedEncryptNonces.remove(oldestKey)
        }
        val bucket = recentlyUsedEncryptNonces.getOrPut(sessionKey) { linkedSetOf() }
        repeat(MAX_ENCRYPT_NONCE_ATTEMPTS) {
            val candidate = ByteArray(NONCE_SIZE_BYTES).also { Random.nextBytes(it) }
            val hex = toHex(candidate)
            if (hex !in bucket) {
                bucket += hex
                while (bucket.size > MAX_REPLAY_WINDOW) {
                    val oldest = bucket.firstOrNull() ?: break
                    bucket.remove(oldest)
                }
                return candidate
            }
        }
        throw IllegalStateException("Unable to generate unique OMEMO nonce for $userId/$deviceId.")
    }

    private suspend fun enforceSessionPolicy(userId: String, deviceId: Int) {
        val lifecycle = findOrCreateLifecycleState(userId, deviceId)
        val now = clockMillis()
        val age = now - lifecycle.establishedAtEpochMillis
        check(age <= maxSessionAgeMillis) {
            "OMEMO session expired for $userId/$deviceId; rotate session before continuing."
        }
        check(lifecycle.operationCount < maxSessionOperations) {
            "OMEMO session operation limit reached for $userId/$deviceId; rotate session before continuing."
        }
    }

    private suspend fun markSessionOperation(userId: String, deviceId: Int) {
        val lifecycle = findOrCreateLifecycleState(userId, deviceId)
        repository.saveLifecycleState(
            lifecycle.copy(operationCount = lifecycle.operationCount + 1),
        )
    }

    private suspend fun registerFreshSession(userId: String, deviceId: Int) {
        val sessionKey = key(userId, deviceId)
        val now = clockMillis()
        repository.saveLifecycleState(
            OmemoSessionLifecycleState(
                userId = userId,
                deviceId = deviceId,
                establishedAtEpochMillis = now,
                operationCount = 0,
                nextSendMessageIndex = 0,
                highestReceivedMessageIndex = null,
                receivedMessageIndexesWindow = emptySet(),
                sendChainKeyBase64 = null,
                receiveChainKeyBase64 = null,
            ),
        )
        replayMutex.withLock {
            recentlySeenPayloadTags.remove(sessionKey)
            recentlyUsedEncryptNonces.remove(sessionKey)
        }
    }

    private suspend fun clearLifecycleState(userId: String, deviceId: Int) {
        val sessionKey = key(userId, deviceId)
        repository.deleteLifecycleState(userId, deviceId)
        replayMutex.withLock {
            recentlySeenPayloadTags.remove(sessionKey)
            recentlyUsedEncryptNonces.remove(sessionKey)
        }
    }

    private suspend fun nextSendMessageIndex(userId: String, deviceId: Int): Int {
        val lifecycle = findOrCreateLifecycleState(userId, deviceId)
        val next = lifecycle.nextSendMessageIndex + 1
        repository.saveLifecycleState(lifecycle.copy(nextSendMessageIndex = next))
        return next
    }

    private suspend fun validateAndRecordReceiveIndex(userId: String, deviceId: Int, receivedIndex: Int): Boolean {
        val lifecycle = findOrCreateLifecycleState(userId, deviceId)
        val highest = lifecycle.highestReceivedMessageIndex
        val seenBucket = lifecycle.receivedMessageIndexesWindow.toMutableSet()

        if (receivedIndex in seenBucket) return false
        if (highest != null) {
            if (receivedIndex <= highest) return false
            if (receivedIndex > highest + MAX_FORWARD_INDEX_GAP) return false
            if (receivedIndex < (highest - RECEIVE_INDEX_WINDOW + 1)) return false
        }
        seenBucket += receivedIndex
        val nextHighest = maxOf(highest ?: receivedIndex, receivedIndex)
        val minAllowed = nextHighest - RECEIVE_INDEX_WINDOW + 1
        val trimmedSeen = seenBucket.filter { it >= minAllowed }.toSet()
        repository.saveLifecycleState(
            lifecycle.copy(
                highestReceivedMessageIndex = nextHighest,
                receivedMessageIndexesWindow = trimmedSeen,
            ),
        )
        return true
    }

    private suspend fun findOrCreateLifecycleState(userId: String, deviceId: Int): OmemoSessionLifecycleState {
        val existing = repository.findLifecycleState(userId, deviceId)
        if (existing != null) return existing
        val created = OmemoSessionLifecycleState(
            userId = userId,
            deviceId = deviceId,
            establishedAtEpochMillis = clockMillis(),
            operationCount = 0,
            nextSendMessageIndex = 0,
            highestReceivedMessageIndex = null,
            receivedMessageIndexesWindow = emptySet(),
            sendChainKeyBase64 = null,
            receiveChainKeyBase64 = null,
        )
        repository.saveLifecycleState(created)
        return created
    }

    private fun key(userId: String, deviceId: Int): String = "$userId#$deviceId"

    private fun toHex(bytes: ByteArray): String =
        bytes.joinToString(separator = "") { byte -> byte.toUByte().toString(16).padStart(2, '0') }

    private fun fromHex(value: String): ByteArray {
        require(value.length % 2 == 0) { "Invalid hex payload length." }
        val output = ByteArray(value.length / 2)
        var index = 0
        while (index < value.length) {
            val chunk = value.substring(index, index + 2)
            val byteValue = chunk.toIntOrNull(16) ?: throw IllegalArgumentException("Invalid hex payload content: '$chunk'.")
            output[index / 2] = byteValue.toByte()
            index += 2
        }
        return output
    }

    private companion object {
        const val PAYLOAD_VERSION_PREFIX: String = "v1"
        const val NONCE_SIZE_BYTES: Int = 12
        const val TAG_SIZE_BYTES: Int = 16
        const val MAX_REPLAY_WINDOW: Int = 512
        const val MAX_REPLAY_SESSION_KEYS: Int = 256
        const val DEFAULT_MAX_SESSION_AGE_MILLIS: Long = 24L * 60L * 60L * 1000L
        const val DEFAULT_MAX_SESSION_OPERATIONS: Int = 10_000
        const val MIN_ROTATION_NONCE_LENGTH: Int = 12
        const val MIN_KEY_MATERIAL_BYTES: Int = 8
        const val MAX_ENCRYPT_NONCE_ATTEMPTS: Int = 16
        const val RECEIVE_INDEX_WINDOW: Int = 256
        const val MAX_FORWARD_INDEX_GAP: Int = 64
        const val CHAIN_DIRECTION_RECV: String = "recv"
    }

    private data class MessageKeys(
        val encryptionKey: ByteArray,
        val authenticationKey: ByteArray,
    )

}
