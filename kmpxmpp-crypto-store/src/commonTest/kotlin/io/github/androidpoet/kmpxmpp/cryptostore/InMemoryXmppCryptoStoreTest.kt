package io.github.androidpoet.kmpxmpp.cryptostore

import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryXmppCryptoStoreTest {
    @Test
    fun test_store_putGet_whenValid_returnsCopiedBytes() = runTest {
        val store = InMemoryXmppCryptoStore()
        val secret = byteArrayOf(1, 2, 3)
        store.put("omemo:key", secret)
        secret[0] = 9

        val getResult = store.get("omemo:key")
        val success = assertIs<XmppResult.Success<ByteArray?>>(getResult)
        assertContentEquals(byteArrayOf(1, 2, 3), success.value)
    }

    @Test
    fun test_store_get_whenMutatingReturnedArray_doesNotMutateStoredValue() = runTest {
        val store = InMemoryXmppCryptoStore()
        store.put("token", byteArrayOf(4, 5, 6))

        val firstRead = assertIs<XmppResult.Success<ByteArray?>>(store.get("token")).value!!
        firstRead[1] = 99
        val secondRead = assertIs<XmppResult.Success<ByteArray?>>(store.get("token")).value!!
        assertContentEquals(byteArrayOf(4, 5, 6), secondRead)
    }

    @Test
    fun test_store_containsAndDelete_whenAliasPresent_tracksState() = runTest {
        val store = InMemoryXmppCryptoStore()
        store.put("session", byteArrayOf(7))

        assertTrue(assertIs<XmppResult.Success<Boolean>>(store.contains("session")).value)
        assertTrue(assertIs<XmppResult.Success<Boolean>>(store.delete("session")).value)
        assertFalse(assertIs<XmppResult.Success<Boolean>>(store.contains("session")).value)
        assertNull(assertIs<XmppResult.Success<ByteArray?>>(store.get("session")).value)
    }

    @Test
    fun test_store_clear_whenEntriesPresent_removesAll() = runTest {
        val store = InMemoryXmppCryptoStore()
        store.put("a", byteArrayOf(1))
        store.put("b", byteArrayOf(2))
        store.clear()

        val aliases = assertIs<XmppResult.Success<Set<String>>>(store.listAliases()).value
        assertTrue(aliases.isEmpty())
    }

    @Test
    fun test_store_put_whenInvalidInput_returnsFailure() = runTest {
        val store = InMemoryXmppCryptoStore()
        val aliasFailure = assertIs<XmppResult.Failure>(store.put(" ", byteArrayOf(1)))
        val bytesFailure = assertIs<XmppResult.Failure>(store.put("k", byteArrayOf()))
        assertEquals("Crypto alias cannot be blank.", aliasFailure.error.message)
        assertEquals("Crypto bytes cannot be empty.", bytesFailure.error.message)
    }
}
