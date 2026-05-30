package io.github.androidpoet.kmpxmpp.core

import kotlin.test.Test
import kotlin.test.assertEquals

class JidTest {
    @Test
    fun test_jid_toString_whenAllPartsPresent_returnsFullJid() {
        val jid = Jid(local = "alice", domain = "example.com", resource = "mobile")

        val text = jid.toString()

        assertEquals("alice@example.com/mobile", text)
    }
}
