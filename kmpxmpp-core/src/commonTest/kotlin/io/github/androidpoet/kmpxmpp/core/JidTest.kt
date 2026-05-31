package io.github.androidpoet.kmpxmpp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JidTest {
    @Test
    fun test_jid_toString_whenAllPartsPresent_returnsFullJid() {
        val jid = Jid(local = "alice", domain = "example.com", resource = "mobile")

        val text = jid.toString()

        assertEquals("alice@example.com/mobile", text)
    }

    @Test
    fun test_parseJidOrNull_whenValidFullJid_returnsParsedJid() {
        val parsed = parseJidOrNull("alice@example.com/mobile")
        assertEquals("alice@example.com/mobile", parsed.toString())
    }

    @Test
    fun test_parseJidOrNull_whenDomainOnly_returnsDomainJid() {
        val parsed = parseJidOrNull("example.com")
        assertEquals("example.com", parsed.toString())
    }

    @Test
    fun test_parseJidOrNull_whenInvalid_returnsNull() {
        assertNull(parseJidOrNull(""))
        assertNull(parseJidOrNull("@example.com"))
        assertNull(parseJidOrNull("alice@"))
    }
}
