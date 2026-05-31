package io.github.androidpoet.kmpxmpp.omemocore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class OmemoLifecycleServiceTest {

    @Test
    fun test_establishSession_encryptDecrypt_verifyTrust_roundTrips() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)
        val bundle = OmemoDeviceBundle(
            userId = "alice",
            deviceId = 1001,
            identityKeyBase64 = km("identity-key-00"),
            preKeyBase64 = km("pre-key-00"),
            signedPreKeyBase64 = km("signed-pre-key-00"),
        )

        service.establishSession(bundle)
        service.verifyIdentity("alice", 1001, trusted = true)
        val encrypted = service.encryptPayload("alice", 1001, "hello-omemo")
        val decrypted = service.decryptPayload(encrypted)
        val trusted = service.verifyIdentity("alice", 1001, trusted = true)

        assertEquals("hello-omemo", decrypted)
        assertEquals(OmemoIdentityTrust.Trusted, trusted.trust)
        check(encrypted.ciphertextBase64.startsWith("v1:"))
    }

    @Test
    fun test_establishSession_whenIdentityKeyBlank_throws() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)

        assertFailsWith<IllegalArgumentException> {
            service.establishSession(
                OmemoDeviceBundle(
                    userId = "alice",
                    deviceId = 1001,
                    identityKeyBase64 = " ",
                    preKeyBase64 = km("pre-key-ok"),
                    signedPreKeyBase64 = km("signed-key-ok"),
                ),
            )
        }
    }

    @Test
    fun test_establishSession_whenKeyMaterialTooShort_throws() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)

        assertFailsWith<IllegalArgumentException> {
            service.establishSession(
                OmemoDeviceBundle(
                    userId = "alice",
                    deviceId = 1001,
                    identityKeyBase64 = km("short"),
                    preKeyBase64 = km("pre-key-ok"),
                    signedPreKeyBase64 = km("signed-ok"),
                ),
            )
        }
    }

    @Test
    fun test_establishSession_whenPreKeyEqualsSignedPreKey_throws() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)

        assertFailsWith<IllegalArgumentException> {
            service.establishSession(
                OmemoDeviceBundle(
                    userId = "alice",
                    deviceId = 1001,
                    identityKeyBase64 = km("identity-ok"),
                    preKeyBase64 = km("same-key-1"),
                    signedPreKeyBase64 = km("same-key-1"),
                ),
            )
        }
    }

    @Test
    fun test_encryptPayload_whenIdentityRevoked_throws() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = false)
        assertFailsWith<IllegalStateException> {
            service.encryptPayload("alice", 1001, "blocked")
        }
    }

    @Test
    fun test_encryptPayload_whenIdentityUnknown_throwsUntilVerified() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )

        assertFailsWith<IllegalStateException> {
            service.encryptPayload("alice", 1001, "blocked")
        }

        service.verifyIdentity("alice", 1001, trusted = true)
        val encrypted = service.encryptPayload("alice", 1001, "allowed")
        assertEquals("allowed", service.decryptPayload(encrypted))
    }

    @Test
    fun test_rotateAndClearSession_updatesKey_thenRemovesSession() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)

        val beforeRotate = repository.findSession("alice", 1001)!!
        val rotated = service.rotateSession("alice", 1001, "nonce-1234567")
        assertFailsWith<IllegalStateException> {
            service.decryptPayload(
                OmemoEncryptedPayload("alice", 4040, "00"),
            )
        }
        service.clearSession("alice", 1001)
        assertFailsWith<IllegalStateException> {
            service.encryptPayload("alice", 1001, "missing")
        }
        assertNotEquals(beforeRotate.chainKeyBase64, rotated.chainKeyBase64)
        assertNotEquals(beforeRotate.rootKeyBase64, rotated.rootKeyBase64)
        assertEquals(1001, rotated.deviceId)
    }

    @Test
    fun test_rotateSession_whenNonceTooShort_throws() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)

        assertFailsWith<IllegalArgumentException> {
            service.rotateSession("alice", 1001, "short")
        }
    }

    @Test
    fun test_decryptPayload_whenSessionRotated_rejectsPreRotationPayload() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)

        val payloadBeforeRotate = service.encryptPayload("alice", 1001, "before-rotate")
        service.rotateSession("alice", 1001, "rotation-xyz-1234")

        assertFailsWith<IllegalStateException> {
            service.decryptPayload(payloadBeforeRotate)
        }
    }

    @Test
    fun test_encryptPayload_whenSamePlaintextEncryptedTwice_generatesDifferentCiphertext() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)

        val first = service.encryptPayload("alice", 1001, "same message")
        val second = service.encryptPayload("alice", 1001, "same message")

        assertNotEquals(first.ciphertextBase64, second.ciphertextBase64)
        val firstNonce = first.ciphertextBase64.split(':')[1]
        val secondNonce = second.ciphertextBase64.split(':')[1]
        assertNotEquals(firstNonce, secondNonce)
    }

    @Test
    fun test_encryptPayload_whenCalledTwice_incrementsAuthenticatedMessageIndex() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)

        val first = service.encryptPayload("alice", 1001, "one")
        val second = service.encryptPayload("alice", 1001, "two")
        val firstIndex = first.ciphertextBase64.split(':').last().toInt()
        val secondIndex = second.ciphertextBase64.split(':').last().toInt()

        assertEquals(1, firstIndex)
        assertEquals(2, secondIndex)
    }

    @Test
    fun test_encryptPayload_whenServiceRecreated_keepsMessageIndexFromRepositoryState() = runTest {
        val repository = InMemoryRepository()
        val service1 = OmemoLifecycleService(repository)
        service1.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service1.verifyIdentity("alice", 1001, trusted = true)
        val first = service1.encryptPayload("alice", 1001, "one")
        assertEquals(1, first.ciphertextBase64.split(':').last().toInt())

        val service2 = OmemoLifecycleService(repository)
        val second = service2.encryptPayload("alice", 1001, "two")
        assertEquals(2, second.ciphertextBase64.split(':').last().toInt())
    }

    @Test
    fun test_decryptPayload_whenServiceRecreated_rejectsReplayUsingPersistedReceiveIndexState() = runTest {
        val repository = InMemoryRepository()
        val service1 = OmemoLifecycleService(repository)
        service1.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service1.verifyIdentity("alice", 1001, trusted = true)
        val payload = service1.encryptPayload("alice", 1001, "persisted-replay-check")
        assertEquals("persisted-replay-check", service1.decryptPayload(payload))

        val service2 = OmemoLifecycleService(repository)
        assertFailsWith<IllegalStateException> {
            service2.decryptPayload(payload)
        }
    }

    @Test
    fun test_decryptPayload_whenServiceRecreated_rejectsOlderOutOfOrderPayload() = runTest {
        val repository = InMemoryRepository()
        val service1 = OmemoLifecycleService(repository)
        service1.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service1.verifyIdentity("alice", 1001, trusted = true)
        val first = service1.encryptPayload("alice", 1001, "m1")
        val second = service1.encryptPayload("alice", 1001, "m2")
        assertEquals("m1", service1.decryptPayload(first))
        assertEquals("m2", service1.decryptPayload(second))

        val service2 = OmemoLifecycleService(repository)
        assertFailsWith<IllegalStateException> {
            service2.decryptPayload(first)
        }
    }

    @Test
    fun test_decryptPayload_whenCalled_advancesReceiveLifecycleChainKey() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)
        val payload = service.encryptPayload("alice", 1001, "ratchet-recv")
        val before = repository.findLifecycleState("alice", 1001)?.receiveChainKeyBase64

        assertEquals("ratchet-recv", service.decryptPayload(payload))

        val after = repository.findLifecycleState("alice", 1001)?.receiveChainKeyBase64
        assertNotEquals(before, after)
    }

    @Test
    fun test_encryptPayload_whenServiceRecreated_persistsAndAdvancesSendChainLifecycleKey() = runTest {
        val repository = InMemoryRepository()
        val service1 = OmemoLifecycleService(repository)
        service1.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service1.verifyIdentity("alice", 1001, trusted = true)
        service1.encryptPayload("alice", 1001, "one")
        val sendAfterFirst = repository.findLifecycleState("alice", 1001)?.sendChainKeyBase64
        assertTrue(sendAfterFirst != null && sendAfterFirst.isNotBlank())

        val service2 = OmemoLifecycleService(repository)
        val second = service2.encryptPayload("alice", 1001, "two")
        assertEquals(2, second.ciphertextBase64.split(':').last().toInt())
        val sendAfterSecond = repository.findLifecycleState("alice", 1001)?.sendChainKeyBase64
        assertNotEquals(sendAfterFirst, sendAfterSecond)
    }

    @Test
    fun test_decryptPayload_whenServiceRecreated_persistsAndAdvancesReceiveChainLifecycleKey() = runTest {
        val repository = InMemoryRepository()
        val service1 = OmemoLifecycleService(repository)
        service1.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service1.verifyIdentity("alice", 1001, trusted = true)

        val first = service1.encryptPayload("alice", 1001, "m1")
        val second = service1.encryptPayload("alice", 1001, "m2")
        assertEquals("m1", service1.decryptPayload(first))
        val receiveAfterFirst = repository.findLifecycleState("alice", 1001)?.receiveChainKeyBase64
        assertTrue(receiveAfterFirst != null && receiveAfterFirst.isNotBlank())

        val service2 = OmemoLifecycleService(repository)
        assertEquals("m2", service2.decryptPayload(second))
        val receiveAfterSecond = repository.findLifecycleState("alice", 1001)?.receiveChainKeyBase64
        assertNotEquals(receiveAfterFirst, receiveAfterSecond)
    }

    @Test
    fun test_rotateSession_whenCalled_resetsDirectionalLifecycleChains() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)
        val outbound = service.encryptPayload("alice", 1001, "m1")
        assertEquals("m1", service.decryptPayload(outbound))
        val beforeRotate = repository.findLifecycleState("alice", 1001)!!
        assertTrue(beforeRotate.sendChainKeyBase64 != null && beforeRotate.sendChainKeyBase64.isNotBlank())
        assertTrue(beforeRotate.receiveChainKeyBase64 != null && beforeRotate.receiveChainKeyBase64.isNotBlank())

        service.rotateSession("alice", 1001, "rotation-abc-1234")
        val afterRotate = repository.findLifecycleState("alice", 1001)!!
        assertEquals(null, afterRotate.sendChainKeyBase64)
        assertEquals(null, afterRotate.receiveChainKeyBase64)
    }

    @Test
    fun test_encryptThenDecrypt_whenMultipleMessages_sentAndReceivedChainsProgressIndependently() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)

        val first = service.encryptPayload("alice", 1001, "m1")
        val second = service.encryptPayload("alice", 1001, "m2")

        assertEquals("m1", service.decryptPayload(first))
        assertEquals("m2", service.decryptPayload(second))
    }

    @Test
    fun test_decryptPayload_whenCiphertextTampered_throwsIntegrityFailure() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)

        val payload = service.encryptPayload("alice", 1001, "secure message")
        val parts = payload.ciphertextBase64.split(':').toMutableList()
        parts[2] = if (parts[2].startsWith("00")) "11" + parts[2].drop(2) else "00" + parts[2].drop(2)
        val tampered = payload.copy(ciphertextBase64 = parts.joinToString(":"))

        assertFailsWith<IllegalStateException> {
            service.decryptPayload(tampered)
        }
    }

    @Test
    fun test_decryptPayload_whenPayloadReboundToDifferentIdentity_throwsIntegrityFailure() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.establishSession(
            OmemoDeviceBundle("bob", 2002, km("identity01"), km("pre-key-01"), km("signed-key-01")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)
        service.verifyIdentity("bob", 2002, trusted = true)

        val payload = service.encryptPayload("alice", 1001, "identity-bound")
        val rebound = payload.copy(userId = "bob", deviceId = 2002)

        assertFailsWith<IllegalStateException> {
            service.decryptPayload(rebound)
        }
    }

    @Test
    fun test_decryptPayload_whenSamePayloadUsedTwice_detectsReplay() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)

        val payload = service.encryptPayload("alice", 1001, "replay-check")
        val first = service.decryptPayload(payload)
        assertEquals("replay-check", first)

        assertFailsWith<IllegalStateException> {
            service.decryptPayload(payload)
        }
    }

    @Test
    fun test_decryptPayload_whenMessageIndexTampered_throwsIntegrityFailure() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)

        val payload = service.encryptPayload("alice", 1001, "first")
        val parts = payload.ciphertextBase64.split(':').toMutableList()
        parts[4] = "0"
        val tampered = payload.copy(ciphertextBase64 = parts.joinToString(":"))

        assertFailsWith<IllegalArgumentException> {
            service.decryptPayload(tampered)
        }
    }

    @Test
    fun test_decryptPayload_whenMessageIndexZero_throwsInvalidIndex() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)

        val payload = service.encryptPayload("alice", 1001, "first")
        val parts = payload.ciphertextBase64.split(':').toMutableList()
        parts[4] = "0"
        val tampered = payload.copy(ciphertextBase64 = parts.joinToString(":"))

        assertFailsWith<IllegalArgumentException> {
            service.decryptPayload(tampered)
        }
    }

    @Test
    fun test_decryptPayload_whenUsingStaleChainState_failsIntegrity() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)

        val payload = service.encryptPayload("alice", 1001, "stale-chain")
        val session = repository.findSession("alice", 1001)!!
        repository.saveSession(session.copy(chainKeyBase64 = km("different-chain-00")))

        assertFailsWith<IllegalStateException> {
            service.decryptPayload(payload)
        }
    }

    @Test
    fun test_decryptPayload_whenOutOfOrder_rejectsOlderPayload() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)

        val first = service.encryptPayload("alice", 1001, "m1")
        val second = service.encryptPayload("alice", 1001, "m2")

        assertFailsWith<IllegalStateException> {
            service.decryptPayload(second)
        }
        assertEquals("m1", service.decryptPayload(first))
        assertEquals("m2", service.decryptPayload(second))
        assertFailsWith<IllegalStateException> {
            service.decryptPayload(first)
        }
    }

    @Test
    fun test_decryptPayload_whenIndexTooFarInFuture_rejects() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)

        val payload = service.encryptPayload("alice", 1001, "future-gap")
        val parts = payload.ciphertextBase64.split(':').toMutableList()
        parts[4] = "99999"
        val tampered = payload.copy(ciphertextBase64 = parts.joinToString(":"))

        assertFailsWith<IllegalStateException> {
            service.decryptPayload(tampered)
        }
    }

    @Test
    fun test_decryptPayload_whenIndexTooOldForReceiveWindow_rejects() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)

        val early = service.encryptPayload("alice", 1001, "early")
        var last = early
        repeat(260) { index ->
            last = service.encryptPayload("alice", 1001, "m$index")
        }
        assertEquals("early", service.decryptPayload(early))
        assertFailsWith<IllegalStateException> {
            service.decryptPayload(last)
        }

        assertFailsWith<IllegalStateException> {
            service.decryptPayload(early)
        }
    }

    @Test
    fun test_decryptPayload_whenTamperedPayloadAttempted_doesNotPoisonReplayForValidPayload() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)

        val original = service.encryptPayload("alice", 1001, "valid-after-tamper")
        val parts = original.ciphertextBase64.split(':').toMutableList()
        parts[2] = if (parts[2].startsWith("00")) "11" + parts[2].drop(2) else "00" + parts[2].drop(2)
        val tampered = original.copy(ciphertextBase64 = parts.joinToString(":"))
        assertFailsWith<IllegalStateException> {
            service.decryptPayload(tampered)
        }

        val decrypted = service.decryptPayload(original)
        assertEquals("valid-after-tamper", decrypted)
    }

    @Test
    fun test_replayCache_whenSessionKeyCapacityExceeded_evictsOldestSessionBucket() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)

        for (index in 0..256) {
            val user = "u$index"
            val device = 1000 + index
            service.establishSession(
                OmemoDeviceBundle(
                    userId = user,
                    deviceId = device,
                    identityKeyBase64 = km("identity-$index"),
                    preKeyBase64 = km("pre-key-$index"),
                    signedPreKeyBase64 = km("signed-key-$index"),
                ),
            )
            service.verifyIdentity(user, device, trusted = true)
            val payload = service.encryptPayload(user, device, "m$index")
            assertEquals("m$index", service.decryptPayload(payload))
        }

        val firstUser = "u0"
        val firstDevice = 1000
        val payload = service.encryptPayload(firstUser, firstDevice, "reloaded")
        assertEquals("reloaded", service.decryptPayload(payload))
    }

    @Test
    fun test_encryptPayload_whenSessionOperationLimitReached_requiresRotation() = runTest {
        val repository = InMemoryRepository()
        var now = 1_000L
        val service = OmemoLifecycleService(
            repository = repository,
            clockMillis = { now },
            maxSessionAgeMillis = 60_000L,
            maxSessionOperations = 2,
        )
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)

        service.encryptPayload("alice", 1001, "one")
        service.encryptPayload("alice", 1001, "two")
        assertFailsWith<IllegalStateException> {
            service.encryptPayload("alice", 1001, "three")
        }

        service.rotateSession("alice", 1001, "rotation-1234")
        val postRotate = service.encryptPayload("alice", 1001, "four")
        val plaintext = service.decryptPayload(postRotate)
        assertEquals("four", plaintext)
    }

    @Test
    fun test_encryptPayload_whenSessionExpired_requiresRotation() = runTest {
        val repository = InMemoryRepository()
        var now = 10_000L
        val service = OmemoLifecycleService(
            repository = repository,
            clockMillis = { now },
            maxSessionAgeMillis = 1_000L,
            maxSessionOperations = 100,
        )
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)
        service.encryptPayload("alice", 1001, "before-expiry")

        now += 1_500L
        assertFailsWith<IllegalStateException> {
            service.encryptPayload("alice", 1001, "after-expiry")
        }

        service.rotateSession("alice", 1001, "rotation-5678")
        val encrypted = service.encryptPayload("alice", 1001, "after-rotate")
        assertEquals("after-rotate", service.decryptPayload(encrypted))
    }

    @Test
    fun test_clearSession_whenCalled_clearsSessionLifecycleAndReplayState() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)
        val firstPayload = service.encryptPayload("alice", 1001, "first")
        assertEquals("first", service.decryptPayload(firstPayload))
        assertFailsWith<IllegalStateException> {
            service.decryptPayload(firstPayload)
        }

        service.clearSession("alice", 1001)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity02"), km("pre-key-02"), km("signed-key-02")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)
        val secondPayload = service.encryptPayload("alice", 1001, "second")
        assertEquals("second", service.decryptPayload(secondPayload))
    }

    @Test
    fun test_decryptPayload_whenLegacyPayloadAndDefaultConfig_throws() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)
        val legacyCipherHex = xorToHex("legacy", "pre-key")

        assertFailsWith<IllegalArgumentException> {
            service.decryptPayload(
                OmemoEncryptedPayload("alice", 1001, legacyCipherHex),
            )
        }
    }

    @Test
    fun test_decryptPayload_whenLegacyPayloadCompatRequested_stillThrows() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository = repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)
        val legacyCipherHex = xorToHex("legacy-ok", "pre-key-00")

        assertFailsWith<IllegalArgumentException> {
            service.decryptPayload(
                OmemoEncryptedPayload("alice", 1001, legacyCipherHex),
            )
        }
    }

    @Test
    fun test_decryptPayload_whenColonizedNonV1Payload_throwsUnsupportedFormat() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository = repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)

        assertFailsWith<IllegalArgumentException> {
            service.decryptPayload(
                OmemoEncryptedPayload("alice", 1001, "v0:0011:2233:4455"),
            )
        }
    }

    @Test
    fun test_decryptPayload_whenV1PayloadMissingMessageIndex_throwsUnsupportedFormat() = runTest {
        val repository = InMemoryRepository()
        val service = OmemoLifecycleService(repository = repository)
        service.establishSession(
            OmemoDeviceBundle("alice", 1001, km("identity00"), km("pre-key-00"), km("signed-key-00")),
        )
        service.verifyIdentity("alice", 1001, trusted = true)
        val payload = service.encryptPayload("alice", 1001, "strict-index")
        val parts = payload.ciphertextBase64.split(':')
        val withoutIndex = parts.take(4).joinToString(":")

        assertFailsWith<IllegalArgumentException> {
            service.decryptPayload(
                payload.copy(ciphertextBase64 = withoutIndex),
            )
        }
    }

    private fun xorToHex(plaintext: String, key: String): String {
        val plainBytes = plaintext.encodeToByteArray()
        val keyBytes = key.encodeToByteArray()
        val out = ByteArray(plainBytes.size)
        for (index in plainBytes.indices) {
            out[index] = (plainBytes[index].toInt() xor keyBytes[index % keyBytes.size].toInt()).toByte()
        }
        return out.joinToString(separator = "") { byte -> byte.toUByte().toString(16).padStart(2, '0') }
    }

    private fun km(raw: String): String = Base64.encode(raw.encodeToByteArray())
}

private class InMemoryRepository : OmemoSessionRepository {
    private val bundles: MutableMap<String, OmemoDeviceBundle> = linkedMapOf()
    private val sessions: MutableMap<String, OmemoSessionRecord> = linkedMapOf()
    private val lifecycle: MutableMap<String, OmemoSessionLifecycleState> = linkedMapOf()

    override suspend fun saveBundle(bundle: OmemoDeviceBundle) {
        bundles[key(bundle.userId, bundle.deviceId)] = bundle
    }

    override suspend fun findBundle(userId: String, deviceId: Int): OmemoDeviceBundle? =
        bundles[key(userId, deviceId)]

    override suspend fun saveSession(session: OmemoSessionRecord) {
        sessions[key(session.userId, session.deviceId)] = session
    }

    override suspend fun findSession(userId: String, deviceId: Int): OmemoSessionRecord? =
        sessions[key(userId, deviceId)]

    override suspend fun setTrust(userId: String, deviceId: Int, trust: OmemoIdentityTrust) {
        val mapKey = key(userId, deviceId)
        val existing = sessions[mapKey] ?: return
        sessions[mapKey] = existing.copy(trust = trust)
    }

    override suspend fun deleteSession(userId: String, deviceId: Int) {
        sessions.remove(key(userId, deviceId))
    }

    override suspend fun saveLifecycleState(state: OmemoSessionLifecycleState) {
        lifecycle[key(state.userId, state.deviceId)] = state
    }

    override suspend fun findLifecycleState(userId: String, deviceId: Int): OmemoSessionLifecycleState? =
        lifecycle[key(userId, deviceId)]

    override suspend fun deleteLifecycleState(userId: String, deviceId: Int) {
        lifecycle.remove(key(userId, deviceId))
    }

    private fun key(userId: String, deviceId: Int): String = "$userId#$deviceId"
}
